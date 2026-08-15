package com.jesse.nen.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainEntryTest {

    @Test
    fun `normalize retire le schema http ou https`() {
        assertEquals("fkbae.to", DomainEntry.normalize("https://fkbae.to"))
        assertEquals("fkbae.to", DomainEntry.normalize("http://fkbae.to"))
    }

    @Test
    fun `normalize retire chemin requete et fragment`() {
        assertEquals("fkbae.to", DomainEntry.normalize("https://fkbae.to/forum?x=1#top"))
    }

    @Test
    fun `normalize retire le port`() {
        assertEquals("fkbae.to", DomainEntry.normalize("https://fkbae.to:8080/forum"))
    }

    @Test
    fun `normalize retire les identifiants utilisateur`() {
        assertEquals("fkbae.to", DomainEntry.normalize("https://user:pass@fkbae.to"))
    }

    @Test
    fun `normalize met en minuscules et retire les espaces`() {
        assertEquals("fkbae.to", DomainEntry.normalize("  HTTPS://FKBAE.TO/  "))
    }

    @Test
    fun `normalize laisse un domaine nu inchange`() {
        assertEquals("fkbae.to", DomainEntry.normalize("fkbae.to"))
    }

    @Test
    fun `normalize laisse un mot-cle sans schema inchange`() {
        assertEquals("youtube", DomainEntry.normalize("YouTube"))
    }

    @Test
    fun `normalize sur chaine vide renvoie vide`() {
        assertEquals("", DomainEntry.normalize("   "))
    }
}
