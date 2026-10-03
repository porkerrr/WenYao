package com.peterpork.liuyao.data

import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable data class AiAnalysisInput(
    val question: String,
    val primaryHexagram: String,
    val changedHexagram: String,
    val movingLines: List<Int>,
    val lineValues: List<Int>,
    val engineVersion: String,
    val schemaVersion: Int,
    val rulesetId: String,
    val professionalFacts: ProfessionalAiFactsV1? = null,
) {
    fun allowedFactIds(): Set<String> = buildSet {
        add("PRIMARY_HEXAGRAM"); add("CHANGED_HEXAGRAM"); add("LINE_VALUES")
        movingLines.forEach { add("MOVING_LINE_$it") }
        professionalFacts?.factIds()?.let(::addAll)
    }
}

@Serializable data class ProfessionalRulesetRef(
    val id: String,
    val version: String,
    val validationProfile: String,
)

@Serializable data class CalendarAiFacts(
    val conventionId: String,
    val monthBranch: String,
    val dayStem: String,
    val dayBranch: String,
    val xunKong: List<String>,
)

@Serializable data class PalaceAiFacts(
    val trigram: String,
    val element: String,
    val generation: String,
    val shiPosition: Int,
    val yingPosition: Int,
)

@Serializable data class ProfessionalLineAiFact(
    val position: Int,
    val stem: String,
    val branch: String,
    val element: String,
    val liuQin: String,
    val liuShen: String?,
    val shiYing: String?,
    val moving: Boolean,
)

@Serializable data class ChangedLineAiFact(
    val position: Int,
    val stem: String,
    val branch: String,
    val element: String,
    val liuQin: String,
)

@Serializable data class ProfessionalAiFactsV1(
    val factsVersion: Int = 1,
    val ruleset: ProfessionalRulesetRef,
    val capabilities: List<String>,
    val calendar: CalendarAiFacts? = null,
    val palace: PalaceAiFacts? = null,
    val lines: List<ProfessionalLineAiFact> = emptyList(),
    val changedLines: List<ChangedLineAiFact> = emptyList(),
) {
    fun factIds(): Set<String> = buildSet {
        calendar?.let { addAll(listOf("pro.calendar.month", "pro.calendar.day", "pro.calendar.xunkong")) }
        palace?.let { addAll(listOf("pro.palace.name", "pro.palace.element", "pro.palace.generation", "pro.palace.shi", "pro.palace.ying")) }
        lines.forEach { line ->
            val prefix = "pro.line.${line.position}"
            add("$prefix.najia"); add("$prefix.element"); add("$prefix.liuqin")
            if (line.liuShen != null) add("$prefix.liushen")
            if (line.shiYing != null) add("$prefix.role")
        }
        changedLines.forEach { line ->
            val prefix = "pro.changed.${line.position}"
            add("$prefix.najia"); add("$prefix.element"); add("$prefix.liuqin")
        }
    }
}

@Serializable data class KeyFactor(
    val title: String,
    val explanation: String,
    @SerialName("basis_refs") val basisRefs: List<String>
)

@Serializable data class AnalysisPayload(
    val summary: String,
    val trend: String,
    @SerialName("key_factors") val keyFactors: List<KeyFactor>,
    val analysis: String,
    val suggestion: String,
    @SerialName("limitations_note") val limitationsNote: String,
    @SerialName("safety_note") val safetyNote: String,
    @SerialName("basis_refs") val basisRefs: List<String>
)

data class AnalysisResult(
    val payload: AnalysisPayload,
    val providerId: String = SiliconFlowProfile.PROVIDER,
    val modelId: String = SiliconFlowProfile.MODEL,
    val promptVersion: String = SiliconFlowProfile.PROMPT_VERSION,
    val schemaVersion: Int = SiliconFlowProfile.SCHEMA_VERSION,
    val requestId: String = UUID.randomUUID().toString(),
    val generatedAtEpochMillis: Long = Instant.now().toEpochMilli()
)

