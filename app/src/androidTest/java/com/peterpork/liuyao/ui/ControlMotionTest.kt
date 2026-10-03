package com.peterpork.liuyao.ui

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Native components with synthetic callbacks: no ViewModel, records, credentials or AI requests. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class ControlMotionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var clicks = 0
    private val enabled = mutableStateOf(true)
    private val visible = mutableStateOf(true)
    private lateinit var inputMode: InputModeManager
    private val action get() = rule.onNodeWithTag("action")
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture(outlined: Boolean = false, requireMotion: Boolean = true) {
        if (requireMotion) assumeTrue("Run at animator scale 1x", ValueAnimator.areAnimatorsEnabled())
        rule.mainClock.autoAdvance = false
        rule.setContent {
            inputMode = LocalInputModeManager.current
            WenYaoTheme {
                Surface(color = Color.White) {
                    Column(Modifier.padding(24.dp).width(280.dp)) {
                        Text("按压验收 · 合成操作")
                        if (visible.value) {
                            val modifier = Modifier.width(260.dp).testTag("action")
                            if (outlined) {
                                WenYaoOutlinedButton({ clicks++ }, modifier, enabled.value, pressFeedback = true) {
                                    Text("重新解读（可能再次产生费用）")
                                }
                            } else {
                                WenYaoPrimaryButton({ clicks++ }, modifier, enabled.value, pressFeedback = true) {
                                    if (enabled.value) Text("开始起卦") else WenYaoBusyIndicator("正在保存…")
                                }
                            }
                        }
                    }
                }
            }
        }
        advance(32)
    }

    private fun advance(ms: Long) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun bitmap() = action.captureToImage().asAndroidBitmap()
    // Midline intersects the straight sides, away from rounded corners and button text.
    private fun inkLeft(image: Bitmap = bitmap()): Int = (0 until image.width).first { x ->
        val pixel = image.getPixel(x, image.height / 2)
        android.graphics.Color.red(pixel) < 240 || android.graphics.Color.green(pixel) < 240 || android.graphics.Color.blue(pixel) < 240
    }
    private fun save(name: String) {
        val directory = File(context.filesDir, "motion-round2").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun pressScalesDrawingOnlyAndReleaseDispatchesBeforeRecoveryEnds() {
        fixture()
        val bounds = action.fetchSemanticsNode().boundsInRoot
        val resting = inkLeft()
        save("button-rest")
        action.performTouchInput { down(center) }
        advance(128)
        val inset = inkLeft() - resting
        val expectedInset = (bitmap().width * 0.015f).roundToInt()
        assertTrue("Expected 3% visual shrink, inset=$inset", inset in (expectedInset - 2)..(expectedInset + 2))
        assertEquals(bounds, action.fetchSemanticsNode().boundsInRoot)
        action.assertHeightIsAtLeast(52.dp)
        assertEquals(0, clicks)
        save("button-pressed")
        action.performTouchInput { up() }
        rule.runOnIdle { assertEquals("onClick must not await the release animation", 1, clicks) }
        advance(48)
        assertTrue(inkLeft() < resting + inset)
        advance(160)
        assertEquals(resting, inkLeft())
        save("button-released")
    }

    @Test fun slideOutsideAndSystemCancelDoNotClickAndRestoreSize() {
        fixture(outlined = true)
        val resting = inkLeft()
        action.performTouchInput { down(center) }
        advance(128)
        assertTrue(inkLeft() > resting)
        action.performTouchInput { moveTo(Offset(center.x, -150f)); up() }
        advance(208)
        assertEquals(0, clicks)
        assertEquals(resting, inkLeft())
        action.performTouchInput { down(center) }
        advance(128)
        action.performTouchInput { cancel() }
        advance(208)
        assertEquals(0, clicks)
        assertEquals(resting, inkLeft())
    }

    @Test fun rapidTapsAndSemanticsClickHaveExactlyOneCallbackEach() {
        fixture()
        repeat(10) { action.performTouchInput { click() } }
        action.performClick()
        rule.runOnIdle { assertEquals(11, clicks) }
        advance(224)
        assertEquals(0, inkLeft())
    }

    @Test fun disabledLoadingCancelsPressAndKeepsNativeDisabledSemantics() {
        fixture()
        action.performTouchInput { down(center) }
        advance(128)
        rule.runOnIdle { enabled.value = false }
        advance(32)
        action.assertIsNotEnabled()
        assertEquals("Disabled/loading must snap to full size", 0, inkLeft())
        action.performTouchInput { up(); click() }
        action.performClick()
        rule.runOnIdle { assertEquals(0, clicks); enabled.value = true }
        advance(32)
        assertEquals(0, inkLeft())
        action.performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun backgroundAndPageRemovalDoNotRetainAStalePress() {
        fixture()
        action.performTouchInput { down(center) }
        advance(128)
        assertTrue(inkLeft() > 0)
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        advance(32)
        assertEquals(0, inkLeft())
        action.performTouchInput { cancel() }
        rule.runOnIdle { assertEquals(0, clicks) }
        action.performTouchInput { down(center) }
        advance(128)
        rule.runOnIdle { visible.value = false }
        advance(32)
        rule.onRoot().performTouchInput { cancel() }
        rule.runOnIdle { visible.value = true }
        advance(32)
        assertEquals(0, inkLeft())
        action.performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun keyboardActivationKeepsNativeFocusAndClickBehavior() {
        fixture(outlined = true)
        rule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        action.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        advance(32)
        action.assertIsFocused().performKeyInput { keyDown(Key.Enter) }
        advance(128)
        action.performKeyInput { keyUp(Key.Enter) }
        advance(208)
        rule.runOnIdle { assertEquals(1, clicks) }
        action.assertIsFocused().assertHeightIsAtLeast(48.dp)
        assertEquals(0, inkLeft())
        rule.runOnIdle { inputMode.requestInputMode(InputMode.Touch) }
    }

    @Test fun focusColorTransitionsWithoutChangingBoundsTextOrSelection() {
        assumeTrue(ValueAnimator.areAnimatorsEnabled())
        rule.mainClock.autoAdvance = false
        val otherFocus = FocusRequester()
        rule.setContent {
            inputMode = LocalInputModeManager.current
            var text by remember { mutableStateOf("动效验收示例") }
            WenYaoTheme { Surface(color = Color.White) {
                Column {
                    WenYaoQuestionField(text, { text = it }, null, Modifier.width(280.dp).testTag("question"))
                    Box(Modifier.size(1.dp).focusRequester(otherFocus).focusable())
                }
            } }
        }
        rule.runOnIdle { inputMode.requestInputMode(InputMode.Touch) }
        val field = rule.onNodeWithTag("question")
        fun border(): Int = field.captureToImage().asAndroidBitmap().let { it.getPixel(it.width / 2, 1) }
        val bounds = field.fetchSemanticsNode().boundsInRoot
        val resting = border()
        field.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        advance(64)
        val during = border()
        field.performTextInputSelection(TextRange(1, 3))
        advance(160)
        val focused = border()
        assertNotEquals(resting, during)
        assertNotEquals(during, focused)
        assertEquals(bounds, field.fetchSemanticsNode().boundsInRoot)
        assertEquals(TextRange(1, 3), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        field.performTextInput("测试")
        advance(32)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("动测试收示例")))
        // Transfer focus explicitly: the platform may restore the sole text field after clearFocus.
        rule.runOnIdle { otherFocus.requestFocus() }
        advance(208)
        field.assertIsNotFocused()
        assertEquals(resting, border())
        field.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        advance(208)
        // Native BasicTextField collapses its selection on blur; this round preserves that policy.
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("动测试收示例")))
    }

    @Test fun turningAnimationsOffMidPressAndColdDisabledMotionKeepClicksAndFullSize() {
        val original = Settings.Global.getString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        val originalScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
                .use { it.readBytes() }
        }
        fixture(requireMotion = false)
        try {
            action.performTouchInput { down(center) }
            advance(128)
            shell("settings put global animator_duration_scale 0")
            rule.waitUntil(5_000) { !ValueAnimator.areAnimatorsEnabled() }
            // The platform animator update and ContentObserver delivery are independent IPCs.
            // Keep holding the press: time alone cannot restore its size if the policy is broken.
            rule.waitUntil(5_000) { advance(16); inkLeft() == 0 }
            assertEquals(0, inkLeft())
            action.performTouchInput { up() }
            rule.runOnIdle { assertEquals(1, clicks); visible.value = false }
            advance(32)
            rule.runOnIdle { visible.value = true }
            advance(32)
            action.performTouchInput { down(center) }
            advance(32)
            assertEquals("No zero-scale transition or custom wait", 0, inkLeft())
            action.performTouchInput { up() }
            rule.runOnIdle { assertEquals(2, clicks) }
            save("button-zero-motion")
        } finally {
            shell("settings put global animator_duration_scale $originalScale")
            rule.waitUntil(5_000) { ValueAnimator.areAnimatorsEnabled() == (originalScale > 0f) }
            if (original == null) shell("settings delete global animator_duration_scale")
        }
    }
}
