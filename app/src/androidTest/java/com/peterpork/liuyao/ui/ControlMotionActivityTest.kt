package com.peterpork.liuyao.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.peterpork.liuyao.MainActivity
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real home/casting flow on an isolated package. Never saves a record or requests AI. */
class ControlMotionActivityTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun homeCancellationThenSingleCastRetainsExistingNavigationAndBusyGuard() {
        assumeTrue("Use tests/motion.init.gradle", isMotionTestPackage(context.packageName))
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).use { scenario ->
            rule.onNode(hasSetTextAction()).performTextInput("动效验收：本周阅读安排如何？")
            // Keep native focus/IME behavior; hide the keyboard before exercising the pinned action.
            rule.onNodeWithText("开始起卦").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            val start = rule.onNodeWithText("开始起卦")
            start.performTouchInput { down(center); moveTo(Offset(center.x, -200f)); up() }
            rule.onNodeWithText("你想问什么？").assertExists()
            start.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(128)
            save("home-pressed")
            start.performTouchInput { up() }
            val toss = rule.onNodeWithText("掷铜钱")
            toss.assertIsEnabled()
            rule.mainClock.autoAdvance = false
            toss.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(128)
            save("casting-pressed")
            toss.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(32)
            toss.assertIsNotEnabled()
            rule.onNodeWithText("自动完成剩余爻").assertIsNotEnabled()
            repeat(5) { toss.performTouchInput { click() } }
            rule.onNodeWithText("从初爻向上生成 · 1/6").assertIsDisplayed()
            rule.onNodeWithText("跳过动画").performClick()
            rule.mainClock.autoAdvance = true
            toss.assertIsEnabled()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            toss.assertIsEnabled()
            rule.onNodeWithText("从初爻向上生成 · 1/6").assertIsDisplayed()
            save("casting-after-resume")
            rule.onNodeWithContentDescription("返回").performClick()
            rule.onNodeWithText("开始起卦").assertIsEnabled()
        }
    }

    private fun save(name: String) {
        val directory = File(context.filesDir, "motion-round2").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
