package com.peterpork.liuyao.ui

import com.peterpork.liuyao.data.*
import com.peterpork.liuyao.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WenYaoViewModelTest {
    @Test fun `stale repeated tap does not generate or append another cast`() = runTest(dispatcher) {
        var calls = 0
        val vm = WenYaoViewModel(FakeDao(), RecordingAnalysis(false) { fixedResult() }, FakeCredentials(), FakeSettings(),
            CoinCaster { CoinCastResult("tap-${calls++}", listOf(CoinSide.TAILS, CoinSide.HEADS, CoinSide.TAILS)) })
        assertEquals(null, vm.castOne(0))
        assertTrue(vm.castRemaining().isEmpty())
        assertEquals(0, calls)
        vm.begin("连点测试")
        val first = vm.castOne(0)!!
        repeat(20) { assertEquals(null, vm.castOne(0)) }
        assertEquals(1, calls)
        assertEquals(listOf(first), vm.castingState.value.castsBottomUp)
        assertEquals(listOf(2, 3, 2), first.coins.map { it.points })
        assertEquals(7, first.total)
    }

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `finish saves basic record before starting configured analysis`() = runTest(dispatcher) {
        val dao = FakeDao()
        val analysis = RecordingAnalysis(configured = true) { assertEquals(1, dao.saved.size); fixedResult() }
        val vm = viewModel(dao, analysis)
        vm.begin("测试问题"); vm.castRemaining(); vm.finish { }
        advanceUntilIdle()
        assertEquals(1, dao.saved.size)
        assertEquals(1, dao.revisions.size)
        assertEquals("SUCCESS", dao.revisions.single().status)
    }

    @Test fun `unconfigured mode saves divination and performs no model request`() = runTest(dispatcher) {
        val dao = FakeDao(); val analysis = RecordingAnalysis(false) { fixedResult() }; val vm = viewModel(dao, analysis)
        vm.begin("未配置测试"); vm.castRemaining(); var id = ""; vm.finish { id = it }; advanceUntilIdle()
        assertEquals(1, dao.saved.size); assertEquals(0, analysis.calls); assertEquals(0, dao.revisions.size)
        assertTrue(vm.analysisState(id) is AnalysisGenerationState.NotConfigured)
    }

    @Test fun `analysis failure preserves basic record and stores terminal revision`() = runTest(dispatcher) {
        val dao = FakeDao(); val analysis = RecordingAnalysis(true) { throw AnalysisException(AnalysisErrorCode.RATE_LIMITED) }
        val vm = viewModel(dao, analysis)
        vm.begin("失败测试"); vm.castRemaining(); vm.finish { }; advanceUntilIdle()
        assertEquals("乾为天", dao.saved.single().originalName)
        assertEquals("FAILED", dao.revisions.single().status)
        assertEquals("RATE_LIMITED", dao.revisions.single().errorCode)
    }

    @Test fun `finish and generation ignore duplicate submissions`() = runTest(dispatcher) {
        val dao = FakeDao(); val analysis = RecordingAnalysis(true) { fixedResult() }; val vm = viewModel(dao, analysis)
        vm.begin("防重复测试"); vm.castRemaining(); var id = ""; vm.finish { id = it }; vm.finish { }; advanceUntilIdle()
        vm.generateAnalysis(id); advanceUntilIdle()
        assertEquals(1, dao.saved.size)
        assertEquals(2, analysis.calls) // the later explicit retry is a new user action
    }

    @Test fun `begin locks cast time and timezone`() = runTest(dispatcher) {
        val locked = Instant.parse("2026-09-13T23:59:59Z")
        val dao = FakeDao(); val settings = FakeSettings(AppSettings(true, "UTC"))
        val vm = WenYaoViewModel(dao, RecordingAnalysis(false) { fixedResult() }, FakeCredentials(), settings,
            fixedYangCaster(), Clock.fixed(locked, ZoneOffset.UTC))
        advanceUntilIdle(); vm.begin("时间测试"); settings.update(AppSettings(false, "Asia/Shanghai")); vm.castRemaining(); vm.finish { }; advanceUntilIdle()
        assertEquals(locked.toEpochMilli(), dao.saved.single().castAtEpochMillis)
        assertEquals("UTC", dao.saved.single().timezoneId)
    }

    @Test fun `failed retry keeps previous successful revision`() = runTest(dispatcher) {
        val dao = FakeDao(); var fail = false
        val analysis = RecordingAnalysis(true) { if (fail) throw AnalysisException(AnalysisErrorCode.TIMEOUT) else fixedResult() }
        val vm = viewModel(dao, analysis)
        vm.begin("重试保留测试"); vm.castRemaining(); var id = ""; vm.finish { id = it }; advanceUntilIdle()
        fail = true; vm.generateAnalysis(id); advanceUntilIdle()
        val detail = vm.record(id)!!
        assertEquals("摘要", detail.analysis?.summary)
        assertEquals("FAILED", detail.latestAttempt?.status)
        assertEquals(2, dao.revisions.size)
    }

    private fun viewModel(dao: FakeDao, analysis: AnalysisRepository) = WenYaoViewModel(
        dao, analysis, FakeCredentials(), FakeSettings(), fixedYangCaster()
    )

    @Test fun `cast updates authoritative state without animation callback`() = runTest(dispatcher) {
        val vm = viewModel(FakeDao(), RecordingAnalysis(false) { fixedResult() })
        vm.begin("立即更新测试")
        val result = vm.castOne()!!
        assertEquals(listOf(result), vm.castingState.value.castsBottomUp)
        assertEquals(listOf(LineValue.YOUNG_YANG), vm.castingState.value.linesBottomUp)
    }

    @Test fun `auto cast immediately fills authoritative state and cannot exceed six lines`() = runTest(dispatcher) {
        val vm = viewModel(FakeDao(), RecordingAnalysis(false) { fixedResult() })
        vm.begin("自动完成测试")
        val generated = vm.castRemaining()
        assertEquals(6, generated.size)
        assertEquals(6, vm.castingState.value.castsBottomUp.size)
        assertTrue(vm.castRemaining().isEmpty())
        assertEquals(null, vm.castOne())
    }

    @Test fun `ink interruption replay and six completions never change truth or business counts`() = runTest(dispatcher) {
        val values = listOf(6, 7, 8, 9, 7, 7)
        var tosses = 0
        val caster = CoinCaster {
            val total = values[tosses]
            CoinCastResult("ink-${tosses++}", List(3) { if (it < total - 6) CoinSide.HEADS else CoinSide.TAILS })
        }
        val dao = FakeDao()
        val analysis = RecordingAnalysis(true) { fixedResult() }
        val vm = WenYaoViewModel(dao, analysis, FakeCredentials(), FakeSettings(), caster)
        vm.begin("墨线确定输入测试")
        values.forEachIndexed { index, value ->
            val cast = vm.castOne()!!
            val snapshot = vm.castingState.value
            assertEquals(value, cast.total)
            val ticket = NewLineInk(snapshot.castAt, cast.castId, index + 1)
            if (index % 2 == 0) ticket.reveal.start()
            ticket.reveal.finish(); ticket.reveal.finish(); assertTrue(!ticket.reveal.start())
            var coin: RevealState = RevealState.Idle.replay(cast.castId)
            repeat(3) { coin = coin.skip().replay(cast.castId) }
            coin.cancel()
            assertEquals(snapshot, vm.castingState.value)
            assertEquals(index + 1, tosses)
            assertEquals(0, dao.saveCalls); assertEquals(0, analysis.calls)
        }
        var navigations = 0
        val completion = InkReveal()
        vm.finish { navigations++; completion.start(); completion.finish() }
        vm.finish { navigations++ }
        advanceUntilIdle()
        repeat(3) { completion.finish(); completion.start() }
        assertEquals(values, vm.castingState.value.linesBottomUp.map { it.value })
        assertEquals(6, tosses); assertEquals(1, dao.saved.size); assertEquals(1, dao.saveCalls)
        assertEquals(1, navigations); assertEquals(1, analysis.calls)
        assertEquals("6,7,8,9,7,7", dao.saved.single().lineValues)
    }
}

