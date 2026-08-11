package com.jesse.nen.accessibility

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
import com.jesse.nen.data.NenDatabase
import com.jesse.nen.data.KeywordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service d'accessibilité de Nen (étape 3).
 *
 * Rôle : compléter le filtrage DNS en observant l'INTÉRIEUR de certaines apps, là où le
 * DNS ne suffit pas :
 *  - Navigateurs et app Google : comparer aux mots-clés le contenu des CHAMPS DE SAISIE —
 *    barre d'adresse, champ de recherche d'une page, et champ de l'app Google (celle du
 *    widget de l'écran d'accueil), qui est le cas le plus courant. Seul ce que l'utilisateur
 *    saisit est lu, jamais le texte statique des pages.
 *  - YouTube : repérer l'ouverture du lecteur "Shorts" via des marqueurs structurels de l'UI.
 *  - Instagram : repérer l'ouverture des Reels, sur le même principe que les Shorts YouTube.
 *
 * Shorts et Reels ne sont pas bloqués systématiquement : chaque plateforme a un budget
 * quotidien réglable (voir [ShortFormLimitPrefs]) ; seul le dépassement déclenche le blocage
 * (suivi par [ShortFormBudgetTracker], un par plateforme).
 *
 * Confidentialité / périmètre :
 *  - LISTE BLANCHE stricte : on ne traite QUE les apps déclarées (voir [WatchedApps]), à la
 *    fois via `android:packageNames` dans la config XML (filtrage système) et via une
 *    seconde vérification défensive ici. Aucune autre app n'est observée.
 *  - Tout se fait sur l'appareil, sans réseau.
 *
 * Sur détection positive (étape 4), on renvoie l'utilisateur à l'écran d'accueil
 * (GLOBAL_ACTION_HOME) et on affiche un Toast, avec un anti-rebond pour ne pas répéter
 * l'action à chaque événement (le flux Shorts émet des dizaines d'événements par seconde).
 *
 * La correspondance de vœux ([VowMatcher]), le filtre de termes explicites
 * ([ExplicitContentFilter]) et le suivi de budget Shorts/Reels ([ShortFormBudgetTracker])
 * sont de la logique pure extraite dans des fichiers dédiés ; ce service se limite à
 * l'orchestration avec le framework d'accessibilité Android.
 */
class NenAccessibilityService : AccessibilityService() {

    // Portée coroutine du service (annulée à la déconnexion).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Vœux scellés gardés en mémoire, rechargés automatiquement depuis la base.
    @Volatile private var vows: List<Vow> = emptyList()

    // Anti-rebond : horodatage (uptime) du dernier blocage déclenché.
    @Volatile private var lastBlockAt: Long = 0L

    // Texte exact qui a déclenché le dernier blocage de vœu. Sert à ne brider QUE ses
    // événements résiduels, sans ouvrir de fenêtre de passage libre pour une autre recherche.
    @Volatile private var lastBlockedText: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Écran de blocage des vœux scellés (créé à la première utilisation).
    private val auraOverlay by lazy { AuraOverlay(this) }

    // Blocage demandé mais overlay pas encore affiché. AuraOverlay.show() est posté sur le
    // thread principal : sans ce drapeau posé tout de suite, deux frappes rapprochées
    // passeraient toutes deux le test isShowing et empileraient deux écrans.
    @Volatile private var blockPending = false

    // Un suivi de budget par plateforme, chacun portant sa propre session en cours.
    private val reelsTracker = ShortFormBudgetTracker(ShortFormPlatform.INSTAGRAM_REELS)
    private val shortsTracker = ShortFormBudgetTracker(ShortFormPlatform.YOUTUBE_SHORTS)

    /**
     * Signe de vie périodique. Il ne dépend PAS des événements reçus : sans app surveillée au
     * premier plan, aucun événement n'arrive, et l'absence de battement serait alors prise à
     * tort pour un service mort.
     */
    private val heartbeat = object : Runnable {
        override fun run() {
            A11yHeartbeat.beat(this@NenAccessibilityService)
            mainHandler.postDelayed(this, A11yHeartbeat.BEAT_INTERVAL_MS)
        }
    }

