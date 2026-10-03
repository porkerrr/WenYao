package com.peterpork.liuyao.ui

import org.junit.Assert.*
import org.junit.Test

class AnalysisMotionTest {
    @Test fun `initial and repeated state do not replay while new revisions do`() {
        val changes = AnalysisChange("revision-1")
        assertNull(changes.next("revision-1"))
        val reveal = changes.next("revision-2")!!
        assertTrue(reveal.start())
        reveal.finish()
        assertFalse(reveal.start())
        assertNull(changes.next("revision-2"))
        assertNotNull(changes.next("revision-3"))
    }

    @Test fun `restored record is final and timing stays bounded`() {
        assertNull(AnalysisChange(AnalysisGenerationState.Running).next(AnalysisGenerationState.Running))
        assertTrue(AnalysisMotion.StateMillis in 180..240)
        assertTrue(AnalysisMotion.ExpandMillis in 200..250)
    }
}
