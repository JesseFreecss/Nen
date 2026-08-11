package com.jesse.nen.accessibility

import java.text.Normalizer

/**
 * Un vœu scellé, avec sa forme normalisée précalculée. La normalisation coûte cher et la
 * liste peut compter des dizaines de milliers d'entrées après un import : la refaire à
 * chaque frappe serait ruineux, on la fait une fois au chargement.
 */
internal class Vow(val raw: String, val normalized: String, val allowPrefix: Boolean)

/**
 * Recherche de vœux scellés dans un texte observé par [NenAccessibilityService].
 *
 * Logique pure (aucune dépendance au framework d'accessibilité) : testable directement.
 */
internal object VowMatcher {

    // Longueur minimale d'un début de mot pour déclencher. En dessous, « red » ou « po »
    // feraient correspondre trop de vœux sans rapport.
    const val MIN_PREFIX_LEN = 4

    private val WHITESPACE = Regex("\\s+")

    /**
     * Réduit un texte à ses lettres et chiffres, sans accents ni casse. NFD sépare la lettre
     * de son accent, le filtre ne garde ensuite que a-z et 0-9, ce qui élimine du même coup
     * les marques diacritiques, les espaces et la ponctuation.
     */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val builder = StringBuilder(decomposed.length)
        for (char in decomposed) {
            if (char in 'a'..'z' || char in '0'..'9') builder.append(char)
        }
        return builder.toString()
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
    fun match(vows: List<Vow>, text: String): String? {
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
}
