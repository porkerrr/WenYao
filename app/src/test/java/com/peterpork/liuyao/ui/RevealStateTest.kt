package com.peterpork.liuyao.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealStateTest {
    @Test fun `replay starts a new visual run for the same authoritative cast`() {
        val first = RevealState.Idle.replay("cast-1") as RevealState.Animating
        val second = RevealState.Revealed("cast-1", first.runId).replay("cast-1") as RevealState.Animating
        assertEquals("cast-1", second.castId)
        assertEquals(first.runId + 1, second.runId)
        assertEquals(0f, second.progress)
    }

    @Test fun `skip and cancel only transform reveal state`() {
        val animating = RevealState.Animating("cast-1", 0.4f, 3)
        assertEquals(RevealState.Revealed("cast-1", 3), animating.skip())
        assertTrue(animating.cancel() is RevealState.Idle)
    }

    @Test fun `line positions are labelled from bottom to top`() {
        assertEquals("初爻", lineName(1))
        assertEquals("二爻", lineName(2))
        assertEquals("上爻", lineName(6))
    }
}
