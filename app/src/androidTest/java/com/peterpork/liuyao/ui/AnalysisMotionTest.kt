package com.peterpork.liuyao.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.peterpork.liuyao.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Synthetic presentation only: no credentials, network, Room, or production sample injection. */
class AnalysisMotionTest {
    @get:Rule val rule = createComposeRule()
    private val payload = AnalysisPayload("公开合成摘要", "合成趋势", emptyList(),
        List(8) { "这是用于长文本验收的合成说明，不能作为真实解读。" }.joinToString("\n"),
        "合成建议", "可信度说明：仅测试展示", "风险提示：不作为决策依据", emptyList())

    @Test fun singleCardHeightInterpolatesWithoutChangingWidth() {
        assumeTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        rule.mainClock.autoAdvance = false
        var open by mutableStateOf(false)
        rule.setContent { WenYaoTheme { AnalysisSizedCard(Modifier.width(260.dp).testTag("card")) {
            Box(Modifier.height(if (open) 160.dp else 32.dp))
        } } }
        rule.mainClock.advanceTimeBy(32)
        val initial = rule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { open = true }
        rule.mainClock.advanceTimeBy(96)
        val middle = rule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        rule.mainClock.advanceTimeBy(256)
        val final = rule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        assertTrue(middle.height > initial.height)
        assertTrue(middle.height < final.height)
        assertEquals(initial.width, final.width, .1f)
        rule.runOnIdle { open = false }
        rule.mainClock.advanceTimeBy(288)
        assertEquals(initial, rule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun zeroScaleWaitingIsStaticAndFoldCompletesImmediately() {
        assumeTrue(!android.animation.ValueAnimator.areAnimatorsEnabled())
        rule.setContent { WenYaoTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            AnalysisWaiting()
            AnalysisReading("zero", payload)
        } } }
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        rule.onNodeWithText("收起详细分析").performScrollTo().performClick()
        rule.onNodeWithText(payload.analysis).assertDoesNotExist()
        rule.onNodeWithText(payload.safetyNote).assertExists()
        rule.onNodeWithText("展开详细分析").performClick()
        rule.onNodeWithText(payload.analysis).assertExists()
    }

    @Test fun realStatesPreserveBodyAndActionsNeverAutoretry() {
        var state by mutableStateOf<AnalysisGenerationState>(AnalysisGenerationState.Running)
        var calls = 0
        rule.setContent { WenYaoTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("基础卦保持可见")
            AnalysisRegion("record", state, payload, null, {}, { calls++; state = AnalysisGenerationState.Running })
        } } }
        rule.onNodeWithText("基础卦保持可见").assertExists()
        rule.onNodeWithText(payload.summary).assertExists()
        rule.onNodeWithText("正在生成 AI 解读…").assertExists()
        rule.onNodeWithText("重新解读（可能再次产生费用）").assertDoesNotExist()
        rule.runOnIdle { state = AnalysisGenerationState.Failed(AnalysisErrorCode.TIMEOUT) }
        rule.onNodeWithText("正在生成 AI 解读…").assertDoesNotExist()
        rule.onNodeWithText(payload.summary).assertExists()
        rule.onNodeWithText("最新一次解读失败：请求超时，未自动重试以避免重复费用").assertExists()
        rule.runOnIdle { assertEquals(0, calls) }
        rule.onNodeWithText("重新解读（可能再次产生费用）").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, calls); state = AnalysisGenerationState.NotConfigured }
        rule.onNodeWithText("配置 AI").assertExists()
        rule.onNodeWithText("正在生成 AI 解读…").assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun foldingRestoresPerRecordAndKeepsWarningsAndSelection() {
        val restore = StateRestorationTester(rule)
        var id by mutableStateOf("first")
        restore.setContent { WenYaoTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            AnalysisReading(id, payload)
        } } }
        rule.onNodeWithText(payload.analysis).assertExists()
        rule.onNodeWithText("收起详细分析").performScrollTo().performClick()
        rule.onNodeWithText(payload.analysis).assertDoesNotExist()
        rule.onNodeWithText(payload.limitationsNote).assertExists()
        rule.onNodeWithText(payload.safetyNote).assertExists()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("展开详细分析").assertExists()
        rule.runOnIdle { id = "second" }
        rule.onNodeWithText(payload.analysis).assertExists()
        rule.onNodeWithText("收起详细分析").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
    }

    @Test fun backgroundStopsWaitingLoopAndExpansionRemainsFinal() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        var active = false
        rule.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            active = rememberAnalysisMotionActive()
            WenYaoTheme { AnalysisWaiting() }
        } }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.runOnIdle { assertFalse(active) }
        rule.onNodeWithText("正在生成 AI 解读…").assertExists()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
    }

    @Test fun largeTextStaticSuccessHasNoLoopAndProducesPublicEvidence() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                WenYaoTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AnalysisRegion("public", AnalysisGenerationState.Succeeded("synthetic"), payload, null, {}, {})
                } }
            }
        }
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        rule.onNodeWithText("收起详细分析").performScrollTo().performClick()
        rule.onNodeWithText(payload.safetyNote).performScrollTo().assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(isMotionTestPackage(context.packageName))
        val file = File(context.getExternalFilesDir(null), "motion-round5-large-collapsed.png")
        file.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
