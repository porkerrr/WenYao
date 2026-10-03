package com.peterpork.liuyao.preview
import org.junit.Assert.*
import org.junit.Test
class SettledRevealTest {
    @Test fun earlyGateIsOncePerReplay() {
        val r = SettledReveal(2.0)
        assertEquals(1.625, r.revealSourceSeconds, .000001)
        assertFalse(r.frame(1.624f, 1.2992))
        assertTrue(r.frame(1.625f, 1.3))
        assertEquals(-.300, r.revealedAt!! - r.stableActiveSeconds, .000001)
        assertFalse(r.frame(2f, 1.6))
        r.reset(); assertFalse(r.frame(0f, 0.0)); assertTrue(r.frame(1.625f, 1.3))
    }
    @Test fun backgroundDoesNotReachGate() {
        val p = PreviewPlayback(1.25f).apply { configure(6f); replay(); frame(0); frame(1_000_000_000) }
        val r = SettledReveal(2.0)
        assertFalse(r.frame(p.seconds, p.activeSeconds))
        p.suspendFrames(); p.frame(91_000_000_000)
        assertFalse(r.frame(p.seconds, p.activeSeconds))
        p.frame(91_300_000_000); assertTrue(r.frame(p.seconds, p.activeSeconds))
        assertEquals(1.3, r.revealedAt!!, .000001)
        assertTrue(p.playing)
    }
    @Test fun originalSpeedAndSkipEndpointRemainAvailable() {
        val p = PreviewPlayback().apply { configure(6f); replay(); frame(0); frame(1_000_000_000) }
        assertEquals(1f, p.seconds)
        p.finish(); assertTrue(p.filamentSeconds() < p.duration)
        p.replay(); assertEquals(0.0, p.activeSeconds, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidSpeedRejected() { SettledReveal(2.0, 0.0) }
}
