package com.peterpork.liuyao.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SiliconFlowAnalysisRepositoryTest {
    private lateinit var server: MockWebServer
    private val credentials = MemoryCredentials("sk-secret")
    private val input = AiAnalysisInput("测试问题", "乾为天", "乾为天", emptyList(), List(6) { 7 }, "0.1", 1, "basic")

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }

    @Test fun `valid response uses only message content and sends allowlisted fields`() = runTest {
        server.enqueue(MockResponse().setBody(response(validPayload(), "private reasoning")))
        val result = repository().analyze(input)
        val request = server.takeRequest()
        assertEquals("Bearer sk-secret", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("primary_hexagram"))
        assertFalse(body.contains("\"professional_facts\":"))
        assertTrue(body.contains("\"stream\":false"))
        assertEquals("摘要", result.payload.summary)
        assertFalse(result.payload.analysis.contains("private reasoning"))
    }

    @Test fun `unknown basis reference is rejected without retry`() = runTest {
        server.enqueue(MockResponse().setBody(response(validPayload().replace("LINE_VALUES", "UNKNOWN"))))
        val error = runCatching { repository().analyze(input) }.exceptionOrNull() as AnalysisException
        assertEquals(AnalysisErrorCode.INVALID_MODEL_OUTPUT, error.code)
        assertEquals(1, server.requestCount)
    }

    @Test fun `cross validated professional facts are serialized and references are accepted`() = runTest {
        val professional = ProfessionalAiFactsV1(
            ruleset = ProfessionalRulesetRef("wenyao-mainstream", "1", "CROSS_VALIDATED"),
            capabilities = listOf("STATIC_CORE"),
            palace = PalaceAiFacts("乾", "METAL", "PURE", 6, 3),
            lines = listOf(ProfessionalLineAiFact(1, "甲", "子", "WATER", "CHILD", null, null, false)),
        )
        val professionalInput = input.copy(professionalFacts = professional)
        server.enqueue(MockResponse().setBody(response(validPayload().replace("LINE_VALUES", "pro.line.1.liuqin"))))

        repository().analyze(professionalInput)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("professional_facts"))
        assertTrue(body.contains("pro.line.1.liuqin"))
        assertFalse(body.contains("castAt"))
        assertFalse(body.contains("userZoneId"))
    }

    @Test fun `connection test checks model list without posting generation`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"${SiliconFlowProfile.MODEL}"}]}"""))
        assertTrue(repository().testConnection().modelAvailable)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test fun `authentication error is mapped and not retried`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("invalid"))
        val error = runCatching { repository().analyze(input) }.exceptionOrNull() as AnalysisException
        assertEquals(AnalysisErrorCode.INVALID_CREDENTIAL, error.code)
        assertEquals(1, server.requestCount)
    }

    @Test fun `oversized response is rejected before parsing`() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(256 * 1024 + 1)))
        val error = runCatching { repository().analyze(input) }.exceptionOrNull() as AnalysisException
        assertEquals(AnalysisErrorCode.INVALID_RESPONSE, error.code)
    }

    @Test fun `high risk question replaces model action advice`() = runTest {
        val riskyInput = input.copy(question = "这个卦是否说明我可以停药")
        server.enqueue(MockResponse().setBody(response(validPayload().replace("谨慎参考", "可以停药"))))
        val result = repository().analyze(riskyInput)
        assertTrue(result.payload.suggestion.contains("不能作为医疗"))
    }

    private fun repository() = SiliconFlowAnalysisRepository(credentials, baseUrl = server.url("/v1/"))
    private fun response(content: String, reasoning: String = "") =
        """{"choices":[{"message":{"content":${quote(content)},"reasoning_content":${quote(reasoning)}}}]}"""
    private fun quote(value: String) = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer<String>(), value)
    private fun validPayload() = """{"summary":"摘要","trend":"平稳","key_factors":[{"title":"本卦","explanation":"乾","basis_refs":["PRIMARY_HEXAGRAM"]}],"analysis":"分析","suggestion":"谨慎参考","limitations_note":"仅供娱乐","safety_note":"不用于现实决定","basis_refs":["LINE_VALUES"]}"""
}

private class MemoryCredentials(initial: String? = null) : AiCredentialStore {
    private var value = initial
    override suspend fun save(providerId: ProviderId, credential: String) { value = credential }
    override suspend fun load(providerId: ProviderId) = value
    override suspend fun delete(providerId: ProviderId) { value = null }
}
