package com.peterpork.liuyao.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HomeEntranceMotionTest {
    @get:Rule val rule = createComposeRule()
    @Test fun naturalCompletionUsesOneClock() {
        rule.mainClock.autoAdvance = false
        val session = HomeEntranceSession(false, true)
        lateinit var frame: HomeEntranceFrame
        rule.setContent { frame = rememberHomeEntrance(session) }
        rule.mainClock.advanceTimeBy(250)
        rule.runOnIdle { assertTrue(frame.value.ink > 0f); assertEquals(0f, frame.value.writing) }
        rule.mainClock.advanceTimeBy(850)
        rule.runOnIdle {
            assertNotEquals(SplashValues.Final, frame.value)
            assertEquals(HomeEntranceSession.Phase.Running, session.phase)
        }
        rule.mainClock.advanceTimeBy(1200)
        rule.runOnIdle {
            assertEquals(SplashValues.Final, frame.value)
            assertEquals(1, session.completionCount)
        }
    }
    @Test fun everySkipStageStartsAtCurrentPictureAndCompletesOnce() {
        rule.mainClock.autoAdvance = false
        var session = HomeEntranceSession(false, true)
        val state = mutableStateOf(session)
        lateinit var frame: HomeEntranceFrame
        rule.setContent { frame = rememberHomeEntrance(state.value) }
        for (time in listOf(400L, 1200L, 1900L)) {
            rule.runOnIdle { session = HomeEntranceSession(false,true); state.value = session }
            rule.mainClock.advanceTimeBy(time)
            lateinit var before: SplashValues
            rule.runOnIdle { before = frame.value; session.skip(); session.skip() }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnIdle {
                assertTrue(frame.value.writing >= before.writing)
                assertTrue(frame.value.move >= before.move)
            }
            rule.mainClock.advanceTimeBy(180)
            rule.runOnIdle { assertEquals(SplashValues.Final, frame.value); assertEquals(1, session.completionCount) }
        }
    }
    @Test fun disposalAndReentryNeverReplay() {
        rule.mainClock.autoAdvance = false
        val visible = mutableStateOf(true)
        val session = HomeEntranceSession(false, true)
        lateinit var frame: HomeEntranceFrame
        rule.setContent { if (visible.value) frame = rememberHomeEntrance(session) }
        rule.mainClock.advanceTimeBy(200)
        rule.runOnIdle { visible.value = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { visible.value = true }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(SplashValues.Final, frame.value); assertFalse(session.tryStart()) }
    }
    @Test fun fullGestureIncludingHeldUpCannotClickThrough() {
        rule.mainClock.autoAdvance = false
        val session = HomeEntranceSession(false, true)
        var clicks = 0
        rule.setContent {
            rememberHomeEntrance(session)
            Box(Modifier.size(150.dp).finishEntranceOnTouch(session)) {
                Button(onClick = { clicks++ }) { Text("Target") }
            }
        }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("Target").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(180)
        rule.onNodeWithText("Target").performTouchInput { up() }
        rule.runOnIdle { assertEquals(0, clicks); assertEquals(1, session.completionCount) }
        rule.onNodeWithText("Target").performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, clicks) }
    }
    @Test fun systemCoverDoesNotConsumeTimeline() {
        rule.mainClock.autoAdvance = false
        val session = HomeEntranceSession(false, true, visible = false)
        lateinit var frame: HomeEntranceFrame
        rule.setContent { frame = rememberHomeEntrance(session) }
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle { assertEquals(SplashValues(), frame.value); session.contentVisible = true }
        rule.mainClock.advanceTimeBy(HomeEntranceMotion.TotalMillis.toLong() + 100)
        rule.runOnIdle { assertEquals(SplashValues.Final, frame.value) }
    }
}
