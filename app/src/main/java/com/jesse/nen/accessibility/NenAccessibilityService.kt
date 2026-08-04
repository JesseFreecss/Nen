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
import java.text.Normalizer
import android.widget.Toast
import com.jesse.nen.data.NenDatabase
import com.jesse.nen.data.KeywordRepository
import kotlin.reflect.KMutableProperty0
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
 * quotidien réglable (voir [ShortFormLimitPrefs]) ; seul le dépassement déclenche le blocage.
 *
 * Confidentialité / périmètre :
 *  - LISTE BLANCHE stricte : on ne traite QUE les apps déclarées (navigateurs + YouTube +
 *    Instagram), à la fois via `android:packageNames` dans la config XML (filtrage système)
 *    et via une seconde vérification défensive ici. Aucune autre app n'est observée.
 *  - Tout se fait sur l'appareil, sans réseau.
 *
 * Sur détection positive (étape 4), on renvoie l'utilisateur à l'écran d'accueil
 * (GLOBAL_ACTION_HOME) et on affiche un Toast, avec un anti-rebond pour ne pas répéter
 * l'action à chaque événement (le flux Shorts émet des dizaines d'événements par seconde).
 */
class NenAccessibilityService : AccessibilityService() {

    // Portée coroutine du service (annulée à la déconnexion).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Un vœu scellé, avec sa forme normalisée précalculée. La normalisation coûte cher et la
     * liste peut compter des dizaines de milliers d'entrées après un import : la refaire à
     * chaque frappe serait ruineux, on la fait une fois au chargement.
     */
    private class Vow(val raw: String, val normalized: String, val allowPrefix: Boolean)

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

