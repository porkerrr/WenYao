package com.peterpork.liuyao.ui

import org.junit.Assert.*
import org.junit.Test

class CastPresentationTest {
    @Test fun `late completion cannot replace next cast or replay`() {
        val current = RevealState.Animating("cast-2", 0f, 7)
        assertFalse(acceptPresentationUpdate(current, RevealState.Revealed("cast-1", 6)))
        assertFalse(acceptPresentationUpdate(current, RevealState.Revealed("cast-2", 6)))
        assertTrue(acceptPresentationUpdate(current, RevealState.Revealed("cast-2", 7)))
    }

    @Test fun `skip and completion are terminal even for delayed progress`() {
        val running = RevealState.Animating("cast", .3f, 2)
        val skipped = running.skip()
        assertFalse(acceptPresentationUpdate(skipped, RevealState.Animating("cast", .4f, 2)))
        assertFalse(acceptPresentationUpdate(skipped, RevealState.Revealed("cast", 2)))
        assertFalse(acceptPresentationUpdate(RevealState.Idle, RevealState.Revealed("cast", 2)))
    }
}