    /**
     * Reverse périodiquement au cumul persisté le temps des sessions Reels/Shorts en cours,
     * pour ne pas perdre la progression du jour si le process est tué (HyperOS) avant la
     * sortie normale du flux.
     */
    private val shortFormFlush = object : Runnable {
        override fun run() {
            reelsTracker.flush(this@NenAccessibilityService)
            shortsTracker.flush(this@NenAccessibilityService)
            mainHandler.postDelayed(this, SHORT_FORM_FLUSH_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Observe la base : la liste se met à jour toute seule (comme dans le VpnService).
        val repo = KeywordRepository(NenDatabase.getInstance(this).blockedKeywordDao())
        scope.launch {
            repo.observeAll().collect { list ->
                vows = list.mapNotNull { entry ->
                    val normalized = VowMatcher.normalize(entry.keyword)
                    if (normalized.isEmpty()) {
                        null
                    } else {
                        // Un point trahit un domaine issu d'un import de liste hosts. La
                        // règle de préfixe ne s'y applique pas : sur des dizaines de milliers
                        // de domaines, taper « news » ou « mail » correspondrait au début de
                        // centaines d'entrées et bloquerait à tort en permanence.
                        Vow(entry.keyword, normalized, allowPrefix = !entry.keyword.contains('.'))
                    }
                }
            }
        }
        // Premier battement immédiat : sans lui, l'app signalerait une protection morte
        // pendant la minute suivant chaque activation du service.
        mainHandler.post(heartbeat)
        mainHandler.postDelayed(shortFormFlush, SHORT_FORM_FLUSH_INTERVAL_MS)
        Log.d(TAG, "Service d'accessibilité connecté")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // Frappe dans un champ de texte : c'est le chemin le plus court vers le texte saisi
        // (pas de parcours de l'arbre de vues), donc le plus rapide. C'est lui qui donne le
        // blocage « à la frappe », avant même la validation de la recherche.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (pkg !in WatchedApps.WATCHED_SEARCH_PACKAGES) return
            checkTypedText(pkg, event)
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        // Liste blanche défensive : navigateurs connus, app Google, YouTube et Instagram.
        when {
            pkg == WatchedApps.YOUTUBE_PACKAGE -> checkYouTubeShorts()
            pkg == WatchedApps.INSTAGRAM_PACKAGE -> checkInstagramReels()
            pkg in WatchedApps.WATCHED_SEARCH_PACKAGES -> checkSearchFields(pkg)
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
        WatchedApps.BROWSER_URL_BAR_IDS[pkg]?.let { urlBarId ->
            for (node in root.findAccessibilityNodeInfosByViewId(urlBarId)) {
                val url = node.text?.toString()
                if (!url.isNullOrEmpty()) texts.add(url.lowercase())
            }
        }

        // Vues de requête connues, lues QUEL QUE SOIT leur caractère éditable. Sur l'écran
        // de résultats de l'app Google, la requête est un simple TextView : elle ne devient
        // un champ de saisie que si on la touche. Sans cette lecture, cliquer une suggestion
        // de l'historique passait librement — rien n'étant tapé, aucun événement de frappe
        // n'est émis non plus.
        for (queryId in WatchedApps.SEARCH_QUERY_VIEW_IDS) {
            for (node in root.findAccessibilityNodeInfosByViewId(queryId)) {
                val query = node.text?.toString()
                if (!query.isNullOrEmpty()) texts.add(query)
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
        val vow = VowMatcher.match(vows, text)
        if (vow != null) {
            Log.d(TAG, "DÉTECTÉ (vœu scellé, $pkg) : « $vow » dans « $text »")
            triggerVowBlock(vow, text)
            return true
        }
        val full = VowMatcher.normalize(text)
        val explicit = ExplicitContentFilter.firstMatchOrNull(full)
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
    private fun triggerVowBlock(vow: String, sourceText: String) {
        if (blockPending || auraOverlay.isShowing) return

        // Anti-rebond CIBLÉ : on ne bride que la répétition du même texte, c'est-à-dire les
        // événements résiduels de la recherche qu'on vient de bloquer. Un anti-rebond global
        // ouvrait une fenêtre de 3 s pendant laquelle une NOUVELLE recherche passait sans
        // être bloquée — il suffisait de retaper juste après avoir congédié l'écran.
        val now = SystemClock.uptimeMillis()
        if (sourceText == lastBlockedText && now - lastBlockAt < COOLDOWN_MS) return
        lastBlockAt = now
        lastBlockedText = sourceText
        blockPending = true

        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.post {
            auraOverlay.show {
                // Le décompte repart du CONGÉ : l'overlay pouvant rester affiché longtemps,
                // les 3 s seraient épuisées au toucher et un dernier événement de l'app
                // rouvrirait aussitôt un écran de blocage par-dessus l'accueil.
                lastBlockAt = SystemClock.uptimeMillis()
                blockPending = false
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            // Affichage échoué (fenêtre refusée par le système) : sans ce relâchement, le
            // drapeau resterait levé et plus aucun blocage ne se déclencherait.
            if (!auraOverlay.isShowing) blockPending = false
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
        for (id in WatchedApps.YOUTUBE_SHORTS_IDS) {
            if (root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) {
                Log.d(TAG, "DÉTECTÉ (YouTube Shorts) via le marqueur « $id »")
                if (shortsTracker.onFlowDetected(this)) {
                    // Shorts : on force YouTube à revenir sur SA page d'accueil (retire le
                    // Short de l'écran, contrairement au bouton Home qui ne fait que
                    // minimiser l'app).
                    triggerBlock("YouTube Shorts") { openYouTubeHome() }
                }
                return
            }
        }
        // Plus dans le flux Shorts : on clôture une éventuelle session en cours.
        shortsTracker.flush(this, endSession = true)
    }

    /**
     * Détection des Reels Instagram in-app, même principe que les YouTube Shorts : marqueurs
     * structurels du pager Reels par resource-id (voir [WatchedApps.INSTAGRAM_REELS_IDS]).
     */
    private fun checkInstagramReels() {
        val root = rootInActiveWindow ?: return
        for (id in WatchedApps.INSTAGRAM_REELS_IDS) {
            if (root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) {
                Log.d(TAG, "DÉTECTÉ (Instagram Reels) via le marqueur « $id »")
                if (reelsTracker.onFlowDetected(this)) {
                    // Comme pour Shorts (openYouTubeHome) : un simple retour arrière quitte
                    // parfois l'app entière, les Reels étant un onglet de la barre du bas et
                    // non un écran empilé. Sans ce lien profond, Instagram peut rouvrir sur
                    // l'onglet Reels et redéclencher le blocage aussitôt, ce qui donnait
                    // l'impression que fil d'actu, DM et paramètres étaient bloqués aussi.
                    triggerBlock("Instagram Reels") { openInstagramHome() }
                }
                return
            }
        }
        reelsTracker.flush(this, endSession = true)
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
                .setPackage(WatchedApps.YOUTUBE_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Ouverture de la home YouTube échouée (${e.message}), repli sur retour arrière")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /**
     * Ramène Instagram sur son fil d'actualité via un lien profond, même principe que
     * [openYouTubeHome]. Repli sur un retour arrière si le lancement échoue.
     */
    private fun openInstagramHome() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/"))
                .setPackage(WatchedApps.INSTAGRAM_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Ouverture du fil Instagram échouée (${e.message}), repli sur retour arrière")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {
        // Appelé quand le système interrompt le service ; rien de spécial à faire ici.
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ne pas perdre la progression du jour si le service s'arrête en pleine session.
        reelsTracker.flush(this, endSession = true)
        shortsTracker.flush(this, endSession = true)
        scope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        // Filet de sécurité : ne jamais laisser l'écran de blocage derrière soi.
        blockPending = false
        auraOverlay.hide()
    }

    private companion object {
        const val TAG = "NenA11y"

        // Délai minimal entre deux blocages, pour absorber la rafale d'événements d'une
        // même fenêtre (le lecteur Shorts émet ~10 événements/seconde).
        const val COOLDOWN_MS = 3_000L

        // Cadence à laquelle le temps d'une session Reels/Shorts active est reversé au
        // cumul persisté du jour.
        const val SHORT_FORM_FLUSH_INTERVAL_MS = 15_000L

        // Bornes du parcours d'arbre à la recherche des champs de saisie.
        const val MAX_NODES_SCANNED = 300
        const val MAX_FIELDS = 8
    }
}
