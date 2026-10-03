package com.peterpork.liuyao.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.peterpork.liuyao.domain.LineValue
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeFalse

class DivinationMotionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun allFourValuesRevealSeparateYinSegmentsAndKeepFullGeometry() {
        val fraction = mutableFloatStateOf(.3f)
        rule.setContent {
            WenYaoTheme {
                Column(Modifier.background(Color.White)) {
                    LineValue.entries.forEach { line ->
                        DivinationGlyph(line.isYang, line.isMoving,
                            Modifier.width(200.dp).testTag("glyph-${line.value}"), Color.Black) { fraction.floatValue }
                    }
                }
            }
        }
        val bounds = LineValue.entries.map { rule.onNodeWithTag("glyph-${it.value}").fetchSemanticsNode().boundsInRoot }
        for (line in LineValue.entries) {
            val pixels = rule.onNodeWithTag("glyph-${line.value}").captureToImage().toPixelMap()
            val y = pixels.height / 2
            assertTrue(pixels[(pixels.width * .05f).toInt(), y].red < .1f)
            assertTrue(pixels[pixels.width / 2, y].red > .9f)
            if (!line.isYang) assertTrue("Second yin segment appears independently", pixels[(pixels.width * .6f).toInt(), y].red < .1f)
        }
        rule.runOnIdle { fraction.floatValue = 1f }
        for ((index, line) in LineValue.entries.withIndex()) {
            val node = rule.onNodeWithTag("glyph-${line.value}")
            assertEquals(bounds[index], node.fetchSemanticsNode().boundsInRoot)
            val pixels = node.captureToImage().toPixelMap()
            assertEquals(if (line.isYang) 0f else 1f, pixels[pixels.width / 2, pixels.height / 2].red, .01f)
        }
    }

    @Test fun realReadinessStartsOnceAndSameValueNextCastHasItsOwnReveal() {
        rule.mainClock.autoAdvance = false
        var ticket by mutableStateOf(InkReveal())
        var ready by mutableStateOf(false)
        lateinit var frame: InkFrame
        rule.setContent { frame = rememberInkReveal(ticket, ready, DivinationMotion.LineMillis) }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle { assertEquals(0f, frame.fraction); ready = true }
        rule.mainClock.advanceTimeBy(96)
        rule.runOnIdle { assertTrue(frame.fraction in .01f.. .99f) }
        rule.mainClock.advanceTimeBy(250)
        rule.runOnIdle { assertEquals(1f, frame.fraction); ready = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { ready = true }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(1f, frame.fraction); ticket = InkReveal(); ready = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(0f, frame.fraction); ready = true }
        rule.mainClock.advanceTimeBy(96)
        rule.runOnIdle { assertTrue(frame.fraction < 1f) }
    }

    @Test fun pauseEvenBeforeSettlementAndReentryFastForwardWithoutReplay() {
        rule.mainClock.autoAdvance = false
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }
        val ticket = InkReveal()
        var visible by mutableStateOf(true)
        var ready by mutableStateOf(false)
        lateinit var frame: InkFrame
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (visible) frame = rememberInkReveal(ticket, ready, DivinationMotion.LineMillis)
            }
        }
        rule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
            ready = true
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(1f, frame.fraction); visible = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { visible = true }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(1f, frame.fraction); assertFalse(ticket.start()) }
    }

    @Test fun interruptedCompletionAndHistoryAreFinalOnReentry() {
        rule.mainClock.autoAdvance = false
        val ticket = InkReveal()
        var visible by mutableStateOf(true)
        var history by mutableStateOf(false)
        lateinit var frame: InkFrame
        rule.setContent { if (visible) frame = rememberInkReveal(if (history) null else ticket, true, DivinationMotion.CompletionMillis) }
        rule.mainClock.advanceTimeBy(80)
        rule.runOnIdle { assertTrue(frame.fraction < 1f); visible = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { visible = true }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(1f, frame.fraction); history = true }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(1f, frame.fraction) }
    }

    @Test fun sixSlotsAndOlderLinesRemainStillWhileOnlyNewestInkChanges() {
        var lines by mutableStateOf(emptyList<LineValue>())
        var fraction by mutableFloatStateOf(0f)
        rule.setContent { WenYaoTheme { CastingLineProgress(lines, lines.size) { fraction } } }
        val bounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        rule.onAllNodesWithText("待生成").assertCountEquals(6)
        rule.runOnIdle { lines = listOf(6, 7, 8, 9, 7, 7).map(LineValue::from) }
        assertEquals(bounds, rule.onRoot().fetchSemanticsNode().boundsInRoot)
        val old = rule.onNodeWithText("6 老阴").captureToImage()
        rule.runOnIdle { fraction = 1f }
        val after = rule.onNodeWithText("6 老阴").captureToImage()
        val a = old.toPixelMap(); val b = after.toPixelMap()
        for (x in 0 until a.width) for (y in 0 until a.height) assertEquals(a[x,y], b[x,y])
        assertTrue(rule.onNodeWithText("初爻").fetchSemanticsNode().boundsInRoot.top > rule.onNodeWithText("上爻").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun zeroScaleShowsCommittedLineAndCompletionWithoutReadinessWait() {
        assumeFalse(android.animation.ValueAnimator.areAnimatorsEnabled())
        rule.mainClock.autoAdvance = false
        val line = InkReveal(); val result = InkReveal()
        lateinit var lineFrame: InkFrame
        lateinit var resultFrame: InkFrame
        rule.setContent {
            lineFrame = rememberInkReveal(line, false, DivinationMotion.LineMillis)
            resultFrame = rememberInkReveal(result, true, DivinationMotion.CompletionMillis)
        }
        rule.runOnIdle {
            assertEquals(1f, lineFrame.fraction); assertEquals(1f, resultFrame.fraction)
            assertFalse(line.start()); assertFalse(result.start())
        }
    }

    @Test fun disablingWhileWaitingConsumesTicketWithoutWaitingForCoinOrReplaying() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val original = android.provider.Settings.Global.getString(resolver, "animator_duration_scale")
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                .use { it.readBytes() }
        }
        rule.mainClock.autoAdvance = false
        val ticket = InkReveal()
        var ready by mutableStateOf(false)
        lateinit var frame: InkFrame
        rule.setContent { frame = rememberInkReveal(ticket, ready, DivinationMotion.LineMillis) }
        try {
            shell("settings put global animator_duration_scale 0")
            rule.waitUntil(5_000) { !android.animation.ValueAnimator.areAnimatorsEnabled() }
            rule.waitUntil(5_000) { rule.mainClock.advanceTimeByFrame(); frame.fraction == 1f }
            rule.runOnIdle { assertEquals(InkReveal.Phase.Finished, ticket.phase); ready = true }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnIdle { assertEquals(1f, frame.fraction); assertFalse(ticket.start()) }
        } finally {
            shell(if (original == null) "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $original")
            rule.waitUntil(5_000) { android.animation.ValueAnimator.areAnimatorsEnabled() == ((original?.toFloatOrNull() ?: 1f) > 0f) }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(1f, frame.fraction) }
    }
}
