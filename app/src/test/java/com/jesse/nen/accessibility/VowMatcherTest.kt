package com.jesse.nen.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VowMatcherTest {

    @Test
    fun `normalize retire espaces ponctuation et casse`() {
        assertEquals("reddit", VowMatcher.normalize("Reddi t"))
        assertEquals("reddit", VowMatcher.normalize("R.E.D.D.I.T"))
    }

    @Test
    fun `normalize retire les accents via decomposition NFD`() {
        assertEquals("reddit", VowMatcher.normalize("réddit"))
    }

    @Test
    fun `normalize conserve les chiffres`() {
        assertEquals("test123", VowMatcher.normalize("Test 123 !"))
    }

    @Test
    fun `match trouve un voeu par sous-chaine du texte normalise`() {
        val vow = Vow(raw = "reddit", normalized = "reddit", allowPrefix = true)
        assertEquals("reddit", VowMatcher.match(listOf(vow), "check Reddit now"))
    }

    @Test
    fun `match trouve un voeu par prefixe du dernier mot quand autorise`() {
        val vow = Vow(raw = "reddit", normalized = "reddit", allowPrefix = true)
        // "reddi" (5 caractères >= MIN_PREFIX_LEN) est un début de "reddit".
        assertEquals("reddit", VowMatcher.match(listOf(vow), "quoi de neuf reddi"))
    }

    @Test
    fun `match ignore un prefixe trop court`() {
        val vow = Vow(raw = "reddit", normalized = "reddit", allowPrefix = true)
        // "red" ne fait que 3 caractères, sous MIN_PREFIX_LEN (4).
        assertNull(VowMatcher.match(listOf(vow), "quoi de neuf red"))
    }

    @Test
    fun `match n applique pas la regle de prefixe aux domaines`() {
        // Domaines importés (contiennent un point) : allowPrefix = false.
        val domainVow = Vow(raw = "example.com", normalized = "examplecom", allowPrefix = false)
        assertNull(VowMatcher.match(listOf(domainVow), "un site exampl"))
        // Mais la correspondance complète fonctionne toujours.
        assertEquals("example.com", VowMatcher.match(listOf(domainVow), "aller sur example.com"))
    }

    @Test
    fun `match renvoie null sans correspondance`() {
        val vow = Vow(raw = "reddit", normalized = "reddit", allowPrefix = true)
        assertNull(VowMatcher.match(listOf(vow), "recette de cuisine"))
    }

    @Test
    fun `match renvoie null pour un texte vide`() {
        val vow = Vow(raw = "reddit", normalized = "reddit", allowPrefix = true)
        assertNull(VowMatcher.match(listOf(vow), "   "))
    }
}
