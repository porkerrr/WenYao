package com.peterpork.liuyao.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.peterpork.liuyao.WenYaoApplication
import com.peterpork.liuyao.data.*
import com.peterpork.liuyao.domain.*
import com.peterpork.liuyao.ui.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Real app/navigation/screens, isolated in-memory records and a provider that cannot send requests. */
class CastingVerificationActivity : ComponentActivity() {
    internal lateinit var session: CastingVerificationSession
    internal lateinit var business: WenYaoViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = ViewModelProvider(this)[CastingVerificationSession::class.java]
        if (session.codes.isEmpty()) {
            session.codes = (intent.getStringExtra("fixedResults") ?: "222,223,232,233,322,323").split(",").also {
                require(it.isNotEmpty() && it.all { code -> PreviewResult.fromCode(code) != null })
            }
        }
        val settings = (application as WenYaoApplication).settingsRepository
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WenYaoViewModel(session.dao, session.analysis, session.credentials, settings, session.caster) as T
        }
        business = ViewModelProvider(this, factory)[WenYaoViewModel::class.java]
        val entrance = HomeEntranceSession(restored = true, normalLaunch = false)
        setContent {
            WenYaoTheme {
                WenYaoApp(session.dao, session.analysis, session.credentials, settings, entrance,
                    presentation = session.renderer,
                    viewModelFactory = factory)
            }
        }
    }
}

class CastingVerificationSession : ViewModel() {
    internal var codes = emptyList<String>()
    internal val renderer = DebugCastingPresentation()
    internal val dao = CastingMemoryDao()
    internal var castCalls = 0
    internal var analysisCalls = 0
    internal val caster = CoinCaster {
        val code = codes[castCalls % codes.size]
        CoinCastResult("fixed-${castCalls++}", code.map { if (it == '3') CoinSide.HEADS else CoinSide.TAILS })
    }
    internal val analysis = object : AnalysisRepository {
        override suspend fun isConfigured() = false
        override suspend fun analyze(input: AiAnalysisInput): AnalysisResult { analysisCalls++; error("No network in casting verification") }
        override suspend fun testConnection(): ConnectionTestResult = error("No network in casting verification")
    }
    internal val credentials = object : AiCredentialStore {
        override suspend fun load(providerId: ProviderId): String? = null
        override suspend fun save(providerId: ProviderId, credential: String) = error("No credentials in verification")
        override suspend fun delete(providerId: ProviderId) = Unit
    }
}

internal class CastingMemoryDao : DivinationDao {
    private val records = MutableStateFlow<List<DivinationRecord>>(emptyList())
    var saveCalls = 0
        private set
    override fun observeAll() = records
    override suspend fun get(id: String) = records.value.firstOrNull { it.id == id }
    override suspend fun save(record: DivinationRecord) { saveCalls++; records.value = records.value.filterNot { it.id == record.id } + record }
    override suspend fun delete(record: DivinationRecord) { records.value = records.value.filterNot { it.id == record.id } }
    override suspend fun count() = records.value.size
    override suspend fun saveAnalysisRevision(revision: AnalysisRevisionEntity) = error("No AI revisions in verification")
    override suspend fun analysisRevisions(divinationId: String) = emptyList<AnalysisRevisionEntity>()
    override suspend fun latestSuccessfulAnalysis(divinationId: String): AnalysisRevisionEntity? = null
    override suspend fun latestAnalysisAttempt(divinationId: String): AnalysisRevisionEntity? = null
}
