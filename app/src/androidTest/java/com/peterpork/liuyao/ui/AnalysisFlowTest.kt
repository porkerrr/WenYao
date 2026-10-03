package com.peterpork.liuyao.ui

import android.graphics.Bitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.peterpork.liuyao.data.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real app screens/ViewModel with memory-only records and a controlled, non-network repository. */
class AnalysisFlowTest {
    @get:Rule val rule = createComposeRule()
    @Test fun sixCastsFailureManualRetrySuccessFoldHistoryAndRapidReturn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(isMotionTestPackage(context.packageName))
        val dao = MotionMemoryDao()
        val replies = Channel<AnalysisResult>(Channel.UNLIMITED)
        var calls = 0
        val repo = object : AnalysisRepository {
            override suspend fun isConfigured() = true
            override suspend fun analyze(input: AiAnalysisInput): AnalysisResult {
                calls++
                assertEquals(6, input.lineValues.size)
                if (calls == 1) { replies.receive(); throw AnalysisException(AnalysisErrorCode.TIMEOUT) }
                return replies.receive()
            }
            override suspend fun testConnection(): ConnectionTestResult = error("Not used")
        }
        val credential = object : AiCredentialStore {
            override suspend fun load(providerId: ProviderId): String? = null
            override suspend fun save(providerId: ProviderId, credential: String) = error("Not used")
            override suspend fun delete(providerId: ProviderId) = error("Not used")
        }
        val restore = StateRestorationTester(rule)
        restore.setContent { WenYaoTheme {
            WenYaoApp(dao, repo, credential, remember { SettingsRepository(context) },
                remember { HomeEntranceSession(false, true) })
        } }
        fun click(text: String) {
            val node = rule.onNodeWithText(text)
            if (text !in listOf("开始起卦", "掷铜钱", "生成卦象")) node.performScrollTo()
            node.performSemanticsAction(SemanticsActions.OnClick) { it() }
        }
        fun save(name: String) {
            rule.waitForIdle()
            File(context.getExternalFilesDir(null), "motion-round5-$name.png").outputStream().use {
                rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("导航验收：AI 合成集成流程")
        click("开始起卦")
        repeat(6) { click("掷铜钱") }
        click("生成卦象")
        rule.waitUntil(10_000) { calls == 1 }
        rule.onNodeWithText("正在生成 AI 解读…").performScrollTo().assertIsDisplayed()
        save("waiting")
        val values = dao.saved.value.single().lineValues
        val payload = AnalysisPayload("公开合成摘要", "公开合成趋势", emptyList(),
            List(6) { "合成长文本用于动效验收，不是真实模型解读。" }.joinToString("\n"),
            "请独立核实信息", "仅测试展示，不代表真实可信度", "不作为医疗、法律或财务决策依据", emptyList())
        replies.trySend(AnalysisResult(payload))
        rule.waitUntil(10_000) { dao.revisions.size == 1 }
        rule.onNodeWithText("最新一次解读失败：请求超时，未自动重试以避免重复费用").performScrollTo()
        save("failure")
        click("生成 AI 解读")
        rule.waitUntil(10_000) { calls == 2 }
        rule.onNodeWithText("生成 AI 解读").assertDoesNotExist()
        replies.trySend(AnalysisResult(payload))
        rule.waitUntil(10_000) { dao.revisions.size == 2 }
        rule.onNodeWithText(payload.summary).performScrollTo().assertIsDisplayed()
        save("success")
        click("收起详细分析")
        rule.onNodeWithText(payload.analysis).assertDoesNotExist()
        save("collapsed")
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("展开详细分析").assertExists()
        click("展开详细分析")
        rule.onNodeWithText(payload.analysis).assertExists()
        click("收起详细分析")
        click("查看专业信息")
        click("收起专业信息")
        rule.onNodeWithContentDescription("返回").performClick()
        click("历史记录")
        repeat(2) {
            click("导航验收：AI 合成集成流程")
            rule.onNodeWithText(payload.summary).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("展开详细分析").assertExists()
            // Tool-bar returns home by the existing route contract.
            rule.onNodeWithContentDescription("返回").performClick()
            click("历史记录")
        }
        rule.runOnIdle {
            assertEquals(1, dao.saveCalls)
            assertEquals(1, dao.saved.value.size)
            assertEquals(values, dao.saved.value.single().lineValues)
            assertEquals(2, calls) // first attempt plus exactly one explicit retry
            assertEquals(listOf("FAILED", "SUCCESS"), dao.revisions.map { it.status })
        }
    }
}

private class MotionMemoryDao : DivinationDao {
    var saveCalls = 0
    val saved = MutableStateFlow<List<DivinationRecord>>(emptyList())
    val revisions = mutableListOf<AnalysisRevisionEntity>()
    override fun observeAll() = saved
    override suspend fun get(id: String) = saved.value.firstOrNull { it.id == id }
    override suspend fun save(record: DivinationRecord) { saveCalls++; saved.value = saved.value + record }
    override suspend fun delete(record: DivinationRecord) = error("No data deletion in this test")
    override suspend fun saveAnalysisRevision(revision: AnalysisRevisionEntity) { revisions += revision }
    override suspend fun analysisRevisions(divinationId: String) = revisions.filter { it.divinationId == divinationId }.sortedByDescending { it.createdAtEpochMillis }
    override suspend fun latestSuccessfulAnalysis(divinationId: String) = analysisRevisions(divinationId).firstOrNull { it.status == "SUCCESS" }
    override suspend fun latestAnalysisAttempt(divinationId: String) = analysisRevisions(divinationId).firstOrNull()
    override suspend fun count() = saved.value.size
}
