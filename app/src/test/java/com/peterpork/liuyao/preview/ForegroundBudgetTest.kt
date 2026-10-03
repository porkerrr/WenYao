package com.peterpork.liuyao.preview

import org.junit.Assert.*
import org.junit.Test

class ForegroundBudgetTest {
    @Test fun backgroundAndResumeDoNotConsumeDeadline() {
        val budget = ForegroundBudget(60_000)
        assertFalse(budget.update(0, true))
        assertFalse(budget.update(20_000, false))
        assertEquals(20_000L, budget.elapsedMillis)
        assertFalse(budget.update(900_000, true))
        assertFalse(budget.update(939_999, true))
        assertTrue(budget.update(940_000, true))
    }
    @Test fun startsPausedAndChargesLongForegroundOperation() {
        val budget = ForegroundBudget(60_000)
        budget.update(100, false)
        budget.update(100_000, true)
        assertEquals(0L, budget.elapsedMillis)
        assertTrue(budget.update(160_001, false))
    }
    @Test fun retryHasIndependentBudget() {
        val old = ForegroundBudget(10).apply { update(0, true) }
        assertTrue(old.update(11, true))
        val retry = ForegroundBudget(10)
        assertFalse(retry.update(11, true))
        assertFalse(retry.update(20, true))
        assertTrue(retry.update(21, true))
    }
}
