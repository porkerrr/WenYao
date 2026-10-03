package com.peterpork.liuyao.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.peterpork.liuyao.data.*
import com.peterpork.liuyao.domain.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CastingUiState(
    val question: String = "",
    val castsBottomUp: List<CoinCastResult> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val completedRecordId: String? = null,
    val castAt: Instant? = null,
    val zoneId: String? = null,
    val experimentalProfessionalEnabled: Boolean = false
) {
    val linesBottomUp: List<LineValue> get() = castsBottomUp.map(CoinCastResult::lineValue)
}

sealed interface AnalysisGenerationState {
    data object Idle : AnalysisGenerationState
    data object NotConfigured : AnalysisGenerationState
    data object Running : AnalysisGenerationState
    data class Succeeded(val revisionId: String) : AnalysisGenerationState
    data class Failed(val errorCode: AnalysisErrorCode) : AnalysisGenerationState
}

data class AiSettingsUiState(
    val configured: Boolean = false,
    val lastFour: String? = null,
    val busy: Boolean = false,
    val message: String? = null
)

class WenYaoViewModel(
    private val dao: DivinationDao,
    private val analysisRepository: AnalysisRepository,
    private val credentialStore: AiCredentialStore,
    private val settingsRepository: SettingsStore,
    private val coinCaster: CoinCaster = SecureCoinCaster(),
    private val clock: Clock = Clock.systemUTC(),
    private val professionalEngine: ProfessionalChartEngine = ExperimentalProfessionalChartEngine()
) : ViewModel() {
    val history = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var castingState = mutableStateOf(CastingUiState()); private set
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    var aiSettingsState = mutableStateOf(AiSettingsUiState()); private set
    private val generationStates = mutableStateMapOf<String, AnalysisGenerationState>()
    private val analysisJobs = mutableMapOf<String, Job>()
    private val json = Json { ignoreUnknownKeys = true }

    init { viewModelScope.launch { refreshCredentialState() } }

    fun analysisState(recordId: String): AnalysisGenerationState = generationStates[recordId] ?: AnalysisGenerationState.Idle

    fun refreshAnalysisAvailability(recordId: String) = viewModelScope.launch {
        if (analysisJobs[recordId]?.isActive == true) return@launch
        generationStates[recordId] = if (analysisRepository.isConfigured()) AnalysisGenerationState.Idle else AnalysisGenerationState.NotConfigured
    }

    fun begin(question: String): Boolean {
        val clean = question.trim()
        if (clean.length !in 2..200) {
            castingState.value = CastingUiState(question = clean, error = "请输入 2～200 个字符的问题")
            return false
        }
        val current = castingState.value
        if (current.castAt != null && current.completedRecordId == null) return true
        val active = settings.value
        castingState.value = CastingUiState(clean, castAt = clock.instant(), zoneId = active.zoneId, experimentalProfessionalEnabled = active.experimentalProfessionalEnabled)
        return true
    }

    fun castOne(expectedCount: Int = castingState.value.castsBottomUp.size): CoinCastResult? {
        val state = castingState.value
        if (state.castAt == null || state.castsBottomUp.size != expectedCount || state.castsBottomUp.size >= 6 || state.isSaving) return null
        val result = coinCaster.cast()
        castingState.value = state.copy(castsBottomUp = state.castsBottomUp + result, error = null)
        return result
    }
    fun castRemaining(): List<CoinCastResult> {
        val added = mutableListOf<CoinCastResult>()
        while (castingState.value.castsBottomUp.size < 6) added += castOne() ?: break
        return added
    }
    fun reset() { castingState.value = CastingUiState() }

    fun finish(onSaved: (String) -> Unit) {
        val state = castingState.value
        if (state.linesBottomUp.size != 6 || state.isSaving || state.completedRecordId != null) return
        castingState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            runCatching {
                val paipan = LiuyaoEngine.calculate(state.linesBottomUp)
                val castAt = requireNotNull(state.castAt)
                val zoneId = ZoneId.of(requireNotNull(state.zoneId))
                val professional = if (state.experimentalProfessionalEnabled) {
                    runCatching { professionalEngine.calculate(ProfessionalChartInput(paipan, castAt, zoneId)) }
                        .getOrElse { ProfessionalGenerationResult.failed(ProfessionalErrorCode.PROFESSIONAL_ENGINE_FAILED) }
                } else ProfessionalGenerationResult.notRequested()
                val id = UUID.randomUUID().toString()
                withContext(NonCancellable) { dao.save(paipan.toBasicRecord(id, state.question, castAt, zoneId, professional)) }
                id
            }.onSuccess { id ->
                castingState.value = castingState.value.copy(isSaving = false, completedRecordId = id)
                onSaved(id)
                generateAnalysis(id)
            }.onFailure { error -> castingState.value = castingState.value.copy(isSaving = false, error = error.message ?: "保存失败") }
        }
    }

    fun generateAnalysis(recordId: String) {
        if (analysisJobs[recordId]?.isActive == true) return
        analysisJobs[recordId] = viewModelScope.launch {
            if (!analysisRepository.isConfigured()) {
                generationStates[recordId] = AnalysisGenerationState.NotConfigured
                return@launch
            }
            generationStates[recordId] = AnalysisGenerationState.Running
            val record = dao.get(recordId) ?: return@launch
            val input = record.toAiInput()
            runCatching { analysisRepository.analyze(input) }
                .onSuccess { result ->
                    val createdAt = maxOf(result.generatedAtEpochMillis, (dao.latestAnalysisAttempt(recordId)?.createdAtEpochMillis ?: 0L) + 1L)
                    val revision = result.toEntity(recordId, input, json, createdAt)
                    dao.saveAnalysisRevision(revision)
                    generationStates[recordId] = AnalysisGenerationState.Succeeded(revision.id)
                }
                .onFailure { error ->
                    val code = error.toAnalysisCode()
                    val createdAt = maxOf(clock.instant().toEpochMilli(), (dao.latestAnalysisAttempt(recordId)?.createdAtEpochMillis ?: 0L) + 1L)
                    val revision = failedRevision(recordId, code, settings.value.aiProvider.profile(), createdAt)
                    dao.saveAnalysisRevision(revision)
                    generationStates[recordId] = AnalysisGenerationState.Failed(code)
                }
        }
    }

    suspend fun record(id: String): RecordDetail? {
        val record = dao.get(id) ?: return null
        val latestSuccess = dao.latestSuccessfulAnalysis(id)
        val latestAttempt = dao.latestAnalysisAttempt(id)
        return record.toDetail(latestSuccess, latestAttempt, json)
    }

    fun saveApiKey(key: String) {
        if (key.isBlank()) { aiSettingsState.value = aiSettingsState.value.copy(message = "请输入 API Key"); return }
        viewModelScope.launch {
            val provider = settings.value.aiProvider
            aiSettingsState.value = aiSettingsState.value.copy(busy = true, message = null)
            runCatching { credentialStore.save(provider, key) }
                .onSuccess {
                    aiSettingsState.value = AiSettingsUiState(true, key.takeLast(4), message = "API Key 已加密保存")
                    generationStates.filterValues { it is AnalysisGenerationState.NotConfigured }.keys.toList()
                        .forEach { generationStates[it] = AnalysisGenerationState.Idle }
                }
                .onFailure { aiSettingsState.value = AiSettingsUiState(message = "保存失败") }
        }
    }

    fun deleteApiKey() = viewModelScope.launch {
        credentialStore.delete(settings.value.aiProvider)
        aiSettingsState.value = AiSettingsUiState(message = "API Key 已删除")
    }

    fun testConnection() = viewModelScope.launch {
        aiSettingsState.value = aiSettingsState.value.copy(busy = true, message = null)
        runCatching { analysisRepository.testConnection() }
            .onSuccess { result -> aiSettingsState.value = aiSettingsState.value.copy(busy = false, message = if (result.modelAvailable) "连接成功，目标模型可用" else "Key 有效，但目标模型当前不可用") }
            .onFailure { error -> aiSettingsState.value = aiSettingsState.value.copy(busy = false, message = analysisErrorText(error.toAnalysisCode())) }
    }

    fun setAiProvider(provider: ProviderId) = viewModelScope.launch {
        settingsRepository.setAiProvider(provider)
        refreshCredentialState(provider)
    }

    private suspend fun refreshCredentialState(provider: ProviderId = settings.value.aiProvider) {
        val key = credentialStore.load(provider)
        aiSettingsState.value = AiSettingsUiState(key != null, key?.takeLast(4))
    }

    fun delete(record: DivinationRecord) { viewModelScope.launch { dao.delete(record) } }
    fun setExperimentalEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setExperimentalProfessionalEnabled(enabled) } }
    fun acceptExperimentalProfessional() = viewModelScope.launch { settingsRepository.acceptExperimentalProfessional(AppSettings.CURRENT_EXPERIMENTAL_CONSENT_VERSION) }
    fun setZoneId(zoneId: String) { viewModelScope.launch { settingsRepository.setZoneId(zoneId) } }
}