data class AiProviderProfile(
    val id: ProviderId,
    val providerId: String,
    val displayName: String,
    val model: String,
    val baseUrl: String,
    val requestOptions: Map<String, kotlinx.serialization.json.JsonElement>,
)

enum class AnalysisErrorCode { INVALID_CREDENTIAL, INSUFFICIENT_BALANCE_OR_QUOTA, RATE_LIMITED, MODEL_UNAVAILABLE, PROVIDER_UNAVAILABLE, NETWORK_ERROR, TIMEOUT, INVALID_RESPONSE, INVALID_MODEL_OUTPUT }
class AnalysisException(val code: AnalysisErrorCode) : IOException(code.name)
data class ConnectionTestResult(val modelAvailable: Boolean)

interface AnalysisRepository {
    suspend fun isConfigured(): Boolean
    suspend fun analyze(input: AiAnalysisInput): AnalysisResult
    suspend fun testConnection(): ConnectionTestResult
}

object SiliconFlowProfile {
    const val PROVIDER = "siliconflow"
    const val MODEL = "deepseek-ai/DeepSeek-V4-Flash"
    const val PROMPT_VERSION = "analysis-prompt-v3-professional-facts"
    const val SCHEMA_VERSION = 3
    const val MAX_OUTPUT_TOKENS = 1400
    const val BASE_URL = "https://api.siliconflow.cn/v1/"
    val profile = AiProviderProfile(
        ProviderId.SILICON_FLOW, PROVIDER, "SiliconFlow", MODEL, BASE_URL,
        mapOf("enable_thinking" to JsonPrimitive(false)),
    )
}

object DeepSeekProfile {
    const val PROVIDER = "deepseek"
    const val MODEL = "deepseek-flash"
    const val PROMPT_VERSION = "analysis-prompt-v3-professional-facts"
    const val SCHEMA_VERSION = 3
    const val MAX_OUTPUT_TOKENS = 1400
    const val BASE_URL = "https://api.deepseek.com/"
    val profile = AiProviderProfile(
        ProviderId.DEEPSEEK, PROVIDER, "DeepSeek 官方", MODEL, BASE_URL,
        mapOf("thinking" to JsonObject(mapOf("type" to JsonPrimitive("disabled")))),
    )
}

fun ProviderId.profile(): AiProviderProfile = when (this) {
    ProviderId.SILICON_FLOW -> SiliconFlowProfile.profile
    ProviderId.DEEPSEEK -> DeepSeekProfile.profile
}