    // Session en cours dans le flux Reels/Shorts : horodatage (uptime) de son début, ou null
    // hors du flux. Le temps écoulé est régulièrement reversé au cumul persisté du jour
    // (voir shortFormFlush) et à la sortie du flux.
    @Volatile private var reelsSessionStartMs: Long? = null
    @Volatile private var shortsSessionStartMs: Long? = null

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
            flushShortFormSession(ShortFormPlatform.INSTAGRAM_REELS, ::reelsSessionStartMs)
            flushShortFormSession(ShortFormPlatform.YOUTUBE_SHORTS, ::shortsSessionStartMs)
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
                    val normalized = normalize(entry.keyword)
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
            if (pkg !in WATCHED_SEARCH_PACKAGES) return
            checkTypedText(pkg, event)
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        // Liste blanche défensive : navigateurs connus, app Google, YouTube et Instagram.
        when {
            pkg == YOUTUBE_PACKAGE -> checkYouTubeShorts()
            pkg == INSTAGRAM_PACKAGE -> checkInstagramReels()
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

        // Vues de requête connues, lues QUEL QUE SOIT leur caractère éditable. Sur l'écran
        // de résultats de l'app Google, la requête est un simple TextView : elle ne devient
        // un champ de saisie que si on la touche. Sans cette lecture, cliquer une suggestion
        // de l'historique passait librement — rien n'étant tapé, aucun événement de frappe
        // n'est émis non plus.
        for (queryId in SEARCH_QUERY_VIEW_IDS) {
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
        val vow = matchVow(text)
        if (vow != null) {
            Log.d(TAG, "DÉTECTÉ (vœu scellé, $pkg) : « $vow » dans « $text »")
            triggerVowBlock(vow, text)
            return true
        }
        val full = normalize(text)
        val explicit = EXPLICIT_NORMALIZED.firstOrNull { full.contains(it) }
        if (explicit != null) {
            Log.d(TAG, "DÉTECTÉ (terme explicite, $pkg) : « $explicit » dans « $text »")
            triggerBlock("« $explicit »") { performGlobalAction(GLOBAL_ACTION_BACK) }
            return true
        }
        return false
    }

    /**
     * Cherche un vœu scellé dans un texte, en tolérant les variantes.
     *
     * Deux règles :
     *  1. le texte NORMALISÉ contient le vœu normalisé. La normalisation retire accents,
     *     casse, espaces et ponctuation : « Reddi t », « R.E.D.D.I.T » et « réddit » se
     *     ramènent tous à « reddit ».
     *  2. le dernier mot tapé est un DÉBUT de vœu, à partir de [MIN_PREFIX_LEN] caractères.
     *     C'est ce qui bloque « reddi » avant même que le mot soit fini. Réservée aux vœux
     *     saisis à la main (voir Vow.allowPrefix).
     *
     * La règle 2 travaille sur le dernier mot du texte brut, pas sur le texte normalisé
     * entier : sans ça, « quoi de neuf reddi » ne serait le début d'aucun vœu.
     */
    private fun matchVow(text: String): String? {
        val full = normalize(text)
        if (full.isEmpty()) return null
        val lastWord = normalize(text.trim().split(WHITESPACE).lastOrNull() ?: "")

        for (vow in vows) {
            if (full.contains(vow.normalized)) return vow.raw
            if (vow.allowPrefix && lastWord.length >= MIN_PREFIX_LEN &&
                vow.normalized.startsWith(lastWord)
            ) {
                return vow.raw
            }
        }
        return null
    }

    /**
     * Réduit un texte à ses lettres et chiffres, sans accents ni casse. NFD sépare la lettre
     * de son accent, le filtre ne garde ensuite que a-z et 0-9, ce qui élimine du même coup
     * les marques diacritiques, les espaces et la ponctuation.
     */
    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val builder = StringBuilder(decomposed.length)
        for (char in decomposed) {
            if (char in 'a'..'z' || char in '0'..'9') builder.append(char)
        }
        return builder.toString()
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
        for (id in YOUTUBE_SHORTS_IDS) {
            if (root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) {
                Log.d(TAG, "DÉTECTÉ (YouTube Shorts) via le marqueur « $id »")
                trackShortForm(ShortFormPlatform.YOUTUBE_SHORTS, ::shortsSessionStartMs) {
                    // Shorts : on force YouTube à revenir sur SA page d'accueil (retire le
                    // Short de l'écran, contrairement au bouton Home qui ne fait que
                    // minimiser l'app).
                    triggerBlock("YouTube Shorts") { openYouTubeHome() }
                }
                return
            }
        }
        // Plus dans le flux Shorts : on clôture une éventuelle session en cours.
        flushShortFormSession(ShortFormPlatform.YOUTUBE_SHORTS, ::shortsSessionStartMs, endSession = true)
    }

