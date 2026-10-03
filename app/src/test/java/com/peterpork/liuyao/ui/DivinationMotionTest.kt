package com.peterpork.liuyao.ui

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DivinationMotionTest {
    @Test fun ticketIsConsumedAtStartAndNeverReplayedByDuplicateReadiness() {
        val ink = InkReveal()
        assertEquals(InkReveal.Phase.Waiting, ink.phase)
        assertTrue(ink.start())
        assertFalse(ink.start())
        ink.finish(); ink.finish()
        assertFalse(ink.start())
        assertEquals(InkReveal.Phase.Finished, ink.phase)
    }

    @Test fun interruptionBeforeReadinessAlsoConsumesTicket() {
        val ink = InkReveal()
        ink.finish()
        assertFalse(ink.start())
        assertFalse(InkReveal(eligible = false).start())
    }

    @Test fun identityUsesActualSessionCastAndPositionRatherThanValueOrReplayRun() {
        val at = Instant.parse("2026-09-27T00:00:00Z")
        val first = NewLineInk(at, "cast-1", 1)
        val second = NewLineInk(at, "cast-2", 2)
        first.reveal.start(); first.reveal.finish()
        assertTrue(second.reveal.start())
        assertNotEquals(first.castId, second.castId)
        assertNotEquals(first.position, second.position)
        assertEquals(220, DivinationMotion.LineMillis)
        assertEquals(240, DivinationMotion.CompletionMillis)
    }
}
