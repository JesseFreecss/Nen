package com.jesse.gardefou.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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
 *  - Navigateurs : lire l'URL affichée dans la barre d'adresse et la comparer aux mots-clés.
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
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val pkg = event.packageName?.toString() ?: return

        // Liste blanche défensive : on ne surveille QUE les navigateurs connus et YouTube.
        when {
            pkg == YOUTUBE_PACKAGE -> checkYouTubeShorts()
            BROWSER_URL_BAR_IDS.containsKey(pkg) -> checkBrowserUrl(pkg)
            else -> return
        }
    }

    /**
     * Détection navigateur : retrouve la barre d'adresse par son resource-id et lit son
     * contenu. Pendant la frappe, l'omnibox contient le TEXTE TAPÉ (recherche ou URL en
     * cours) : on le compare donc aux termes bloqués à chaque caractère, ce qui permet un
     * blocage « en amont », avant même la validation. Après navigation, l'omnibox affiche
     * le domaine (suffisant pour les sites explicites, dont le domaine contient le terme).
     */
    private fun checkBrowserUrl(pkg: String) {
        val root = rootInActiveWindow ?: return
        val urlBarId = BROWSER_URL_BAR_IDS[pkg] ?: return
        val nodes = root.findAccessibilityNodeInfosByViewId(urlBarId)
        for (node in nodes) {
            val url = node.text?.toString()?.lowercase() ?: continue
            if (url.isEmpty()) continue
            val hit = matchBlockedTerm(url)
            if (hit != null) {
                Log.d(TAG, "DÉTECTÉ (navigateur $pkg) : « $hit » dans « $url »")
                // Navigateur : on quitte la page / annule la saisie (retour arrière), tout
                // en restant dans l'app.
                triggerBlock("« $hit »") { performGlobalAction(GLOBAL_ACTION_BACK) }
                return
            }
        }
    }

    /**
     * Cherche un terme bloqué dans le texte donné (déjà en minuscules). Combine la blocklist
     * de l'utilisateur (mots-clés + domaines importés) et la liste intégrée de termes
     * explicites, toujours active. Retourne le terme correspondant, ou null.
     */
    private fun matchBlockedTerm(text: String): String? {
        blockedKeywords.firstOrNull { it.isNotEmpty() && text.contains(it) }?.let { return it }
        return EXPLICIT_KEYWORDS.firstOrNull { text.contains(it) }
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
            Toast.makeText(this, "Bloqué par GardeFou : $reason", Toast.LENGTH_SHORT).show()
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
    }

    private companion object {
        const val TAG = "GardeFouA11y"

        // Délai minimal entre deux blocages, pour absorber la rafale d'événements d'une
        // même fenêtre (le lecteur Shorts émet ~10 événements/seconde).
        const val COOLDOWN_MS = 3_000L

        const val YOUTUBE_PACKAGE = "com.google.android.youtube"

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
