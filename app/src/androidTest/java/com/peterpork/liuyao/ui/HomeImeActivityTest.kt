package com.peterpork.liuyao.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.peterpork.liuyao.MainActivity
import com.peterpork.liuyao.WenYaoApplication
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Real window/IME verification; a separate application ID prevents access to personal data. */
class HomeImeActivityTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val smallViewport get() = InstrumentationRegistry.getArguments()
        .getString("smallViewport") == "true"

    @Test fun realImeCompactsHomeRetainsDraftAndKeepsActionsReachable() {
        launch().use { scenario ->
            val normalMarkHeight = heightOf("home-wordmark")
            val normalQuestionHeight = heightOf("home-question")
            captureHome("01_home_closed.png")

            openKeyboardAndType(scenario)
            awaitCompact(normalMarkHeight)
            assertImeWindowSafety(scenario)
            val compactRatio = heightOf("home-wordmark") / normalMarkHeight
            assertTrue("Logo should shrink modestly when the real IME appears: $compactRatio",
                compactRatio in 0.70f..0.85f)
            assertTrue("The question field should release vertical space for the IME",
                heightOf("home-question") < normalQuestionHeight)
            question().assertIsFocused().assert(hasText(SampleQuestion))

            // On a normal viewport this must pass before any manual scroll. On a tiny one,
            // scrolling the actual content is the fallback, while the pinned action stays usable.
            for (label in listOf("历史记录", "设置")) {
                val action = rule.onNodeWithText(label)
                if (smallViewport) action.performScrollTo()
                action.assertIsDisplayed().assertIsEnabled()
                assertFullyInsideContentAndAboveIme(action, scenario)
            }
            question().assertIsFocused().assert(hasText(SampleQuestion))
            rule.onNodeWithText("开始起卦").assertIsDisplayed().assertIsEnabled()
            assertAboveIme(rule.onNodeWithText("开始起卦"), scenario)
            captureHome("02_home_ime.png")

            // A compact editor scrolls long text internally rather than enlarging the page.
            val compactFieldHeight = heightOf("home-question")
            question().performTextReplacement("字".repeat(200))
            question().assertIsFocused().assert(hasText("字".repeat(200)))
            rule.onNodeWithText("200/200", useUnmergedTree = true).assertExists()
            assertEquals(compactFieldHeight, heightOf("home-question"), 1f)
            assertAboveIme(rule.onNodeWithText("开始起卦"), scenario)
            question().performTextReplacement(SampleQuestion)

            // Native pointer injection goes through the real window dispatch. A semantics-only
            // click could pass even if the IME were covering the button on the device.
            nativeTap(rule.onNodeWithText("开始起卦"), scenario)
            rule.waitUntil(10_000) {
                rule.onAllNodesWithText("自动完成剩余爻").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("自动完成剩余爻").assertExists()
            assertNoSavedRecords()
        }
    }

    @Test fun closingImeAndRecreationRestoreFullHomeWithoutLosingQuestion() {
        launch().use { scenario ->
            val normalMarkHeight = heightOf("home-wordmark")
            val normalQuestionHeight = heightOf("home-question")
            openKeyboardAndType(scenario)
            awaitCompact(normalMarkHeight)
            hideKeyboard(scenario)
            awaitNormal(normalMarkHeight)
            assertEquals(normalQuestionHeight, heightOf("home-question"), 1f)
            question().assertIsFocused().assert(hasText(SampleQuestion))

            scenario.recreate()
            rule.waitForIdle()
            // Restored activities intentionally skip the launcher splash. The platform may
            // not dispatch a splash-exit callback on recreation, so do not await cold-launch visibility.
            scenario.onActivity {
                assertEquals(HomeEntranceSession.Phase.Finished, it.homeEntrance.phase)
            }
            question().assertExists()
            hideKeyboard(scenario)
            awaitNormal(normalMarkHeight)
            question().assert(hasText(SampleQuestion))
            assertEquals(normalQuestionHeight, heightOf("home-question"), 1f)
            rule.onNodeWithText("开始起卦").assertIsDisplayed().assertIsEnabled()
            assertNoSavedRecords()
        }
    }

    @Suppress("DEPRECATION")
    @Test fun transparentStatusBarAndFullWindowPaperKeepContentInsideSafeArea() {
        launch().use { scenario ->
            hideKeyboard(scenario)
            val window = windowState(scenario)
            if (InstrumentationRegistry.getArguments().getString("expectTallCutout") == "true") {
                val density = context.resources.displayMetrics.density
                assertTrue("The tall-cutout overlay must really change the safe area", window.safeTop / density > 48f)
            }
            val root = rule.onRoot().fetchSemanticsNode().boundsInWindow
            assertEquals("Compose must draw from the top of the window", 0f, root.top, 1f)
            assertEquals("Compose must draw across the full window", window.width.toFloat(), root.width, 1f)
            assertEquals("Compose must draw behind both system bars", window.height.toFloat(), root.height, 1f)

            val paper = rule.onNodeWithTag("home-paper").fetchSemanticsNode().boundsInWindow
            assertEquals("Paper must extend behind the status bar", root.top, paper.top, 1f)
            assertEquals(root.left, paper.left, 1f)
            assertEquals(root.right, paper.right, 1f)
            assertEquals(root.bottom, paper.bottom, 1f)

            val content = rule.onNodeWithTag("home-content").fetchSemanticsNode().boundsInWindow
            assertEquals("Only one safe top inset should separate content from system UI",
                window.safeTop.toFloat(), content.top, 1f)
            assertTrue("Content must avoid a left display cutout", content.left >= window.safeLeft - 1f)
            assertTrue("Content must avoid a right display cutout",
                content.right <= window.width - window.safeRight + 1f)
            scenario.onActivity { activity ->
                assertEquals("A separate status-bar fill would break the paper backdrop",
                    Color.TRANSPARENT, activity.window.statusBarColor)
                assertTrue("Light paper requires dark status-bar icons",
                    WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        .isAppearanceLightStatusBars)
            }
        }
    }

    private fun launch(): ActivityScenario<MainActivity> {
        assumeTrue("Use -I tests/home-ime.init.gradle; never run against the personal app",
            context.packageName == "com.peterpork.liuyao.imecheck.debug")
        assertNoSavedRecords()
        return ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).also {
            awaitHomeReady(it)
        }
    }

    private fun awaitHomeReady(scenario: ActivityScenario<MainActivity>) {
        rule.waitUntil(10_000) {
            rule.mainClock.advanceTimeByFrame()
            var visible = false
            scenario.onActivity { visible = it.homeEntrance.contentVisible }
            visible
        }
        rule.mainClock.advanceTimeBy(HomeEntranceMotion.TotalMillis.toLong() + 100)
        rule.waitForIdle()
        rule.onNodeWithTag("splash-skip").assertDoesNotExist()
        question().assertExists()
    }

    private fun question() = rule.onNodeWithTag("home-question")

    private fun openKeyboardAndType(scenario: ActivityScenario<MainActivity>) {
        question().performScrollTo().assertIsDisplayed()
        nativeTap(question(), scenario)
        rule.waitUntil(5_000) {
            val window = windowState(scenario)
            window.imeVisible && window.imeBottom > 0
        }
        question().assertIsFocused().performTextInput(SampleQuestion)
    }

    private fun awaitCompact(normalHeight: Float) {
        rule.mainClock.advanceTimeBy(500)
        rule.waitUntil(5_000) { heightOf("home-wordmark") <= normalHeight * 0.85f }
        rule.waitForIdle()
    }

    private fun awaitNormal(normalHeight: Float) {
        rule.mainClock.advanceTimeBy(500)
        rule.waitUntil(5_000) { abs(heightOf("home-wordmark") - normalHeight) <= 1f }
        rule.waitForIdle()
    }

    private fun hideKeyboard(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity {
            (it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.window.decorView.windowToken, 0)
        }
        rule.waitUntil(5_000) {
            val window = windowState(scenario)
            !window.imeVisible && window.imeBottom == 0
        }
    }

    // Unclipped dimensions matter on tiny viewports, where BringIntoView can scroll the logo
    // offscreen without changing its measured size. No device-specific pixels are assumed.
    private fun heightOf(tag: String): Float {
        val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    private fun fullBoundsInWindow(node: SemanticsNodeInteraction): Rect {
        val density = context.resources.displayMetrics.density
        val rootOrigin = rule.onRoot().fetchSemanticsNode().positionInWindow
        val bounds = node.getUnclippedBoundsInRoot()
        return Rect(
            rootOrigin.x + bounds.left.value * density,
            rootOrigin.y + bounds.top.value * density,
            rootOrigin.x + bounds.right.value * density,
            rootOrigin.y + bounds.bottom.value * density,
        )
    }

    private fun assertFullyInsideContentAndAboveIme(
        node: SemanticsNodeInteraction,
        scenario: ActivityScenario<MainActivity>,
    ) {
        val action = fullBoundsInWindow(node)
        val viewport = rule.onNodeWithTag("home-content").fetchSemanticsNode().boundsInWindow
        assertTrue("Action must be fully visible inside the scroll viewport: $action in $viewport",
            action.top >= viewport.top - 1f && action.bottom <= viewport.bottom + 1f)
        assertAboveIme(node, scenario)
    }

    private fun assertAboveIme(node: SemanticsNodeInteraction, scenario: ActivityScenario<MainActivity>) {
        val window = windowState(scenario)
        assertTrue("This check must exercise a real visible IME", window.imeVisible && window.imeBottom > 0)
        val bounds = fullBoundsInWindow(node)
        val imeTop = window.height - window.imeBottom
        assertTrue("Entire action must be above the keyboard: $bounds; IME top=$imeTop",
            bounds.top >= window.safeTop && bounds.bottom <= imeTop + 1f)
    }

    private fun assertImeWindowSafety(scenario: ActivityScenario<MainActivity>) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInWindow
        val paper = rule.onNodeWithTag("home-paper").fetchSemanticsNode().boundsInWindow
        val content = rule.onNodeWithTag("home-content").fetchSemanticsNode().boundsInWindow
        assertEquals(0f, root.top, 1f)
        assertEquals(0f, paper.top, 1f)
        scenario.onActivity {
            val decor = it.window.decorView
            val origin = IntArray(2)
            decor.getLocationOnScreen(origin)
            assertEquals("The entire window must not pan above the display", 0, origin[1])
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(decor))
            assertTrue("The status bar must remain visible while typing", insets.isVisible(WindowInsetsCompat.Type.statusBars()))
            assertTrue(WindowCompat.getInsetsController(it.window, decor).isAppearanceLightStatusBars)
            val safe = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            assertEquals("IME must not remove or double the top safe inset", safe.top.toFloat(), content.top, 1f)
        }
        if (!smallViewport) {
            val mark = fullBoundsInWindow(rule.onNodeWithTag("home-wordmark"))
            assertTrue("The compact logo should stay fully inside the normal viewport", mark.top >= content.top - 1f && mark.bottom <= content.bottom + 1f)
        }
    }

    private fun nativeTap(node: SemanticsNodeInteraction, scenario: ActivityScenario<MainActivity>) {
        rule.waitForIdle()
        val bounds = node.fetchSemanticsNode().boundsInWindow
        assertTrue("Touch target must have visible bounds", bounds.width > 0f && bounds.height > 0f)
        val origin = IntArray(2)
        scenario.onActivity { it.window.decorView.getLocationOnScreen(origin) }
        val x = bounds.center.x + origin[0]
        val y = bounds.center.y + origin[1]
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        instrumentation.waitForIdleSync()
    }

    private data class WindowState(
        val width: Int,
        val height: Int,
        val safeTop: Int,
        val safeLeft: Int,
        val safeRight: Int,
        val imeVisible: Boolean,
        val imeBottom: Int,
    )

    private fun windowState(scenario: ActivityScenario<MainActivity>): WindowState {
        var state: WindowState? = null
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(decor))
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            state = WindowState(decor.width, decor.height, safe.top, safe.left, safe.right,
                insets.isVisible(WindowInsetsCompat.Type.ime()), insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
        }
        return checkNotNull(state)
    }

    private fun assertNoSavedRecords() {
        val dao = (context.applicationContext as WenYaoApplication).database.dao()
        assertEquals("IME checks must not use or save history records", 0, runBlocking { dao.count() })
    }

    private fun captureHome(name: String) {
        assertNoSavedRecords()
        rule.onNodeWithTag("home-content").assertExists()
        rule.onAllNodes(hasText("末四位", substring = true)).assertCountEquals(0)
        rule.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        // SurfaceFlinger presentation can trail Compose layout; capture actual screen pixels.
        SystemClock.sleep(350)
        val directory = File(context.getExternalFilesDir(null), "home-ime-check")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, name).outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    private companion object {
        const val SampleQuestion = "IME 适配验收：本周阅读计划如何安排？"
    }
}