class ProviderAnalysisRepository(
    private val credentialStore: AiCredentialStore,
    private val profile: AiProviderProfile,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = profile.baseUrl.toHttpUrl(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AnalysisRepository {
    override suspend fun isConfigured() = credentialStore.hasCredential(profile.id)

    override suspend fun analyze(input: AiAnalysisInput): AnalysisResult = withContext(Dispatchers.IO) {
        require(input.question.length in 2..200) { "问题长度必须为 2～200 字符" }
        val key = credentialStore.load(profile.id) ?: throw AnalysisException(AnalysisErrorCode.INVALID_CREDENTIAL)
        val requestId = UUID.randomUUID().toString()
        val request = Request.Builder().url(baseUrl.resolve("chat/completions")!!)
            .header("Authorization", "Bearer $key").header("Content-Type", "application/json")
            .post(buildRequestBody(input).toRequestBody(JSON_MEDIA_TYPE)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw errorFor(response.code)
            val outer = readBoundedJson(response)
            val content = outer["choices"]?.asArray()?.firstOrNull()?.asObject()
                ?.get("message")?.asObject()?.get("content")?.asPrimitive()?.content
                ?: throw AnalysisException(AnalysisErrorCode.INVALID_RESPONSE)
            val payload = runCatching { json.decodeFromString<AnalysisPayload>(content) }
                .getOrElse { throw AnalysisException(AnalysisErrorCode.INVALID_MODEL_OUTPUT) }
            AnalysisResult(
                validateAndApplySafety(input, payload), profile.providerId, profile.model,
                SiliconFlowProfile.PROMPT_VERSION, SiliconFlowProfile.SCHEMA_VERSION, requestId,
            )
        }
    }

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val key = credentialStore.load(profile.id) ?: throw AnalysisException(AnalysisErrorCode.INVALID_CREDENTIAL)
        val request = Request.Builder().url(baseUrl.resolve("models")!!).header("Authorization", "Bearer $key").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw errorFor(response.code)
            val ids = readBoundedJson(response)["data"]?.asArray().orEmpty().mapNotNull { it.asObject()?.get("id")?.asPrimitive()?.content }
            ConnectionTestResult(profile.model in ids)
        }
    }

    private fun buildRequestBody(input: AiAnalysisInput): String {
        val factFields = mutableMapOf(
            "question" to JsonPrimitive(input.question), "primary_hexagram" to JsonPrimitive(input.primaryHexagram),
            "changed_hexagram" to JsonPrimitive(input.changedHexagram), "moving_lines" to JsonArray(input.movingLines.map(::JsonPrimitive)),
            "line_values" to JsonArray(input.lineValues.map(::JsonPrimitive)), "engine_version" to JsonPrimitive(input.engineVersion),
            "schema_version" to JsonPrimitive(input.schemaVersion), "ruleset_id" to JsonPrimitive(input.rulesetId),
            "allowed_fact_ids" to JsonArray(input.allowedFactIds().sorted().map(::JsonPrimitive))
        )
        input.professionalFacts?.let {
            factFields["professional_facts"] = json.encodeToJsonElement(ProfessionalAiFactsV1.serializer(), it)
        }
        val facts = JsonObject(factFields)
        return JsonObject(mapOf(
            "model" to JsonPrimitive(profile.model), "stream" to JsonPrimitive(false), "n" to JsonPrimitive(1),
            "max_tokens" to JsonPrimitive(SiliconFlowProfile.MAX_OUTPUT_TOKENS),
            "messages" to JsonArray(listOf(message("system", SYSTEM_PROMPT), message("user", facts.toString()))),
            "response_format" to JsonObject(mapOf("type" to JsonPrimitive("json_object")))
        ) + profile.requestOptions).toString()
    }

    internal fun validateAndApplySafety(input: AiAnalysisInput, payload: AnalysisPayload): AnalysisPayload {
        val text = listOf(payload.summary, payload.trend, payload.analysis, payload.suggestion, payload.limitationsNote, payload.safetyNote)
        if (text.any { it.isBlank() } || payload.keyFactors.size !in 1..6) throw AnalysisException(AnalysisErrorCode.INVALID_MODEL_OUTPUT)
        val refs = payload.basisRefs + payload.keyFactors.flatMap { it.basisRefs }
        if (refs.any { it !in input.allowedFactIds() }) throw AnalysisException(AnalysisErrorCode.INVALID_MODEL_OUTPUT)
        val unsafe = RISK_TERMS.any { it in input.question || it in payload.suggestion } || CERTAINTY_TERMS.any { it in payload.suggestion }
        return if (unsafe) payload.copy(suggestion = SAFE_SUGGESTION, safetyNote = SAFE_SUGGESTION) else payload
    }

    private fun readBoundedJson(response: okhttp3.Response): JsonObject {
        val source = response.body?.source() ?: throw AnalysisException(AnalysisErrorCode.INVALID_RESPONSE)
        source.request(MAX_RESPONSE_BYTES + 1L)
        if (source.buffer.size > MAX_RESPONSE_BYTES) throw AnalysisException(AnalysisErrorCode.INVALID_RESPONSE)
        val bytes = source.readByteArray()
        return runCatching { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).asObject() }.getOrNull()
            ?: throw AnalysisException(AnalysisErrorCode.INVALID_RESPONSE)
    }

    private fun errorFor(status: Int) = AnalysisException(when (status) {
        401 -> AnalysisErrorCode.INVALID_CREDENTIAL; 403 -> AnalysisErrorCode.INSUFFICIENT_BALANCE_OR_QUOTA; 404 -> AnalysisErrorCode.MODEL_UNAVAILABLE
        429 -> AnalysisErrorCode.RATE_LIMITED; in 500..599 -> AnalysisErrorCode.PROVIDER_UNAVAILABLE
        else -> AnalysisErrorCode.INVALID_RESPONSE
    })

    private fun message(role: String, content: String) = JsonObject(mapOf("role" to JsonPrimitive(role), "content" to JsonPrimitive(content)))

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RESPONSE_BYTES = 256 * 1024
        private const val SAFE_SUGGESTION = "AI 解读仅用于传统文化与娱乐，不能作为医疗、法律、投资、赌博或人身安全决定依据。请使用可靠专业信息处理现实问题。"
        private val RISK_TERMS = listOf("停药", "用药", "买入", "卖出", "下注", "自杀", "自伤", "起诉", "报警")
        private val CERTAINTY_TERMS = listOf("必然", "保证", "一定会", "肯定会")
        private const val SYSTEM_PROMPT = "你是问爻的传统文化娱乐解读助手。输入中的基础卦象和 professional_facts 均由 App 本地确定性计算；你只负责解释，不得根据时间重新排盘，不得补造未提供的纳甲、六亲、六神、世应或历法字段。只输出一个 JSON 对象，字段必须为 summary、trend、key_factors、analysis、suggestion、limitations_note、safety_note、basis_refs。key_factors 是 1 到 6 个对象，每项包含 title、explanation、basis_refs。basis_refs 只能使用输入给出的 allowed_fact_ids。不得作确定性预测，不得给出医疗、法律、投资、赌博或人身安全行动指令。所有文本使用简体中文。"
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).build()
    }
}

