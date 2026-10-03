package com.peterpork.liuyao.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRoutingAnalysisRepositoryTest {
    @Test fun `routes configured state and connection tests to the selected provider`() = runTest {
        val settings = RoutingSettings(ProviderId.SILICON_FLOW)
        val silicon = RecordingRepository(configured = true, connection = true)
        val deepSeek = RecordingRepository(configured = false, connection = false)
        val repository = ProviderRoutingAnalysisRepository(EmptyCredentials(), settings, silicon, deepSeek)

        assertTrue(repository.isConfigured())
        assertTrue(repository.testConnection().modelAvailable)
        assertEquals(1, silicon.connectionCalls)
        assertEquals(0, deepSeek.connectionCalls)

        settings.setAiProvider(ProviderId.DEEPSEEK)
        assertFalse(repository.isConfigured())
        assertFalse(repository.testConnection().modelAvailable)
        assertEquals(1, deepSeek.connectionCalls)
    }
}

private class RoutingSettings(provider: ProviderId) : SettingsStore {
    private val state = MutableStateFlow(AppSettings(aiProvider = provider))
    override val settings: Flow<AppSettings> = state
    override suspend fun setExperimentalProfessionalEnabled(enabled: Boolean) = Unit
    override suspend fun acceptExperimentalProfessional(consentVersion: Int) = Unit
    override suspend fun setZoneId(zoneId: String) = Unit
    override suspend fun setAiProvider(providerId: ProviderId) { state.value = state.value.copy(aiProvider = providerId) }
}

private class RecordingRepository(
    private val configured: Boolean,
    private val connection: Boolean,
) : AnalysisRepository {
    var connectionCalls = 0
    override suspend fun isConfigured() = configured
    override suspend fun analyze(input: AiAnalysisInput): AnalysisResult = error("not used")
    override suspend fun testConnection(): ConnectionTestResult {
        connectionCalls += 1
        return ConnectionTestResult(connection)
    }
}

private class EmptyCredentials : AiCredentialStore {
    override suspend fun save(providerId: ProviderId, credential: String) = Unit
    override suspend fun load(providerId: ProviderId): String? = null
    override suspend fun delete(providerId: ProviderId) = Unit
}