data class RecordDetail(
    val record: DivinationRecord,
    val paipan: PaipanResult,
    val analysis: AnalysisPayload?,
    val latestAttempt: AnalysisRevisionEntity?,
    val professional: ExperimentalProfessionalSnapshot?
)

private fun PaipanResult.toBasicRecord(id: String, question: String, castAt: Instant, zoneId: ZoneId, professional: ProfessionalGenerationResult) = DivinationRecord(
    id, question, System.currentTimeMillis(), linesBottomUp.joinToString(",") { it.value.toString() }, original.displayName, changed.displayName,
    "", "", "", "", "", "", "", "", "", "", "", "none", 0, "NOT_CONFIGURED", schemaVersion, engineVersion,
    castAt.toEpochMilli(), zoneId.id, zoneId.rules.getOffset(castAt).totalSeconds, professional.generationStatus.name,
    professional.ruleset?.id ?: "none", professional.ruleset?.version ?: "0", professional.verificationStatus?.name ?: "NOT_AVAILABLE",
    professional.snapshot?.payloadJson, professional.errorCode?.name
)

private fun DivinationRecord.toAiInput(): AiAnalysisInput {
    val paipan = LiuyaoEngine.calculate(parseLineValues(lineValues))
    val professional = professionalSnapshotJson
        ?.let { runCatching { ProfessionalSnapshotCodec.decode(ProfessionalChartSnapshot(it)) }.getOrNull() }
        ?.toAiFacts()
    return AiAnalysisInput(
        question, paipan.original.displayName, paipan.changed.displayName, paipan.movingLines,
        paipan.linesBottomUp.map { it.value }, engineVersion, schemaVersion, paipan.rulesetId, professional,
    )
}

