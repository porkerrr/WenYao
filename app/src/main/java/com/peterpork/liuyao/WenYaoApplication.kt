package com.peterpork.liuyao

import android.app.Application
import com.peterpork.liuyao.data.KeystoreAiCredentialStore
import com.peterpork.liuyao.data.ProviderRoutingAnalysisRepository
import com.peterpork.liuyao.data.HistoryDatabase
import com.peterpork.liuyao.data.SettingsRepository

open class WenYaoApplication : Application() {
    private val renderer by lazy { com.peterpork.liuyao.preview.CastingScenePresentation() }
    internal open fun castPresentation(intent: android.content.Intent): com.peterpork.liuyao.ui.CastPresentation = renderer
    val database by lazy { HistoryDatabase.create(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val credentialStore by lazy { KeystoreAiCredentialStore(this) }
    val analysisRepository by lazy { ProviderRoutingAnalysisRepository(credentialStore, settingsRepository) }
}
