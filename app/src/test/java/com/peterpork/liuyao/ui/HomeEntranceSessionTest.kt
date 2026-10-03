package com.peterpork.liuyao.ui

import org.junit.Assert.*
import org.junit.Test

class HomeEntranceSessionTest {
    @Test fun normalLaunchIsConsumedBeforeCompletion() {
        val session = HomeEntranceSession(restored = false, normalLaunch = true)
        assertTrue(session.tryStart())
        assertEquals(HomeEntranceSession.Phase.Running, session.phase)
        assertFalse(session.tryStart())
        session.finish()
        assertFalse(session.tryStart())
    }

    @Test fun savedStateAlwaysSkipsIncludingAnInterruptedLaunch() {
        val restored = HomeEntranceSession(restored = true, normalLaunch = true)
        assertFalse(restored.tryStart())
        assertEquals(HomeEntranceSession.Phase.Finished, restored.phase)
    }

    @Test fun nonLauncherEntryDoesNotAcquireABrandEntrance() {
        assertFalse(HomeEntranceSession(restored = false, normalLaunch = false).tryStart())
    }

    @Test fun leavingHomeBeforeOrDuringEntranceCannotReplayOnReturn() {
        listOf(false, true).forEach { alreadyStarted ->
            val session = HomeEntranceSession(restored = false, normalLaunch = true)
            if (alreadyStarted) session.tryStart()
            session.onDestination("history")
            session.onDestination("home")
            assertFalse(session.tryStart())
        }
    }

    @Test fun interruptionBeforeFirstFrameAlsoConsumesEntrance() {
        val session = HomeEntranceSession(restored = false, normalLaunch = true)
        session.finish()
        session.finish()
        assertFalse(session.tryStart())
    }

    @Test fun zeroInvalidOrPlatformDisabledScaleSkipsAllMotion() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertFalse(HomeEntranceMotion.enabled(it))
        }
        assertFalse(HomeEntranceMotion.enabled(1f, platformEnabled = false))
        assertTrue(HomeEntranceMotion.enabled(0.5f))
        assertTrue(HomeEntranceMotion.enabled(2f))
    }

    @Test fun fractionClampsAndBrandTimelineIs2200Millis() {
        assertEquals(0f, HomeEntranceMotion.fraction(50f, 100, 350), 0f)
        assertTrue(HomeEntranceMotion.fraction(150f, 0, 300) in 0.01f..0.99f)
        assertTrue(HomeEntranceMotion.fraction(150f, 50, 350) in 0.01f..0.99f)
        assertTrue(HomeEntranceMotion.fraction(150f, 100, 350) in 0.01f..0.99f)
        assertEquals(1f, HomeEntranceMotion.fraction(450f, 100, 350), 0f)
        assertEquals(1f, HomeEntranceMotion.fraction(1000f, 0, 300), 0f)
        assertEquals(2200, HomeEntranceMotion.TotalMillis)
    }
}
