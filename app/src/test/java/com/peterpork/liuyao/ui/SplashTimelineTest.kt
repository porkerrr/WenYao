package com.peterpork.liuyao.ui

import org.junit.Assert.*
import org.junit.Test

class SplashTimelineTest {
    @Test fun paperFibersRetainFullStrengthThroughSplashAndHome() {
        // Home uses one static paper layer throughout; do not wash out the existing fiber asset.
        assertEquals(1f, HomeEntranceMotion.PaperTextureAlpha, 0f)
    }

    @Test fun boundariesAndContinuity() {
        assertEquals(SplashValues(), SplashValues.at(0f))
        val expanded = SplashValues.at(800f)
        assertEquals(1f, expanded.ink, 0f)
        assertEquals(1f, expanded.spread, 0f)
        assertEquals(0f, expanded.writing, 0f)
        val written = SplashValues.at(1600f)
        assertEquals(1f, written.writing, 0f)
        assertEquals(1f, written.subtitle, 0f)
        assertEquals(0f, written.body, 0f)
        assertEquals(0f, written.ink, 0f)
        assertNotEquals(SplashValues.Final, SplashValues.at(1100f))
        assertEquals(0f, SplashValues.at(1100f).body, 0f)
        assertEquals(SplashValues.Final, SplashValues.at(2200f))
        assertTrue(SplashValues.at(1200f).writing in 0.01f..0.999f)
        assertTrue(SplashValues.at(1900f).move in 0.01f..0.999f)
    }

    @Test fun skipPreservesCurrentPictureAndConvergesFromEveryStage() {
        for (ms in listOf(400f, 1200f, 1900f, 2190f)) {
            val start = SplashValues.at(ms)
            assertEquals(start, start.towardsEnd(0f))
            val end = start.towardsEnd(1f)
            assertEquals(0f, end.ink, 0f)
            assertEquals(1f, end.writing, 0f)
            assertEquals(1f, end.subtitle, 0f)
            assertEquals(1f, end.move, 0f)
            assertEquals(1f, end.body, 0f)
            assertTrue(HomeEntranceMotion.skipDuration(ms, 1f) <= 150)
            assertTrue(HomeEntranceMotion.skipDuration(ms, 1f) <= HomeEntranceMotion.TotalMillis-ms)
        }
    }

    @Test fun duplicateSkipFinishAndOldCallbacksCannotRestart() {
        val s = HomeEntranceSession(false, true)
        s.tryStart(); s.skip(); s.skip()
        assertEquals(HomeEntranceSession.Phase.Finishing, s.phase)
        s.finish(); s.finish(); s.skip()
        assertFalse(s.tryStart())
        assertEquals(1, s.completionCount)
    }

    @Test fun scaledSkipStillHas150MillisWallTimeBudget() {
        for (scale in listOf(0.5f, 1f, 2f, 10f)) {
            assertTrue(HomeEntranceMotion.skipDuration(1200f, scale) <= 150)
            assertEquals(minOf(150, (10*scale).toInt()), HomeEntranceMotion.skipDuration(2190f, scale))
        }
    }
}
