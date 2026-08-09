package com.jesse.nen.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExplicitContentFilterTest {

    @Test
    fun `firstMatchOrNull detecte un grand site pour adultes connu`() {
        // "xvideos" plutôt que "pornhub" : les termes génériques sont vérifiés avant les noms
        // de domaine dans EXPLICIT_KEYWORDS, donc "pornhub" matcherait d'abord sur "porn"
        // (comportement d'origine, préservé — voir le test de sur-blocage ci-dessous).
        val normalized = VowMatcher.normalize("www.xvideos.com/video")
        assertEquals("xvideos", ExplicitContentFilter.firstMatchOrNull(normalized))
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
