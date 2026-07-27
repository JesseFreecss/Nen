package com.jesse.gardefou.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.jesse.gardefou.data.GardeFouDatabase
import com.jesse.gardefou.data.KeywordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service d'accessibilité de GardeFou (étape 3).
 *
 * Rôle : compléter le filtrage DNS en observant l'INTÉRIEUR de certaines apps, là où le
 * DNS ne suffit pas :
 *  - Navigateurs et app Google : comparer aux mots-clés le contenu des CHAMPS DE SAISIE —
 *    barre d'adresse, champ de recherche d'une page, et champ de l'app Google (celle du
 *    widget de l'écran d'accueil), qui est le cas le plus courant. Seul ce que l'utilisateur
 *    saisit est lu, jamais le texte statique des pages.
 *  - YouTube : repérer l'ouverture du lecteur "Shorts" via des marqueurs structurels de l'UI.
 *
 * Confidentialité / périmètre :
 *  - LISTE BLANCHE stricte : on ne traite QUE les apps déclarées (navigateurs + YouTube),
 *    à la fois via `android:packageNames` dans la config XML (filtrage système) et via une
 *    seconde vérification défensive ici. Aucune autre app n'est observée.
 *  - Tout se fait sur l'appareil, sans réseau.
 *
 * Sur détection positive (étape 4), on renvoie l'utilisateur à l'écran d'accueil
 * (GLOBAL_ACTION_HOME) et on affiche un Toast, avec un anti-rebond pour ne pas répéter
 * l'action à chaque événement (le flux Shorts émet des dizaines d'événements par seconde).
 */
class GardeFouAccessibilityService : AccessibilityService() {

    // Portée coroutine du service (annulée à la déconnexion).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Mots-clés bloqués gardés en mémoire, rechargés automatiquement depuis la base.
    @Volatile private var blockedKeywords: List<String> = emptyList()