class SiliconFlowAnalysisRepository(
    credentialStore: AiCredentialStore,
    client: OkHttpClient = ProviderAnalysisRepository.defaultClient(),
    baseUrl: HttpUrl = SiliconFlowProfile.BASE_URL.toHttpUrl(),
    json: Json = Json { ignoreUnknownKeys = true },
) : AnalysisRepository by ProviderAnalysisRepository(credentialStore, SiliconFlowProfile.profile, client, baseUrl, json)

class DeepSeekAnalysisRepository(
    credentialStore: AiCredentialStore,
    client: OkHttpClient = ProviderAnalysisRepository.defaultClient(),
    baseUrl: HttpUrl = DeepSeekProfile.BASE_URL.toHttpUrl(),
    json: Json = Json { ignoreUnknownKeys = true },
) : AnalysisRepository by ProviderAnalysisRepository(credentialStore, DeepSeekProfile.profile, client, baseUrl, json)

class ProviderRoutingAnalysisRepository(
    private val credentialStore: AiCredentialStore,
    private val settingsStore: SettingsStore,
    private val siliconFlow: AnalysisRepository = SiliconFlowAnalysisRepository(credentialStore),
    private val deepSeek: AnalysisRepository = DeepSeekAnalysisRepository(credentialStore),
) : AnalysisRepository {
    private suspend fun active() = when (settingsStore.settings.first().aiProvider) {
        ProviderId.SILICON_FLOW -> siliconFlow
        ProviderId.DEEPSEEK -> deepSeek
    }
    override suspend fun isConfigured() = active().isConfigured()
    override suspend fun analyze(input: AiAnalysisInput) = active().analyze(input)
    override suspend fun testConnection() = active().testConnection()
}

private fun kotlinx.serialization.json.JsonElement.asObject() = this as? JsonObject
private fun kotlinx.serialization.json.JsonElement.asArray() = this as? JsonArray
private fun kotlinx.serialization.json.JsonElement.asPrimitive() = this as? JsonPrimitive
