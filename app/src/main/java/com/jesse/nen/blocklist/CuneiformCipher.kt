package com.jesse.nen.blocklist

/**
 * Translittération purement cosmétique des mots scellés en signes cunéiformes Unicode
 * (bloc U+12000+, hors du plan multilingue de base : chaque signe occupe une paire de
 * substituts UTF-16, d'où une table `Char -> String` plutôt que `Char -> Char`).
 *
 * Aucun rapport phonétique avec le sumérien ou l'akkadien réel : c'est un chiffrement
 * lettre-à-lettre stable, pas une traduction. Le mot stocké et comparé
 * ([com.jesse.nen.accessibility.NenAccessibilityService]) reste le français saisi ; seul
 * l'AFFICHAGE dans [SermentDialog] passe par ici.
 */
object CuneiformCipher {

    private fun sign(codePoint: Int): String = String(Character.toChars(codePoint))

    // a..z : 26 premiers signes du bloc Cunéiforme (U+12000+), assignés et contigus
    // (liste de signes sumériens de base, aucun besoin de sens phonétique ici).
    private val LETTERS: Map<Char, String> =
        ('a'..'z').mapIndexed { index, char -> char to sign(0x12000 + index) }.toMap()

    // 0..9 : 10 premiers signes numériques du bloc Chiffres/ponctuation cunéiformes (U+12400+).
    private val DIGITS: Map<Char, String> =
        ('0'..'9').mapIndexed { index, char -> char to sign(0x12400 + index) }.toMap()

    // Ponctuation courante des mots/domaines bloqués -> signes de ponctuation cunéiforme
    // dédiés, en fin du même bloc (seulement 3 existent, réutilisés pour '-'/'_').
    private val PUNCTUATION: Map<Char, String> = mapOf(
        ' ' to sign(0x1247D),
        '.' to sign(0x1247E),
        '-' to sign(0x1247F),
        '_' to sign(0x1247F)
    )

    private val TABLE: Map<Char, String> = LETTERS + DIGITS + PUNCTUATION

    /** Caractère hors table (accent, symbole rare...) : rendu tel quel en repli. */
    fun transliterate(text: String): String =
        text.lowercase().map { char -> TABLE[char] ?: char.toString() }.joinToString("")
}
