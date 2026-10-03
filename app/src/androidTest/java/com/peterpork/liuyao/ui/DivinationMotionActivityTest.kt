package com.peterpork.liuyao.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.peterpork.liuyao.MainActivity
import com.peterpork.liuyao.WenYaoApplication
import com.peterpork.liuyao.domain.CoinCastResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Actual app integration, public isolated records only; deterministic 6/7/8/9 is in unit/pixel tests. */
class DivinationMotionActivityTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun sixThrowsReplayBackgroundRecreationAndHistoryKeepCommittedTruth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(isMotionTestPackage(context.packageName))
        val dao = (context.applicationContext as WenYaoApplication).database.dao()
        val before = runBlocking { dao.observeAll().first() }
        check(before.all { it.question.startsWith("导航验收") })
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).use { scenario ->
            lateinit var vm: WenYaoViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[WenYaoViewModel::class.java] }
            rule.onNode(hasSetTextAction()).performTextInput("导航验收：六次投掷公开样例")
            // No real request is allowed in this public-data integration run.
            rule.onNodeWithText("设置").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("尚未配置 API Key").assertExists()
            rule.onNodeWithContentDescription("返回").performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("开始起卦").performSemanticsAction(SemanticsActions.OnClick) { it() }
            val committed = mutableListOf<CoinCastResult>()
            repeat(6) { index ->
                var oldTop = 0f
                var actionTop = 0f
                if (index == 5) {
                    rule.onNodeWithText("初爻").performScrollTo()
                    oldTop = rule.onNodeWithText("初爻").fetchSemanticsNode().boundsInRoot.top
                    actionTop = rule.onNodeWithText("掷铜钱").fetchSemanticsNode().boundsInRoot.top
                }
                rule.onNodeWithText("掷铜钱").performSemanticsAction(SemanticsActions.OnClick) { it() }
                rule.runOnIdle {
                    assertEquals(index + 1, vm.castingState.value.castsBottomUp.size)
                    committed += vm.castingState.value.castsBottomUp.last()
                    assertEquals(committed, vm.castingState.value.castsBottomUp)
                }
                if (index == 5) {
                    assertEquals(oldTop, rule.onNodeWithText("初爻").fetchSemanticsNode().boundsInRoot.top, 1f)
                    assertEquals(actionTop, rule.onNodeWithText("生成卦象").fetchSemanticsNode().boundsInRoot.top, 1f)
                }
                if (index == 0) {
                    rule.onNodeWithText("重播").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.recreate()
                    rule.waitForIdle()
                    scenario.onActivity { vm = ViewModelProvider(it)[WenYaoViewModel::class.java] }
                    rule.runOnIdle { assertEquals(committed, vm.castingState.value.castsBottomUp) }
                }
            }
            assertEquals(before.size, runBlocking { dao.count() })
            rule.onNodeWithText("生成卦象").performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.waitUntil(15_000) { rule.onAllNodesWithText("配置 AI").fetchSemanticsNodes().isNotEmpty() }
            val record = runBlocking { dao.observeAll().first().first() }
            assertEquals(before.size + 1, runBlocking { dao.count() })
            assertEquals(committed.joinToString(",") { it.total.toString() }, record.lineValues)
            assertTrue(runBlocking { dao.analysisRevisions(record.id).isEmpty() })
            scenario.recreate()
            rule.waitUntil(15_000) { rule.onAllNodesWithText("配置 AI").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("返回").performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onNodeWithText("历史记录").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.onAllNodesWithText("导航验收：六次投掷公开样例")[0].performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.waitUntil(15_000) { rule.onAllNodesWithText("配置 AI").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(record.lineValues, runBlocking { dao.get(record.id)!!.lineValues })
            assertEquals(before.size + 1, runBlocking { dao.count() })
            assertTrue(runBlocking { dao.analysisRevisions(record.id).isEmpty() })
        }
    }
}
