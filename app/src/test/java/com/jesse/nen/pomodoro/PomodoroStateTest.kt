package com.jesse.nen.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroStateTest {

    @Test
    fun `formatRemaining affiche 0 00 pour zero milliseconde`() {
        assertEquals("0:00", formatRemaining(0L))
    }

    @Test
    fun `formatRemaining arrondit a la seconde superieure`() {
        // 1 ms ne doit jamais s'afficher comme "0:00" : l'utilisateur verrait un décompte figé
        // une seconde de trop avant de basculer sur la phase suivante.
        assertEquals("0:01", formatRemaining(1L))
    }

    @Test
    fun `formatRemaining gere une minute exacte`() {
        assertEquals("1:00", formatRemaining(60_000L))
    }

    @Test
    fun `formatRemaining gere plus d une heure sans format heures`() {
        // 61 minutes et 1 seconde : le format ne connaît que minutes:secondes, sans heures.
        assertEquals("61:01", formatRemaining(61 * 60_000L + 1_000L))
    }

    @Test
    fun `formatRemaining ne descend jamais sous zero`() {
        assertEquals("0:00", formatRemaining(-5_000L))
    }

    @Test
    fun `remainingAt renvoie le temps fige quand en pause`() {
        val state = PomodoroState(
            phase = PomodoroPhase.WORK,
            endAtMs = 100_000L,
            remainingMs = 5_000L,
            paused = true
        )
        // Peu importe l'instant demandé : en pause, seul remainingMs compte.
        assertEquals(5_000L, state.remainingAt(0L))
        assertEquals(5_000L, state.remainingAt(999_999L))
    }

    @Test
    fun `remainingAt calcule depuis l instant de fin quand actif`() {
        val state = PomodoroState(phase = PomodoroPhase.WORK, endAtMs = 10_000L, paused = false)
        assertEquals(6_000L, state.remainingAt(4_000L))
    }

    @Test
    fun `remainingAt est borne a zero apres l instant de fin`() {
        val state = PomodoroState(phase = PomodoroPhase.WORK, endAtMs = 10_000L, paused = false)
        assertEquals(0L, state.remainingAt(15_000L))
    }

    @Test
    fun `running est faux uniquement en phase IDLE`() {
        assertEquals(false, PomodoroState(phase = PomodoroPhase.IDLE).running)
        assertEquals(true, PomodoroState(phase = PomodoroPhase.WORK).running)
        assertEquals(true, PomodoroState(phase = PomodoroPhase.BREAK).running)
    }
}
