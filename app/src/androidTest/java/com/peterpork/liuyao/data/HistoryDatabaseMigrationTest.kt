package com.peterpork.liuyao.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class HistoryDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        databaseClass = HistoryDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2PreservesRecordAndAddsUnavailableProfessionalChart() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO divination_records (
                    id, question, createdAtEpochMillis, lineValues, originalName, changedName,
                    analysisSummary, analysisTrend, analysisFactor1, analysisFactor2,
                    analysisFactor3, analysisFactor4, analysisFactor5, analysisFactor6,
                    analysisText, analysisSuggestion, analysisConfidenceNote, analysisProvider,
                    analysisVersion, analysisStatus, schemaVersion, engineVersion
                ) VALUES (
                    'legacy-1', '旧问题', 1, '7,7,7,7,7,7', '乾为天', '乾为天',
                    '', '', '', '', '', '', '', '', '', '', '', 'demo', 1, 'DEMO', 1, '0.1.0'
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, HistoryDatabase.MIGRATION_1_2).use { db ->
            db.query(
                "SELECT question, professionalRulesetId, professionalRulesetVersion, " +
                    "professionalVerificationStatus, professionalSnapshotJson FROM divination_records WHERE id = 'legacy-1'"
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("旧问题", cursor.getString(0))
                assertEquals("none", cursor.getString(1))
                assertEquals("0", cursor.getString(2))
                assertEquals("NOT_AVAILABLE", cursor.getString(3))
                assertNull(cursor.getString(4))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3PreservesLegacyStateWithoutInventingTimeOrSnapshot() {
        helper.createDatabase(TEST_DB_V3, 2).apply {
            execSQL(
                """
                INSERT INTO divination_records (
                    id, question, createdAtEpochMillis, lineValues, originalName, changedName,
                    analysisSummary, analysisTrend, analysisFactor1, analysisFactor2,
                    analysisFactor3, analysisFactor4, analysisFactor5, analysisFactor6,
                    analysisText, analysisSuggestion, analysisConfidenceNote, analysisProvider,
                    analysisVersion, analysisStatus, schemaVersion, engineVersion,
                    professionalRulesetId, professionalRulesetVersion,
                    professionalVerificationStatus, professionalSnapshotJson
                ) VALUES (
                    'legacy-2', '旧问题二', 2, '8,8,8,8,8,8', '坤为地', '坤为地',
                    '', '', '', '', '', '', '', '', '', '', '', 'demo', 1, 'DEMO', 1, '0.1.0',
                    'none', '0', 'NOT_AVAILABLE', NULL
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_V3, 3, true, HistoryDatabase.MIGRATION_2_3).use { db ->
            db.query(
                "SELECT castAtEpochMillis, timezoneId, utcOffsetSeconds, professionalGenerationStatus, " +
                    "professionalVerificationStatus, professionalSnapshotJson, professionalErrorCode " +
                    "FROM divination_records WHERE id = 'legacy-2'"
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertNull(cursor.getString(0))
                assertNull(cursor.getString(1))
                assertNull(cursor.getString(2))
                assertEquals("NOT_REQUESTED", cursor.getString(3))
                assertEquals("NOT_AVAILABLE", cursor.getString(4))
                assertNull(cursor.getString(5))
                assertNull(cursor.getString(6))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To3RunsTheCompleteChainWithoutRecalculatingLegacyData() {
        helper.createDatabase(TEST_DB_CHAIN, 1).apply {
            execSQL(
                """
                INSERT INTO divination_records (
                    id, question, createdAtEpochMillis, lineValues, originalName, changedName,
                    analysisSummary, analysisTrend, analysisFactor1, analysisFactor2,
                    analysisFactor3, analysisFactor4, analysisFactor5, analysisFactor6,
                    analysisText, analysisSuggestion, analysisConfidenceNote, analysisProvider,
                    analysisVersion, analysisStatus, schemaVersion, engineVersion
                ) VALUES (
                    'legacy-chain', '迁移链', 3, '7,7,7,7,7,7', '乾为天', '乾为天',
                    '', '', '', '', '', '', '', '', '', '', '', 'demo', 1, 'DEMO', 1, '0.1.0'
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB_CHAIN, 3, true, HistoryDatabase.MIGRATION_1_2, HistoryDatabase.MIGRATION_2_3
        ).use { db ->
            db.query(
                "SELECT question, castAtEpochMillis, timezoneId, professionalGenerationStatus, " +
                    "professionalVerificationStatus, professionalSnapshotJson FROM divination_records " +
                    "WHERE id = 'legacy-chain'"
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("迁移链", cursor.getString(0))
                assertNull(cursor.getString(1))
                assertNull(cursor.getString(2))
                assertEquals("NOT_REQUESTED", cursor.getString(3))
                assertEquals("NOT_AVAILABLE", cursor.getString(4))
                assertNull(cursor.getString(5))
            }
        }
    }

    @Test
    fun migrate3To4CreatesLegacyRevisionButSkipsFailedPlaceholder() {
        helper.createDatabase(TEST_DB_V4, 3).apply {
            execSQL("INSERT INTO divination_records VALUES ('ok','旧成功',1,'7,7,7,7,7,7','乾为天','乾为天','摘要','平稳','因素','','','','','','分析','建议','局限','demo',1,'DEMO',1,'0.1',NULL,NULL,NULL,'NOT_REQUESTED','none','0','NOT_AVAILABLE',NULL,NULL)")
            execSQL("INSERT INTO divination_records VALUES ('bad','旧失败',2,'8,8,8,8,8,8','坤为地','坤为地','服务不可用','待解读','','','','','','','失败','稍后','说明','unavailable',1,'FAILED',1,'0.1',NULL,NULL,NULL,'NOT_REQUESTED','none','0','NOT_AVAILABLE',NULL,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB_V4, 4, true, HistoryDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT divinationId, providerId, status FROM analysis_revisions").use { cursor ->
                assertEquals(1, cursor.count); check(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0)); assertEquals("legacy_v1", cursor.getString(1)); assertEquals("SUCCESS", cursor.getString(2))
            }
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("DELETE FROM divination_records WHERE id='ok'")
            db.query("SELECT COUNT(*) FROM analysis_revisions WHERE divinationId='ok'").use { cursor -> check(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
        }
    }

    @Test
    fun migrate4To5AddsNullableProfessionalProvenance() {
        helper.createDatabase(TEST_DB_V5, 4).apply {
            execSQL("INSERT INTO divination_records VALUES ('v5','迁移',1,'7,7,7,7,7,7','乾为天','乾为天','','','','','','','','','','','none',0,'NOT_CONFIGURED',1,'0.1',NULL,NULL,NULL,'NOT_REQUESTED','none','0','NOT_AVAILABLE',NULL,NULL)")
            execSQL("INSERT INTO analysis_revisions VALUES ('r','v5',1,'SUCCESS','siliconflow','model','p',2,'req','{}',NULL)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB_V5, 5, true, HistoryDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT factsVersion, professionalRulesetId, professionalRulesetVersion, validationProfile, sentCapabilities FROM analysis_revisions WHERE id='r'").use { cursor ->
                check(cursor.moveToFirst())
                repeat(5) { assertNull(cursor.getString(it)) }
            }
        }
    }

    companion object {
        private const val TEST_DB = "migration-test"
        private const val TEST_DB_V3 = "migration-test-v3"
        private const val TEST_DB_CHAIN = "migration-test-chain"
        private const val TEST_DB_V4 = "migration-test-v4"
        private const val TEST_DB_V5 = "migration-test-v5"
    }
}
