package com.peterpork.liuyao.preview

import org.junit.Assert.*
import org.junit.Test

class PreviewPlaybackTest {
    @Test fun `one shot clamps and holds the exact final pose`() {
        val p = PreviewPlayback().apply { configure(2f); replay() }
        assertFalse(p.frame(0L))
        assertFalse(p.frame(1_000_000_000L))
        assertTrue(p.frame(3_000_000_000L))
        assertEquals(2f, p.seconds)
        assertFalse(p.frame(4_000_000_000L))
        assertFalse(p.playing)
        assertTrue(p.filamentSeconds() < p.duration)
        assertTrue(p.duration - p.filamentSeconds() < 0.000001f)
    }
    @Test fun `background time and recreation do not advance or restart the take`() {
        val p = PreviewPlayback().apply { configure(3f); replay(); frame(0); frame(1_000_000_000) }
        p.suspendFrames()
        p.configure(3f)
        p.frame(90_000_000_000)
        assertEquals(1f, p.seconds)
        assertTrue(p.playing)
        p.frame(91_000_000_000)
        assertEquals(2f, p.seconds)
    }
    @Test fun `skip holds and replay resets only the local playhead`() {
        val p = PreviewPlayback().apply { configure(3f); finish() }
        p.frame(0); p.frame(10_000_000_000)
        assertEquals(3f, p.seconds)
        p.replay()
        assertEquals(0f, p.seconds)
        assertEquals(3f, p.duration)
        assertTrue(p.playing)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `invalid asset duration is rejected`() { PreviewPlayback().configure(Float.NaN) }
}
