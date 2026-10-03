package com.peterpork.liuyao.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "wenyao-settings")

data class AppSettings(
    val experimentalProfessionalEnabled: Boolean = false,
    val zoneId: String = DEFAULT_ZONE_ID,
    val experimentalConsentVersion: Int = 0,
    val aiProvider: ProviderId = ProviderId.SILICON_FLOW,
) {
    companion object {
        const val DEFAULT_ZONE_ID = "Asia/Shanghai"
        const val CURRENT_EXPERIMENTAL_CONSENT_VERSION = 1
    }
}

fun AppSettings.requiresExperimentalConsent(): Boolean =
    experimentalConsentVersion < AppSettings.CURRENT_EXPERIMENTAL_CONSENT_VERSION

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun setExperimentalProfessionalEnabled(enabled: Boolean)
    suspend fun acceptExperimentalProfessional(consentVersion: Int)
    suspend fun setZoneId(zoneId: String)
    suspend fun setAiProvider(providerId: ProviderId)
}

class SettingsRepository(private val context: Context) : SettingsStore {
    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .map { preferences ->
            val zone = preferences[ZONE_ID]?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
                ?: AppSettings.DEFAULT_ZONE_ID
            AppSettings(
                preferences[EXPERIMENTAL_ENABLED] ?: false,
                zone,
                preferences[EXPERIMENTAL_CONSENT_VERSION] ?: 0,
                preferences[AI_PROVIDER]?.let { value ->
                    runCatching { ProviderId.valueOf(value) }.getOrNull()
                } ?: ProviderId.SILICON_FLOW,
            )
        }
        .catch { emit(AppSettings()) }

    override suspend fun setExperimentalProfessionalEnabled(enabled: Boolean) {
        require(!enabled) { "启用实验功能必须通过风险确认接口" }
        context.settingsDataStore.edit { it[EXPERIMENTAL_ENABLED] = enabled }
    }

    override suspend fun acceptExperimentalProfessional(consentVersion: Int) {
        require(consentVersion == AppSettings.CURRENT_EXPERIMENTAL_CONSENT_VERSION)
        context.settingsDataStore.edit {
            it[EXPERIMENTAL_ENABLED] = true
            it[EXPERIMENTAL_CONSENT_VERSION] = consentVersion
        }
    }

    override suspend fun setZoneId(zoneId: String) {
        ZoneId.of(zoneId)
        context.settingsDataStore.edit { it[ZONE_ID] = zoneId }
    }

    override suspend fun setAiProvider(providerId: ProviderId) {
        context.settingsDataStore.edit { it[AI_PROVIDER] = providerId.name }
    }

    companion object {
        private val EXPERIMENTAL_ENABLED = booleanPreferencesKey("experimental_professional_enabled")
        private val EXPERIMENTAL_CONSENT_VERSION = intPreferencesKey("experimental_professional_consent_version")
        private val ZONE_ID = stringPreferencesKey("zone_id")
        private val AI_PROVIDER = stringPreferencesKey("ai_provider")
    }
}
