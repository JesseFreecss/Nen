package com.jesse.nen.accessibility

/**
 * Applications et vues surveillées par [NenAccessibilityService] : navigateurs, app Google,
 * YouTube et Instagram. Données statiques uniquement — la détection elle-même reste dans le
 * service, qui est seul à dépendre des API d'accessibilité.
 */
internal object WatchedApps {

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
    val WATCHED_SEARCH_PACKAGES: Set<String> = BROWSER_URL_BAR_IDS.keys + GOOGLE_APP_PACKAGE

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
