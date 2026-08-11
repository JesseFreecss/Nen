package com.jesse.nen.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortFormBudgetTrackerTest {

    @Test
    fun `isOverLimit est faux sous la limite`() {
        assertFalse(ShortFormBudgetTracker.isOverLimit(accumulatedMs = 0, elapsedMs = 1_000, limitMs = 2_000))
    }

    @Test
    fun `isOverLimit est vrai exactement a la limite`() {
        assertTrue(ShortFormBudgetTracker.isOverLimit(accumulatedMs = 1_000, elapsedMs = 1_000, limitMs = 2_000))
    }

    @Test
    fun `isOverLimit est vrai au-dela de la limite`() {
        assertTrue(ShortFormBudgetTracker.isOverLimit(accumulatedMs = 1_500, elapsedMs = 1_000, limitMs = 2_000))
    }

    @Test
    fun `isOverLimit avec un temps ecoule nul depend uniquement du cumul deja fait`() {
        assertFalse(ShortFormBudgetTracker.isOverLimit(accumulatedMs = 1_000, elapsedMs = 0, limitMs = 2_000))
        assertTrue(ShortFormBudgetTracker.isOverLimit(accumulatedMs = 2_000, elapsedMs = 0, limitMs = 2_000))
    }
}