    /**
     * Détection des Reels Instagram in-app, même principe que les YouTube Shorts : marqueurs
     * structurels du pager Reels par resource-id (voir [INSTAGRAM_REELS_IDS]).
     */
    private fun checkInstagramReels() {
        val root = rootInActiveWindow ?: return
        for (id in INSTAGRAM_REELS_IDS) {
            if (root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) {
                Log.d(TAG, "DÉTECTÉ (Instagram Reels) via le marqueur « $id »")
                trackShortForm(ShortFormPlatform.INSTAGRAM_REELS, ::reelsSessionStartMs) {
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
        flushShortFormSession(ShortFormPlatform.INSTAGRAM_REELS, ::reelsSessionStartMs, endSession = true)
    }

    /**
     * Tient la session en cours dans un flux Reels/Shorts : la démarre si elle ne l'est pas
     * encore, et déclenche [onLimitReached] dès que le cumul du jour (déjà persisté + temps
     * écoulé depuis le début de la session) dépasse le seuil réglé pour [platform].
     *
     * Le temps de la session n'est PAS reversé au cumul à chaque appel (trop fréquent, un
     * événement de contenu peut arriver plusieurs fois par seconde) : seuls le flush
     * périodique ([shortFormFlush]) et la sortie du flux le font.
     */
    private fun trackShortForm(
        platform: ShortFormPlatform,
        sessionStart: KMutableProperty0<Long?>,
        onLimitReached: () -> Unit
    ) {
        val now = SystemClock.uptimeMillis()
        val start = sessionStart.get()
        if (start == null) {
            sessionStart.set(now)
            return
        }
        val elapsedSession = now - start
        val accumulatedToday = ShortFormLimitPrefs.todayAccumulatedMs(this, platform)
        val limitMs = ShortFormLimitPrefs.limitMinutes(this, platform) * 60_000L
        if (accumulatedToday + elapsedSession >= limitMs) {
            ShortFormLimitPrefs.addAccumulatedMs(this, platform, elapsedSession)
            sessionStart.set(now)
            onLimitReached()
        }
    }

    /**
     * Reverse le temps écoulé de la session en cours au cumul persisté du jour.
     *
     * @param endSession si vrai, referme la session (sortie du flux) ; sinon la relance
     * immédiatement à partir de maintenant (flush périodique d'une session toujours active).
     */
    private fun flushShortFormSession(
        platform: ShortFormPlatform,
        sessionStart: KMutableProperty0<Long?>,
        endSession: Boolean = false
    ) {
        val start = sessionStart.get() ?: return
        val now = SystemClock.uptimeMillis()
        val elapsed = now - start
        if (elapsed > 0) ShortFormLimitPrefs.addAccumulatedMs(this, platform, elapsed)
        sessionStart.set(if (endSession) null else now)
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

    /**
     * Ramène Instagram sur son fil d'actualité via un lien profond, même principe que
     * [openYouTubeHome]. Repli sur un retour arrière si le lancement échoue.
     */
    private fun openInstagramHome() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/"))
                .setPackage(INSTAGRAM_PACKAGE)
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
        flushShortFormSession(ShortFormPlatform.INSTAGRAM_REELS, ::reelsSessionStartMs, endSession = true)
        flushShortFormSession(ShortFormPlatform.YOUTUBE_SHORTS, ::shortsSessionStartMs, endSession = true)
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

        // Longueur minimale d'un début de mot pour déclencher. En dessous, « red » ou « po »
        // feraient correspondre trop de vœux sans rapport.
        const val MIN_PREFIX_LEN = 4

        val WHITESPACE = Regex("\\s+")

        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"

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
         * Formes normalisées de la liste ci-dessus, calculées une fois. Pas de règle de
         * préfixe pour cette liste : ses termes sont courts et déclencheraient trop souvent.
         */
        val EXPLICIT_NORMALIZED: List<String> = EXPLICIT_KEYWORDS.map { keyword ->
            buildString {
                for (char in keyword.lowercase()) {
                    if (char in 'a'..'z' || char in '0'..'9') append(char)
                }
            }
        }

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
         * Vues qui affichent la requête en cours SANS être des champs de saisie. On les lit
         * par resource-id, ce qui reste ciblé : c'est la requête de l'utilisateur, pas le
         * contenu de la page.
         */
        val SEARCH_QUERY_VIEW_IDS: List<String> = listOf(
            // App Google, écran de résultats : TextView, éditable seulement une fois touché.
            "com.google.android.googlequicksearchbox:id/googleapp_srp_search_box_text"
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

        /**
         * Marqueur structurel du lecteur plein écran des Reels dans Instagram, confirmé par un
         * dump `uiautomator` sur un Reel réel (comme pour [YOUTUBE_SHORTS_IDS]).
         *
         * `root_clips_layout` et `clips_swipe_refresh_container` ont été retirés de cette liste :
         * Instagram réutilise ces conteneurs génériques ailleurs (grille Reels du profil, rangée
         * Reels de la recherche/explorer), ce qui déclenchait le blocage rien qu'en ouvrant DM,
         * recherche ou profil. Seul `clips_viewer_view_pager` — le ViewPager2 plein écran propre
         * à la LECTURE d'un Reel — est spécifique : les grilles utilisent un RecyclerView, jamais
         * ce pager.
         */
        val INSTAGRAM_REELS_IDS: List<String> = listOf(
            "com.instagram.android:id/clips_viewer_view_pager"
        )
    }
}
