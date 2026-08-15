package com.jesse.nen.data

/**
 * Nettoie une entrée de vœu saisie à la main pour en tirer un domaine nu.
 *
 * Logique pure (aucune dépendance à Room) : testable directement.
 */
internal object DomainEntry {

    /**
     * Réduit une entrée du type "https://fkbae.to/forum?x=1" à "fkbae.to".
     *
     * Nécessaire car [NenVpnService][com.jesse.nen.vpn.NenVpnService] compare les entrées
     * "domaine" au nom d'hôte brut extrait des requêtes DNS (jamais de schéma, de chemin ni
     * de port) : une entrée gardant son "https://" ne correspondrait alors plus jamais,
     * même collée telle quelle depuis la barre d'adresse du navigateur.
     *
     * Ne touche pas aux mots-clés qui ne ressemblent pas à une URL (pas de '/', ':' ou '@') :
     * ils continuent d'être comparés tels quels par [NenVpnService] (correspondance par
     * sous-chaîne).
     */
    fun normalize(rawWord: String): String {
        var word = rawWord.trim().lowercase()
        if (word.isEmpty()) return word

        val schemeIdx = word.indexOf("://")
        if (schemeIdx >= 0) word = word.substring(schemeIdx + 3)

        word = word.substringBefore('/').substringBefore('?').substringBefore('#')

        // "utilisateur:motdepasse@hote" (rare, mais une URL collée peut en contenir).
        word = word.substringAfterLast('@')

        // Port éventuel ("fkbae.to:8080"). Ne touche pas à une IPv6 entre crochets : ce cas
        // n'a pas de sens ici (les vœux visent des noms de domaine, pas des adresses IP).
        word = word.substringBefore(':')

        return word.trim()
    }
}