private fun ExperimentalProfessionalSnapshot.toAiFacts(): ProfessionalAiFactsV1? {
    val capabilities = validatedCapabilities()
    if (capabilities.isEmpty()) return null
    val staticCore = ProfessionalCapability.STATIC_CORE in capabilities
    val calendarContext = ProfessionalCapability.CALENDAR_CONTEXT in capabilities
    val changedRelations = ProfessionalCapability.CHANGED_LINE_RELATION in capabilities
    return ProfessionalAiFactsV1(
        ruleset = ProfessionalRulesetRef(rulesetId, rulesetVersion, verificationStatus),
        capabilities = capabilities.map { it.name }.sorted(),
        calendar = if (calendarContext) CalendarAiFacts(
            "local-midnight+precise-jie-utc8-v1", monthGanZhi.last().toString(),
            dayGanZhi.first().toString(), dayGanZhi.last().toString(), xunkong,
        ) else null,
        palace = if (staticCore) PalaceAiFacts(originalPalace, palaceElement(originalPalace), originalClass, shiPosition, yingPosition) else null,
        lines = if (staticCore) originalLines.map { line ->
            ProfessionalLineAiFact(
                line.position, line.ganZhi.first().toString(), line.ganZhi.last().toString(),
                line.element, line.relation, null, line.shiYing, line.moving,
            )
        } else emptyList(),
        changedLines = if (changedRelations) changedLines.map { line ->
            ChangedLineAiFact(line.position, line.ganZhi.first().toString(), line.ganZhi.last().toString(), line.element, line.relation)
        } else emptyList(),
    )
}

