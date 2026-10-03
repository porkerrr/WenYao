package com.peterpork.liuyao.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.peterpork.liuyao.MainActivity
import com.peterpork.liuyao.WenYaoApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Public synthetic questions only, in an isolated package. No credentials or AI requests.
 * Native OnClick semantics isolate route/state assertions from keyboard-driven moving hit targets.
 * Physical clicks, cancellation and edge gestures are separately exercised on the same components.
 */
class NavigationMotionActivityTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun actualToolbarsIgnoreSameFrameTripleClick() {
        assumeTrue("Use an isolated motion test package", isMotionTestPackage(context.packageName))
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).use { scenario ->
            awaitHomeInput(scenario)
            rule.onNode(hasSetTextAction()).performTextInput("导航验收：返回连点测试")
            hideKeyboard(scenario)
            for (label in listOf("历史记录", "设置", "开始起卦")) {
                val entryButton = rule.onNodeWithText(label)
                if (label != "开始起卦") entryButton.performScrollTo()
                entryButton.performSemanticsAction(SemanticsActions.OnClick) { it() }
                rule.waitForIdle()
                // Capture the actual callback once and invoke it without allowing a frame between clicks.
                // Normal performClick calls wait for idle and would hide the original race.
                val backNode = rule.onNodeWithContentDescription("返回")
                val click = checkNotNull(backNode.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
                rule.runOnUiThread { repeat(3) { click() } }
                rule.waitForIdle()
                rule.onNodeWithText("开始起卦").assertIsDisplayed()
            }
            scenario.recreate()
            rule.onNodeWithText("开始起卦").assertIsDisplayed()
        }
    }

    @Test fun actualRoutesRetainDraftScrollExpansionAndSaveExactlyOnce() {
        assumeTrue("Use tests/motion.init.gradle", isMotionTestPackage(context.packageName))
        val app = context.applicationContext as WenYaoApplication
        val dao = app.database.dao()
        val original = runBlocking { dao.observeAll().first() }
        check(original.all { it.question.startsWith("导航验收") }) { "Only public navigation fixtures may be present" }
        val sample = "导航验收：本周阅读计划如何安排？"
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).use { scenario ->
            awaitHomeInput(scenario)
            val initialHeight = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
            rule.onNode(hasSetTextAction()).performTextInput(sample)
            hideKeyboard(scenario)
            rule.onNodeWithText("历史记录").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            awaitScreen("历史卦例")
            back(scenario)
            rule.onNode(hasSetTextAction()).assertTextContains(sample)
            rule.onNodeWithText("设置").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("尚未配置 API Key").assertExists()
            back(scenario)
            scenario.recreate()
            hideKeyboard(scenario)
            rule.waitUntil(5_000) { rule.onRoot().fetchSemanticsNode().boundsInRoot.height == initialHeight }
            rule.onNode(hasSetTextAction()).assertTextContains(sample)
            rule.onNodeWithText("开始起卦").performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("从初爻向上生成 · 0/6").assertExists()
            back(scenario)
            rule.onNode(hasSetTextAction()).assertTextContains(sample)
            rule.onNodeWithText("开始起卦").performSemanticsAction(SemanticsActions.OnClick) { it() }
            clickWhenEnabled("自动完成剩余爻")
            clickWhenEnabled("生成卦象")
            awaitResult(scenario)
            val record = runBlocking { dao.observeAll().first().first() }
            assertEquals(original.size + 1, runBlocking { dao.count() })
            rule.onNodeWithText("查看专业信息").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("配置 AI").performScrollTo()
            val detailScroll = rule.onNode(hasScrollAction()).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            rule.onNodeWithText("配置 AI").performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("尚未配置 API Key").assertExists()
            back(scenario)
            awaitResult(scenario)
            assertEquals(detailScroll, rule.onNode(hasScrollAction()).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
            rule.onNodeWithText("收起专业信息").performScrollTo().assertIsDisplayed()
            rule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            rule.mainClock.advanceTimeBy(96)
            rule.waitForIdle()
            val expandedScroll = rule.onNode(hasScrollAction()).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("Exercise a nonzero detail scroll position", expandedScroll > 0f)
            rule.onNodeWithText("配置 AI").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("尚未配置 API Key").assertExists()
            back(scenario)
            awaitResult(scenario)
            assertEquals(expandedScroll, rule.onNode(hasScrollAction()).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            rule.onNodeWithText("收起专业信息").assertExists()
            scenario.recreate()
            awaitResult(scenario)
            rule.onNodeWithText("收起专业信息").assertExists()
            val restoredRange = rule.onNode(hasScrollAction()).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
            assertEquals("Restored scroll maximum=${restoredRange.maxValue()}", expandedScroll, restoredRange.value(), 1f)
            // The existing toolbar intentionally clears the stack and starts a new home entry.
            rule.onNodeWithContentDescription("返回").performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("开始起卦").assertExists()
            runBlocking {
                repeat(20) { index -> dao.save(record.copy(id = "navigation-fixture-$index",
                    question = "导航验收列表 $index", createdAtEpochMillis = record.createdAtEpochMillis - index - 1)) }
            }
            val count = runBlocking { dao.count() }
            rule.onNodeWithText("历史记录").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            val list = rule.onNode(hasScrollToIndexAction())
            list.performScrollToIndex(12)
            val scrollBefore = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            rule.onNodeWithText("导航验收列表 11").performSemanticsAction(SemanticsActions.OnClick) { it() }
            awaitResult(scenario)
            back(scenario)
            rule.onNodeWithText("历史卦例").assertIsDisplayed()
            assertEquals(scrollBefore, rule.onNode(hasScrollToIndexAction()).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.01f)
            back(scenario)
            rule.onNodeWithText("开始起卦").assertExists()
            scenario.onActivity { assertEquals(HomeEntranceSession.Phase.Finished, it.homeEntrance.phase) }
            assertEquals(count, runBlocking { dao.count() })
            assertTrue(runBlocking { dao.analysisRevisions(record.id).isEmpty() })
        }
    }

    private fun clickWhenEnabled(text: String) {
        // Production 3D preparation/reveal is asynchronous and is not an Espresso idling resource.
        rule.waitUntil(30_000) {
            rule.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun awaitHomeInput(scenario: ActivityScenario<MainActivity>) {
        // These tests start after branding. Its platform-splash callback is outside Compose's
        // test clock; leaving it pending hides input semantics on some physical devices.
        // Startup animation behavior has its own HomeEntranceActivityTest coverage.
        scenario.onActivity { it.homeEntrance.finish() }
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitResult(scenario: ActivityScenario<MainActivity>) {
        rule.waitUntil(15_000) { rule.onAllNodesWithText("配置 AI").fetchSemanticsNodes().isNotEmpty() }
        // None transitions still need native composition/layout cleanup frames (see host test).
        rule.mainClock.advanceTimeBy(96)
        // Compare saved offsets at the same viewport size, after the platform's IME resize.
        hideKeyboard(scenario)
        rule.waitForIdle()
        var bottomInset = 0
        scenario.onActivity {
            bottomInset = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        }
        // isVisible changes at the START of the IME closing animation. Compose's animated insets
        // can still reduce the viewport, especially when our own transitions have zero duration.
        rule.waitUntil(5_000) {
            val node = rule.onNode(hasScrollAction()).fetchSemanticsNode()
            val finalBottom = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom - bottomInset
            // Short content legitimately wraps; overflowing content must have the full viewport.
            node.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() == 0f ||
                kotlin.math.abs(node.boundsInRoot.bottom - finalBottom) < 1f
        }
    }
    private fun hideKeyboard(scenario: ActivityScenario<MainActivity>) {
        // Finish the real IME resize before touching the scrollable navigation row.
        // Otherwise BringIntoView may move that row back under the pinned action bar mid-tap.
        scenario.onActivity {
            (it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(it.window.decorView.windowToken, 0)
        }
        rule.waitUntil(5_000) {
            var hidden = false
            scenario.onActivity {
                hidden = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == false
            }
            hidden
        }
    }

    private fun awaitScreen(title: String) {
        rule.waitUntil(5_000) { rule.onNodeWithText(title).isDisplayed() }
    }
    private fun back(scenario: ActivityScenario<MainActivity>) {
        // Let the native destination install its back callback before dispatching a system Back.
        // Immediate reversal while the transition is running is covered by the frozen-clock host test.
        rule.mainClock.advanceTimeBy(96)
        rule.waitForIdle()
        rule.waitUntil(5_000) {
            var enabled = false
            scenario.onActivity { enabled = it.onBackPressedDispatcher.hasEnabledCallbacks() }
            enabled
        }
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(96)
        rule.waitForIdle()
    }
}
