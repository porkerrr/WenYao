package com.peterpork.liuyao.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepSeekAnalysisRepositoryTest {
    private lateinit var server: MockWebServer
    private val credentials = DeepSeekMemoryCredentials("sk-deepseek")
    private val input = AiAnalysisInput("测试问题", "乾为天", "乾为天", emptyList(), List(6) { 7 }, "0.1", 1, "basic")

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }

    @Test fun `request uses official DeepSeek settings with thinking disabled`() = runTest {
        server.enqueue(MockResponse().setBody(response(validPayload())))
        val result = repository().analyze(input)
        val request = server.takeRequest()
        val body = Json.parseToJsonElement(request.body.readUtf8()).toString()
        assertEquals("/chat/completions", request.path)
        assertEquals("Bearer sk-deepseek", request.getHeader("Authorization"))
        assertTrue(body.contains("\"model\":\"deepseek-flash\""))
        assertTrue(body.contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertFalse(body.contains("reasoning_effort"))
        assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"))
        assertEquals(DeepSeekProfile.PROVIDER, result.providerId)
        assertEquals(DeepSeekProfile.MODEL, result.modelId)
    }

    @Test fun `connection test checks official model list without generation`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"deepseek-flash"}]}"""))
        assertTrue(repository().testConnection().modelAvailable)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/models", request.path)
    }

    @Test fun `authentication error is mapped without retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val error = runCatching { repository().analyze(input) }.exceptionOrNull() as AnalysisException
        assertEquals(AnalysisErrorCode.INVALID_CREDENTIAL, error.code)
        assertEquals(1, server.requestCount)
    }

    private fun repository() = DeepSeekAnalysisRepository(credentials, baseUrl = server.url("/"))
    private fun response(content: String) = """{"choices":[{"message":{"content":${quote(content)},"reasoning_content":"must-not-be-used"}}]}"""
    private fun quote(value: String) = Json.encodeToString(kotlinx.serialization.serializer<String>(), value)
    private fun validPayload() = """{"summary":"摘要","trend":"平稳","key_factors":[{"title":"本卦","explanation":"乾","basis_refs":["PRIMARY_HEXAGRAM"]}],"analysis":"分析","suggestion":"谨慎参考","limitations_note":"仅供娱乐","safety_note":"不用于现实决定","basis_refs":["LINE_VALUES"]}"""
}

private class DeepSeekMemoryCredentials(initial: String? = null) : AiCredentialStore {
    private val values = mutableMapOf<ProviderId, String?>().apply { put(ProviderId.DEEPSEEK, initial) }
    override suspend fun save(providerId: ProviderId, credential: String) { values[providerId] = credential }
    override suspend fun load(providerId: ProviderId) = values[providerId]
    override suspend fun delete(providerId: ProviderId) { values.remove(providerId) }
}
