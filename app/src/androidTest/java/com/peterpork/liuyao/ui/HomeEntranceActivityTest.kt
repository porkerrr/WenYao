package com.peterpork.liuyao.ui

import android.content.Intent
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.peterpork.liuyao.MainActivity
import com.peterpork.liuyao.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Only runs on a dedicated package: no access to the user's records or keys. */
class HomeEntranceActivityTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun launch(): ActivityScenario<MainActivity> {
        assumeTrue("Use tests/motion.init.gradle", isMotionTestPackage(context.packageName))
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        // Espresso can be idle while the OS starting window still covers a Pending session.
        // Advance only layout frames until the real platform exit callback has arrived.
        rule.waitUntil(10_000) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            var visible = false
            scenario.onActivity { visible = it.homeEntrance.contentVisible }
            visible
        }
        return scenario
    }

    private fun awaitHomeReady(scenario: ActivityScenario<MainActivity>) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(HomeEntranceMotion.TotalMillis.toLong() + 100)
        rule.waitForIdle()
        scenario.onActivity {
            assertEquals("Visible=${it.homeEntrance.contentVisible}",
                HomeEntranceSession.Phase.Finished, it.homeEntrance.phase)
        }
    }

    @Test fun interruptedLaunchBackgroundAndRecreationDoNotReplay() {
        rule.mainClock.autoAdvance = false
        launch().use { scenario ->
            rule.mainClock.advanceTimeBy(32)
            scenario.onActivity {
                assertEquals("Launcher ${it.intent.action} ${it.intent.categories}", HomeEntranceSession.Phase.Running, it.homeEntrance.phase)
            }
            rule.onNodeWithTag("splash-skip").assertExists()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertEquals(HomeEntranceSession.Phase.Finished, it.homeEntrance.phase) }
            scenario.recreate()
            rule.mainClock.advanceTimeByFrame()
            scenario.onActivity { assertEquals(HomeEntranceSession.Phase.Finished, it.homeEntrance.phase) }
            rule.onNodeWithText("开始起卦").assertIsEnabled().assertIsDisplayed()
        }
    }

    @Test fun navigationRestorationAndQuestionEntryKeepExistingBehavior() {
        launch().use { scenario ->
            awaitHomeReady(scenario)
            rule.onNodeWithText("历史记录").performClick()
            rule.onNodeWithText("历史卦例").assertIsDisplayed()
            scenario.recreate()
            rule.onNodeWithText("历史卦例").assertIsDisplayed()
            rule.onNodeWithContentDescription("返回").performClick()
            scenario.onActivity { assertEquals(HomeEntranceSession.Phase.Finished, it.homeEntrance.phase) }
            rule.onNode(hasSetTextAction()).performTextInput("动效验收：本周阅读安排如何？")
            rule.onNodeWithText("开始起卦").performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("自动完成剩余爻").assertExists()
            rule.onNodeWithContentDescription("返回").performClick()
            rule.onNodeWithText("设置").performScrollTo().performClick()
            rule.onNodeWithText("尚未配置 API Key").assertExists()
        }
    }

    @Test fun naturalCompletionDoesNotFocusInputOrOpenKeyboardAndTitleStaysLanded() {
        launch().use { scenario ->
            awaitHomeReady(scenario)
            rule.onNodeWithTag("splash-skip").assertDoesNotExist()
            rule.onNode(hasSetTextAction()).assertIsNotFocused()
            val bounds = rule.onNodeWithTag("splash-title").fetchSemanticsNode().boundsInRoot
            rule.mainClock.advanceTimeBy(100)
            assertEquals(bounds, rule.onNodeWithTag("splash-title").fetchSemanticsNode().boundsInRoot)
            scenario.onActivity {
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                assertNotNull(insets)
                assertFalse(insets!!.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
            }
        }
    }

    @Test fun disablingSystemMotionDuringEntranceImmediatelyFinishes() {
        rule.mainClock.autoAdvance = false
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val original = Settings.Global.getString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        val originalScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        launch().use { scenario ->
            try {
                rule.mainClock.advanceTimeBy(160)
                shell("settings put global animator_duration_scale 0")
                // Settings IPC is asynchronous; freeze animation time until the platform observes it.
                rule.waitUntil(5_000) { !ValueAnimator.areAnimatorsEnabled() }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                // Allow observer -> recomposition -> effect frames. Total entrance time remains
                // below 450ms, so an ignored zero-scale change cannot pass by finishing naturally.
                rule.mainClock.advanceTimeBy(96)
                rule.waitForIdle()
                scenario.onActivity { assertEquals(HomeEntranceSession.Phase.Finished, it.homeEntrance.phase) }
                rule.onNodeWithText("开始起卦").assertIsEnabled()
            } finally {
                // Deleting an absent setting alone leaves WindowManager's cached scale at zero.
                // Restore the effective value first, then restore absence if that was the original state.
                shell("settings put global animator_duration_scale $originalScale")
                rule.waitUntil(5_000) { ValueAnimator.areAnimatorsEnabled() == (originalScale > 0f) }
                if (original == null) shell("settings delete global animator_duration_scale")
            }
        }
    }

    @Test fun zeroSystemScaleIsFinalOnFirstCompositionWithNoStagger() {
        assumeTrue("Run this test with animator_duration_scale=0",
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
        rule.mainClock.autoAdvance = false
        launch().use { scenario ->
            rule.waitForIdle()
            scenario.onActivity { assertEquals(HomeEntranceSession.Phase.Finished, it.homeEntrance.phase) }
            rule.onNodeWithText("开始起卦").assertIsEnabled().assertIsDisplayed()
            rule.onNode(hasSetTextAction()).assertIsEnabled()
            rule.onNodeWithContentDescription("问爻").assertIsDisplayed()
        }
    }

    @Test fun launchBackgroundMatchesExistingThemeInBothSystemModes() {
        for (mode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            }
            val themed = context.createConfigurationContext(configuration)
            themed.setTheme(R.style.Theme_WenYao)
            if (Build.VERSION.SDK_INT >= 31) {
                val icon = TypedValue()
                assertTrue(themed.theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, icon, true))
                assertEquals(R.drawable.splash_empty_icon, icon.resourceId)
                val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                val drawable = themed.getDrawable(icon.resourceId)!!
                drawable.setBounds(0, 0, 64, 64)
                drawable.draw(android.graphics.Canvas(bitmap))
                for (y in 0 until 64) for (x in 0 until 64)
                    assertEquals("System splash icon must be invisible", 0, android.graphics.Color.alpha(bitmap.getPixel(x, y)))
                bitmap.recycle()
            }
            for (attribute in listOfNotNull(android.R.attr.windowBackground,
                if (Build.VERSION.SDK_INT >= 31) android.R.attr.windowSplashScreenBackground else null)) {
                val value = TypedValue()
                assertTrue(themed.theme.resolveAttribute(attribute, value, true))
                assertEquals(context.getColor(R.color.launcher_background), value.data)
            }
        }
    }
}