private fun fixedYangCaster(): CoinCaster {
    var sequence = 0
    return CoinCaster {
        CoinCastResult("test-${sequence++}", listOf(CoinSide.HEADS, CoinSide.TAILS, CoinSide.TAILS))
    }
}

private fun fixedResult() = AnalysisResult(AnalysisPayload(
    "摘要", "平稳", listOf(KeyFactor("本卦", "说明", listOf("PRIMARY_HEXAGRAM"))), "分析", "建议", "局限", "安全", listOf("LINE_VALUES")
))

private class RecordingAnalysis(private val configured: Boolean, private val block: suspend (AiAnalysisInput) -> AnalysisResult) : AnalysisRepository {
    var calls = 0
    override suspend fun isConfigured() = configured
    override suspend fun analyze(input: AiAnalysisInput): AnalysisResult { calls++; return block(input) }
    override suspend fun testConnection() = ConnectionTestResult(true)
}

private class FakeCredentials : AiCredentialStore {
    private var value: String? = null
    override suspend fun save(providerId: ProviderId, credential: String) { value = credential }
    override suspend fun load(providerId: ProviderId) = value
    override suspend fun delete(providerId: ProviderId) { value = null }
}

private class FakeDao : DivinationDao {
    var saveCalls = 0
    val saved = mutableListOf<DivinationRecord>(); val revisions = mutableListOf<AnalysisRevisionEntity>()
    private val flow = MutableStateFlow<List<DivinationRecord>>(emptyList())
    override fun observeAll(): Flow<List<DivinationRecord>> = flow
    override suspend fun get(id: String) = saved.firstOrNull { it.id == id }
    override suspend fun save(record: DivinationRecord) { saveCalls++; saved.removeAll { it.id == record.id }; saved += record; flow.value = saved.toList() }
    override suspend fun delete(record: DivinationRecord) { saved.remove(record); revisions.removeAll { it.divinationId == record.id }; flow.value = saved.toList() }
    override suspend fun saveAnalysisRevision(revision: AnalysisRevisionEntity) { revisions.removeAll { it.id == revision.id }; revisions += revision }
    override suspend fun analysisRevisions(divinationId: String) = revisions.filter { it.divinationId == divinationId }.sortedByDescending { it.createdAtEpochMillis }
    override suspend fun latestSuccessfulAnalysis(divinationId: String) = analysisRevisions(divinationId).firstOrNull { it.status == "SUCCESS" }
    override suspend fun latestAnalysisAttempt(divinationId: String) = analysisRevisions(divinationId).firstOrNull()
    override suspend fun count() = saved.size
}

private class FakeSettings(initial: AppSettings = AppSettings()) : SettingsStore {
    private val state = MutableStateFlow(initial); override val settings: Flow<AppSettings> = state
    override suspend fun setExperimentalProfessionalEnabled(enabled: Boolean) { state.value = state.value.copy(experimentalProfessionalEnabled = enabled) }
    override suspend fun acceptExperimentalProfessional(consentVersion: Int) { state.value = state.value.copy(experimentalProfessionalEnabled = true, experimentalConsentVersion = consentVersion) }
    override suspend fun setZoneId(zoneId: String) { state.value = state.value.copy(zoneId = zoneId) }
    override suspend fun setAiProvider(providerId: ProviderId) { state.value = state.value.copy(aiProvider = providerId) }
    fun update(value: AppSettings) { state.value = value }
}