private fun palaceElement(palace: String): String = when (palace) {
    "乾", "兑" -> "METAL"; "离" -> "FIRE"; "震", "巽" -> "WOOD"; "坎" -> "WATER"; else -> "EARTH"
}

private fun AnalysisResult.toEntity(recordId: String, input: AiAnalysisInput, json: Json, createdAt: Long) = AnalysisRevisionEntity(
    UUID.randomUUID().toString(), recordId, createdAt, "SUCCESS", providerId, modelId, promptVersion, schemaVersion, requestId,
    json.encodeToString(payload), null,
    input.professionalFacts?.factsVersion,
    input.professionalFacts?.ruleset?.id,
    input.professionalFacts?.ruleset?.version,
    input.professionalFacts?.ruleset?.validationProfile,
    input.professionalFacts?.capabilities?.joinToString(","),
)

private fun failedRevision(recordId: String, code: AnalysisErrorCode, profile: AiProviderProfile, createdAt: Long) = AnalysisRevisionEntity(
    UUID.randomUUID().toString(), recordId, createdAt, "FAILED", profile.providerId, profile.model,
    SiliconFlowProfile.PROMPT_VERSION, SiliconFlowProfile.SCHEMA_VERSION, null, null, code.name
)

private fun DivinationRecord.toDetail(success: AnalysisRevisionEntity?, attempt: AnalysisRevisionEntity?, json: Json): RecordDetail {
    val values = parseLineValues(lineValues)
    val payload = when {
        success?.payloadJson != null -> runCatching { json.decodeFromString<AnalysisPayload>(success.payloadJson) }.getOrNull()
        success?.providerId == "legacy_v1" -> AnalysisPayload(
            analysisSummary, analysisTrend,
            listOf(analysisFactor1, analysisFactor2, analysisFactor3, analysisFactor4, analysisFactor5, analysisFactor6).filter { it.isNotBlank() }.map { KeyFactor(it, "", emptyList()) },
            analysisText, analysisSuggestion, analysisConfidenceNote, analysisConfidenceNote, emptyList()
        )
        else -> null
    }
    val professional = professionalSnapshotJson?.let { runCatching { ProfessionalSnapshotCodec.decode(ProfessionalChartSnapshot(it)) }.getOrNull() }
    return RecordDetail(this, LiuyaoEngine.calculate(values), payload, attempt, professional)
}

fun parseLineValues(value: String): List<LineValue> {
    val parts = value.split(",")
    require(parts.size == 6) { "历史卦例必须恰好包含六个爻值" }
    return parts.map { part -> require(part.length == 1 && part[0] in '6'..'9'); LineValue.from(part.toInt()) }
}

private fun Throwable.toAnalysisCode(): AnalysisErrorCode = when (this) {
    is AnalysisException -> code
    is SocketTimeoutException -> AnalysisErrorCode.TIMEOUT
    is IOException -> AnalysisErrorCode.NETWORK_ERROR
    else -> AnalysisErrorCode.INVALID_RESPONSE
}

internal fun analysisErrorText(code: AnalysisErrorCode) = when (code) {
    AnalysisErrorCode.INVALID_CREDENTIAL -> "API Key 无效或已失效"
    AnalysisErrorCode.INSUFFICIENT_BALANCE_OR_QUOTA -> "账户余额或额度不足，或请求被拒绝"
    AnalysisErrorCode.RATE_LIMITED -> "调用频率受限，请稍后手动重试"
    AnalysisErrorCode.MODEL_UNAVAILABLE -> "目标模型当前不可用"
    AnalysisErrorCode.PROVIDER_UNAVAILABLE -> "AI 服务暂不可用"
    AnalysisErrorCode.TIMEOUT -> "请求超时，未自动重试以避免重复费用"
    AnalysisErrorCode.NETWORK_ERROR -> "网络连接失败"
    AnalysisErrorCode.INVALID_MODEL_OUTPUT -> "AI 输出未通过本地校验"
    AnalysisErrorCode.INVALID_RESPONSE -> "服务响应无效"
}

class WenYaoViewModelFactory(
    private val dao: DivinationDao,
    private val analysisRepository: AnalysisRepository,
    private val credentialStore: AiCredentialStore,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WenYaoViewModel(dao, analysisRepository, credentialStore, settingsRepository) as T
}
