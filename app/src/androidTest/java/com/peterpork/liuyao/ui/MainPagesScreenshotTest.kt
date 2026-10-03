package com.peterpork.liuyao.ui

import android.graphics.Bitmap
import android.os.SystemClock
import com.peterpork.liuyao.WenYaoApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.peterpork.liuyao.MainActivity
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Drives the real app only in its isolated screenshot package; never clears user data. */
class MainPagesScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun captureFiveMainPagesWithPublicSampleData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Use -I tests/screenshots.init.gradle", context.packageName == "com.peterpork.liuyao.screenshots.debug")
        val sample = "界面截图示例：如何安排本周的阅读时间？"
        val allowed = setOf(sample, "界面截图示例：本周学习计划如何安排？")
        val records = runBlocking { (context.applicationContext as WenYaoApplication).database.dao().observeAll().first() }
        check(records.all { it.question in allowed }) { "Screenshot package contains non-sample history; capture stopped." }
        rule.onNodeWithText("开始起卦").assertIsDisplayed()
        rule.onNodeWithContentDescription("问爻").assertIsDisplayed()
        rule.onNodeWithText("心有所问，卦有所示").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                check(getLayout(layouts))
                org.junit.Assert.assertEquals(androidx.compose.ui.text.style.TextAlign.Center,
                    layouts.single().layoutInput.style.textAlign)
            }
        rule.onAllNodesWithText("问爻").assertCountEquals(0)
        capture("01_home.png")

        rule.onNode(hasSetTextAction()).performTextInput(sample)
        rule.runOnIdle {
            val keyboard = rule.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.hideSoftInputFromWindow(rule.activity.window.decorView.windowToken, 0)
        }
        rule.waitUntil(5_000) {
            val insets = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            insets != null && !insets.isVisible(WindowInsetsCompat.Type.ime()) &&
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom == 0
        }
        rule.onNodeWithText("开始起卦").performSemanticsAction(SemanticsActions.OnClick) { it() }
        // Native renderer preparation is asynchronous, particularly on a fresh test device.
        // An existing but disabled action correctly ignores OnClick; wait for real readiness.
        rule.waitUntil(90_000) { rule.onAllNodes(hasText("自动完成剩余爻") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("自动完成剩余爻").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("生成卦象") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("生成卦象").assertIsDisplayed()
        capture("02_divination.png")

        rule.onNodeWithText("生成卦象").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitUntil(15_000) { runBlocking { (context.applicationContext as WenYaoApplication).database.dao().count() } == records.size + 1 }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("配置 AI").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("卦象结果").assertIsDisplayed()
        capture("03_result.png")

        rule.onNodeWithContentDescription("返回").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("历史记录").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onAllNodesWithText(sample)[0].assertIsDisplayed()
        capture("04_history.png")

        rule.onNodeWithContentDescription("返回").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("设置").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("尚未配置 API Key").assertExists()
        rule.onNodeWithText("API Key", useUnmergedTree = true).performScrollTo()
        capture("05_settings.png")
    }

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onAllNodes(hasText("末四位", substring = true)).assertCountEquals(0)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "main-page-screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        // Semantics can lead SurfaceFlinger by a frame; allow the device to present the settled UI.
        SystemClock.sleep(350)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