    // Anti-rebond : horodatage (uptime) du dernier blocage déclenché.
    @Volatile private var lastBlockAt: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    // Écran de blocage des vœux scellés (créé à la première utilisation).
    private val auraOverlay by lazy { AuraOverlay(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Observe la base : la liste se met à jour toute seule (comme dans le VpnService).
        val repo = KeywordRepository(GardeFouDatabase.getInstance(this).blockedKeywordDao())
        scope.launch {
            repo.observeAll().collect { list ->
                blockedKeywords = list.map { it.keyword }
            }
        }
        Log.d(TAG, "Service d'accessibilité connecté")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // Frappe dans un champ de texte : c'est le chemin le plus court vers le texte saisi
        // (pas de parcours de l'arbre de vues), donc le plus rapide. C'est lui qui donne le
        // blocage « à la frappe », avant même la validation de la recherche.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (pkg !in WATCHED_SEARCH_PACKAGES) return
            checkTypedText(pkg, event)
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        // Liste blanche défensive : navigateurs connus, app Google, et YouTube.
        when {
            pkg == YOUTUBE_PACKAGE -> checkYouTubeShorts()
            pkg in WATCHED_SEARCH_PACKAGES -> checkSearchFields(pkg)
            else -> return
        }
    }

    /**
     * Traite une frappe dans un CHAMP DE SAISIE du navigateur.
     *
     * On ne se limite pas à la barre d'adresse : le champ de recherche d'une page (Google,
     * YouTube…) est un EditText SANS resource-id, et c'est là que la recherche est le plus
     * souvent tapée. Ne regarder que l'omnibox laissait donc passer le cas principal.
     *
     * Le filtre reste `isEditable` : on lit ce que l'utilisateur saisit, jamais le texte
     * statique des pages.
     */
    private fun checkTypedText(pkg: String, event: AccessibilityEvent) {
        val source = event.source
        if (source != null && !source.isEditable) return

        val typed = event.text.joinToString(" ").lowercase()
        if (typed.isNotEmpty() && handleSearchText(pkg, typed)) return

        // Nœud source indisponible : on se rabat sur la lecture directe de la fenêtre.
        if (source == null) checkSearchFields(pkg)
    }

    /**
     * Collecte le texte des champs de saisie de la fenêtre. Bornée en nombre de nœuds visités
     * et de champs retenus : l'événement « contenu de fenêtre modifié » est très fréquent, un
     * parcours complet de l'arbre à chaque fois coûterait cher.
     */
    private fun collectEditableTexts(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        budget: IntArray
    ) {
        if (node == null || budget[0] <= 0 || out.size >= MAX_FIELDS) return
        budget[0]--
        if (node.isEditable) {
            val text = node.text?.toString()
            if (!text.isNullOrEmpty()) out.add(text.lowercase())
        }
        for (i in 0 until node.childCount) collectEditableTexts(node.getChild(i), out, budget)
    }

    /**
     * Détection navigateur : retrouve la barre d'adresse par son resource-id et lit son
     * contenu. Pendant la frappe, l'omnibox contient le TEXTE TAPÉ (recherche ou URL en
     * cours) : on le compare donc aux termes bloqués à chaque caractère, ce qui permet un
     * blocage « en amont », avant même la validation. Après navigation, l'omnibox affiche
     * le domaine (suffisant pour les sites explicites, dont le domaine contient le terme).
     */
    private fun checkSearchFields(pkg: String) {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()

        // Barre d'adresse d'abord, quand l'app en a une (chemin direct par resource-id).
        BROWSER_URL_BAR_IDS[pkg]?.let { urlBarId ->
            for (node in root.findAccessibilityNodeInfosByViewId(urlBarId)) {
                val url = node.text?.toString()
                if (!url.isNullOrEmpty()) texts.add(url.lowercase())
            }
        }

        // Puis les champs de saisie : après validation d'une recherche, l'omnibox de Chrome
        // n'affiche que le domaine (« google.com »), la requête y est masquée. Et l'app
        // Google n'a pas de barre d'adresse du tout : son champ de recherche est le seul
        // endroit où le terme apparaisse.
        collectEditableTexts(root, texts, intArrayOf(MAX_NODES_SCANNED))

        for (text in texts) {
            if (handleSearchText(pkg, text)) return
        }
    }

    /**
     * Confronte un texte d'omnibox (déjà en minuscules) aux deux listes, et déclenche le
     * blocage correspondant. Retourne true si un blocage a été déclenché.
     *
     * Les deux listes n'ont pas le même traitement : un VŒU SCELLÉ est un engagement que
     * l'utilisateur a pris lui-même, il mérite l'écran d'aura. La liste intégrée de termes
     * explicites garde le blocage discret d'origine (retour arrière + Toast).
     */
    private fun handleSearchText(pkg: String, text: String): Boolean {
        val vow = blockedKeywords.firstOrNull { it.isNotEmpty() && text.contains(it) }
        if (vow != null) {
            Log.d(TAG, "DÉTECTÉ (vœu scellé, $pkg) : « $vow » dans « $text »")
            triggerVowBlock(vow)
            return true
        }
        val explicit = EXPLICIT_KEYWORDS.firstOrNull { text.contains(it) }
        if (explicit != null) {
            Log.d(TAG, "DÉTECTÉ (terme explicite, $pkg) : « $explicit » dans « $text »")
            triggerBlock("« $explicit »") { performGlobalAction(GLOBAL_ACTION_BACK) }
            return true
        }
        return false
    }

    /**
     * Blocage d'un vœu scellé : on quitte immédiatement la page (retour arrière), puis on
     * affiche l'écran d'aura, qui reste jusqu'à ce que l'utilisateur le touche — ce toucher
     * le renvoie à l'écran d'accueil.
     */
    private fun triggerVowBlock(vow: String) {
        if (auraOverlay.isShowing) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < COOLDOWN_MS) return
        lastBlockAt = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.post {
            auraOverlay.show {
                // L'anti-rebond repart du CONGÉ, pas du déclenchement : l'overlay pouvant
                // rester affiché longtemps, les 3 s sont épuisées quand l'utilisateur le
                // touche, et un dernier événement du navigateur rouvrirait aussitôt un
                // nouvel écran de blocage par-dessus l'accueil.
                lastBlockAt = SystemClock.uptimeMillis()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        Log.d(TAG, "BLOCAGE vœu scellé déclenché (« $vow »)")
    }

    /**
     * Détection des YouTube Shorts in-app : le lecteur Shorts a des vues bien identifiées
     * par leur resource-id. Si l'une d'elles est présente dans la fenêtre active, on est
     * (très probablement) dans le flux Shorts.
     */
    private fun checkYouTubeShorts() {
        val root = rootInActiveWindow ?: return
        for (id in YOUTUBE_SHORTS_IDS) {
            val found = root.findAccessibilityNodeInfosByViewId(id)
            if (found.isNotEmpty()) {
                Log.d(TAG, "DÉTECTÉ (YouTube Shorts) via le marqueur « $id »")
                // Shorts : on force YouTube à revenir sur SA page d'accueil (retire le Short
                // de l'écran, contrairement au bouton Home qui ne fait que minimiser l'app).
                triggerBlock("YouTube Shorts") { openYouTubeHome() }
                return
            }
        }
    }

    /**
     * Exécute une action de blocage, protégée par un anti-rebond (COOLDOWN_MS) pour ne pas
     * la répéter à chaque événement d'une même fenêtre. Affiche aussi un Toast informatif.
     *
     * @param reason libellé court de la raison (affiché dans le Toast).
     * @param action l'action concrète à réaliser (retour arrière, home YouTube...).
     */
    private fun triggerBlock(reason: String, action: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < COOLDOWN_MS) return
        lastBlockAt = now

        action()

        // Toast informatif (sur le thread principal, par sécurité).
        mainHandler.post {
            Toast.makeText(this, "Bloqué par Nen : $reason", Toast.LENGTH_SHORT).show()
        }
        Log.d(TAG, "BLOCAGE déclenché ($reason)")
    }

    /**
     * Ramène YouTube sur sa page d'accueil via un deep link. Comme HomeActivity est réutilisée
     * (même tâche), YouTube remplace le lecteur Shorts par le flux d'accueil. Repli sur un
     * retour arrière si le lancement échoue.
     */
    private fun openYouTubeHome() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/"))
                .setPackage(YOUTUBE_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Ouverture de la home YouTube échouée (${e.message}), repli sur retour arrière")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {
        // Appelé quand le système interrompt le service ; rien de spécial à faire ici.
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        // Filet de sécurité : ne jamais laisser l'écran de blocage derrière soi.
        auraOverlay.hide()
    }

    private companion object {
        const val TAG = "GardeFouA11y"

        // Délai minimal entre deux blocages, pour absorber la rafale d'événements d'une
        // même fenêtre (le lecteur Shorts émet ~10 événements/seconde).
        const val COOLDOWN_MS = 3_000L

        // Bornes du parcours d'arbre à la recherche des champs de saisie.
        const val MAX_NODES_SCANNED = 300
        const val MAX_FIELDS = 8

        const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        /**
         * L'app Google : c'est elle qui s'ouvre depuis le widget de recherche de l'écran
         * d'accueil, et c'est de loin la façon la plus courante de lancer une recherche.
         * Elle n'a pas de barre d'adresse — seul son champ de saisie contient le terme,
         * d'où la détection par `isEditable` plutôt que par resource-id.
         */
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"

        /**
         * Liste intégrée de termes explicites (contenu adulte), TOUJOURS active en plus de
         * la blocklist de l'utilisateur. Comparée par sous-chaîne au texte de la barre
         * d'adresse (donc dès la frappe). Tout en minuscules.
         *
         * Compromis : certains termes courts (ex. « sex ») peuvent sur-bloquer (« sussex »,
         * « sexual health »...). C'est volontaire pour un filtre agressif ; ajuste la liste
         * ici selon tes besoins.
         */
        val EXPLICIT_KEYWORDS: List<String> = listOf(
            // Termes génériques (FR/EN)
            "porn", "porno", "pornographie", "xxx", "sex", "sexe", "sexo", "sexy",
            "nsfw", "nude", "nudes", "hentai", "hardcore", "camgirl", "camsex",
            "escort", "milf", "gangbang", "blowjob", "creampie", "deepthroat",
            "bukkake", "fetish", "bdsm", "incest", "rule34", "cameltoe",
            // Grands sites pour adultes (le domaine seul suffit à matcher après navigation)
            "pornhub", "xvideos", "xnxx", "youporn", "redtube", "xhamster",
            "brazzers", "onlyfans", "chaturbate", "stripchat", "spankbang",
            "fapello", "motherless", "tnaflix", "hqporner", "eporner", "beeg",
            "tube8", "youjizz", "nhentai", "erome"
        )

        /**
         * Resource-id de la barre d'adresse par navigateur. Sert de liste blanche des
         * navigateurs surveillés (doit rester cohérent avec `packageNames` de la config XML).
         */
        val BROWSER_URL_BAR_IDS: Map<String, String> = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.chrome.beta" to "com.chrome.beta:id/url_bar",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "com.kiwibrowser.browser" to "com.kiwibrowser.browser:id/url_bar",
            "com.vivaldi.browser" to "com.vivaldi.browser:id/url_bar",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.opera.browser" to "com.opera.browser:id/url_field",
            "com.opera.mini.native" to "com.opera.mini.native:id/url_field",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput"
        )

        /**
         * Apps dont on inspecte les champs de saisie. Doit rester cohérent avec
         * `packageNames` de la config XML : c'est ce dernier qui décide réellement des
         * événements que le système nous transmet, cette liste n'étant qu'un garde-fou.
         */
        val WATCHED_SEARCH_PACKAGES: Set<String> =
            BROWSER_URL_BAR_IDS.keys + GOOGLE_APP_PACKAGE

        /**
         * Marqueurs structurels de l'UI Shorts dans l'app YouTube, confirmés par un dump
         * `uiautomator` sur un Short réel. Ordonnés par fiabilité décroissante ; la présence
         * d'UN SEUL de ces nœuds suffit à conclure qu'on est dans le lecteur Shorts.
         * (On évite volontairement `player_overlay`/`slim_status_bar_player_container`, qui
         * apparaissent aussi dans le lecteur vidéo classique.)
         */
        val YOUTUBE_SHORTS_IDS: List<String> = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root", // racine du fragment Shorts
            "com.google.android.youtube:id/reel_recycler",            // pager vertical des Shorts
            "com.google.android.youtube:id/reel_watch_player",        // vue lecteur Shorts
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_player_underlay"
        )
    }
}
