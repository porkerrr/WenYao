package com.peterpork.liuyao.preview

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.peterpork.liuyao.data.*
import com.peterpork.liuyao.ui.*
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.peterpork.liuyao.R

/** Debug-only replay of the real app; in-memory history and no credential/network access. */
class SplashPreviewActivity : ComponentActivity() {
    internal var entrance by mutableStateOf(HomeEntranceSession(false, true))
    private var failAsset by mutableStateOf(false)
    private lateinit var fixture: SplashPreviewFixture
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        fixture = ViewModelProvider(this)[SplashPreviewFixture::class.java]
        if (fixture.database == null) fixture.database = Room.inMemoryDatabaseBuilder(
            applicationContext, HistoryDatabase::class.java).build()
        reset(intent, savedInstanceState != null)
        setContent {
            WenYaoTheme {
                CompositionLocalProvider(LocalSplashInk provides if (failAsset)
                    LottieCompositionSpec.JsonString("invalid") else LottieCompositionSpec.RawRes(R.raw.wenyao_ink)) {
                WenYaoApp(fixture.database!!.dao(), fixture.analysis, fixture.credentials,
                    remember { SettingsRepository(applicationContext) }, entrance)
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); reset(intent, false) }
    private fun reset(intent: Intent, restored: Boolean) {
        failAsset = intent.getBooleanExtra("fail_asset", false)
        entrance.finish()
        entrance = HomeEntranceSession(restored, true).also {
            if (intent.hasExtra("splash_ms")) it.previewMillis = intent.getIntExtra("splash_ms", 0).coerceIn(0, HomeEntranceMotion.TotalMillis).toFloat()
            if (it.previewMillis == HomeEntranceMotion.TotalMillis.toFloat()) it.finish()
        }
    }
    override fun onPause() { entrance.finish(); super.onPause() }
}

class SplashPreviewFixture : ViewModel() {
    internal var database: HistoryDatabase? = null
    internal val analysis = object : AnalysisRepository {
        override suspend fun isConfigured() = false
        override suspend fun analyze(input: AiAnalysisInput): AnalysisResult = error("Preview has no network")
        override suspend fun testConnection(): ConnectionTestResult = error("Preview has no network")
    }
    internal val credentials = object : AiCredentialStore {
        override suspend fun load(providerId: ProviderId): String? = null
        override suspend fun save(providerId: ProviderId, credential: String) = error("Preview cannot save credentials")
        override suspend fun delete(providerId: ProviderId) = Unit
    }
    override fun onCleared() { database?.close() }
}
