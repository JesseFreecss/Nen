package com.jesse.nen.accessibility

/**
 * Filtre intégré de termes explicites (contenu adulte), TOUJOURS actif en plus de la
 * blocklist de l'utilisateur. Comparé par sous-chaîne à un texte déjà normalisé (voir
 * [VowMatcher.normalize]), donc dès la frappe.
 *
 * Compromis : certains termes courts (ex. « sex ») peuvent sur-bloquer (« sussex »,
 * « sexual health »...). C'est volontaire pour un filtre agressif ; ajuste la liste
 * ici selon tes besoins.
 */
internal object ExplicitContentFilter {

    private val EXPLICIT_KEYWORDS: List<String> = listOf(
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
    private val EXPLICIT_NORMALIZED: List<String> = EXPLICIT_KEYWORDS.map { keyword ->
        buildString {
            for (char in keyword.lowercase()) {
                if (char in 'a'..'z' || char in '0'..'9') append(char)
            }
        }
    }

    /** Premier terme explicite trouvé dans [normalizedText] (déjà passé par [VowMatcher.normalize]), ou null. */
    fun firstMatchOrNull(normalizedText: String): String? =
        EXPLICIT_NORMALIZED.firstOrNull { normalizedText.contains(it) }
}
