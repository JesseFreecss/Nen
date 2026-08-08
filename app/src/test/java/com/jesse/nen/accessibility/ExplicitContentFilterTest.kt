package com.jesse.nen.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExplicitContentFilterTest {

    @Test
    fun `firstMatchOrNull detecte un grand site pour adultes connu`() {
        val normalized = VowMatcher.normalize("www.pornhub.com/video")
        assertEquals("pornhub", ExplicitContentFilter.firstMatchOrNull(normalized))
    }

    @Test
    fun `firstMatchOrNull renvoie null sur un texte sans rapport`() {
        val normalized = VowMatcher.normalize("recette de cuisine italienne")
        assertNull(ExplicitContentFilter.firstMatchOrNull(normalized))
    }

    @Test
    fun `firstMatchOrNull sur-bloque volontairement les termes courts (comportement actuel assume)`() {
        // Comportement documenté et assumé du filtre agressif : "sussex" contient "sex".
        // Ce test verrouille le comportement ACTUEL, il ne le "corrige" pas — une correction
        // serait un changement de comportement hors périmètre de ce refactor.
        val normalized = VowMatcher.normalize("visiting Sussex today")
        assertEquals("sex", ExplicitContentFilter.firstMatchOrNull(normalized))
    }
}
