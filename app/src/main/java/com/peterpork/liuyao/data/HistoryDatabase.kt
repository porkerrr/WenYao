package com.peterpork.liuyao.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "divination_records")
data class DivinationRecord(
    @PrimaryKey val id: String,
    val question: String,
    val createdAtEpochMillis: Long,
    val lineValues: String,
    val originalName: String,
    val changedName: String,
    val analysisSummary: String,
    val analysisTrend: String,
    val analysisFactor1: String,
    val analysisFactor2: String,
    val analysisFactor3: String,
    val analysisFactor4: String,
    val analysisFactor5: String,
    val analysisFactor6: String,
    val analysisText: String,
    val analysisSuggestion: String,
    val analysisConfidenceNote: String,
    val analysisProvider: String = "demo",
    val analysisVersion: Int = 1,
    val analysisStatus: String = "DEMO",
    val schemaVersion: Int = 1,
    val engineVersion: String = "0.1.0",
    val castAtEpochMillis: Long? = null,
    val timezoneId: String? = null,
    val utcOffsetSeconds: Int? = null,
    val professionalGenerationStatus: String = "NOT_REQUESTED",
    val professionalRulesetId: String = "none",
    val professionalRulesetVersion: String = "0",
    val professionalVerificationStatus: String = "NOT_AVAILABLE",
    val professionalSnapshotJson: String? = null,
    val professionalErrorCode: String? = null
)

@Entity(
    tableName = "analysis_revisions",
    foreignKeys = [ForeignKey(
        entity = DivinationRecord::class,
        parentColumns = ["id"],
        childColumns = ["divinationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("divinationId")]
)
data class AnalysisRevisionEntity(
    @PrimaryKey val id: String,
    val divinationId: String,
    val createdAtEpochMillis: Long,
    val status: String,
    val providerId: String,
    val modelId: String?,
    val promptVersion: String,
    val analysisSchemaVersion: Int,
    val requestId: String?,
    val payloadJson: String?,
    val errorCode: String?,
    val factsVersion: Int? = null,
    val professionalRulesetId: String? = null,
    val professionalRulesetVersion: String? = null,
    val validationProfile: String? = null,
    val sentCapabilities: String? = null,
)

@Dao
interface DivinationDao {
    @Query("SELECT * FROM divination_records ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<DivinationRecord>>

    @Query("SELECT * FROM divination_records WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DivinationRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(record: DivinationRecord)

    @Delete
    suspend fun delete(record: DivinationRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnalysisRevision(revision: AnalysisRevisionEntity)

    @Query("SELECT * FROM analysis_revisions WHERE divinationId = :divinationId ORDER BY createdAtEpochMillis DESC")
    suspend fun analysisRevisions(divinationId: String): List<AnalysisRevisionEntity>

    @Query("SELECT * FROM analysis_revisions WHERE divinationId = :divinationId AND status = 'SUCCESS' ORDER BY createdAtEpochMillis DESC LIMIT 1")
    suspend fun latestSuccessfulAnalysis(divinationId: String): AnalysisRevisionEntity?

    @Query("SELECT * FROM analysis_revisions WHERE divinationId = :divinationId ORDER BY createdAtEpochMillis DESC LIMIT 1")
    suspend fun latestAnalysisAttempt(divinationId: String): AnalysisRevisionEntity?

    @Query("SELECT COUNT(*) FROM divination_records")
    suspend fun count(): Int
}

@Database(entities = [DivinationRecord::class, AnalysisRevisionEntity::class], version = 5, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun dao(): DivinationDao

    companion object {
        fun create(context: Context): HistoryDatabase = Room.databaseBuilder(
            context.applicationContext,
            HistoryDatabase::class.java,
            "wenyao-history.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE divination_records ADD COLUMN professionalRulesetId TEXT NOT NULL DEFAULT 'none'")
                db.execSQL("ALTER TABLE divination_records ADD COLUMN professionalRulesetVersion TEXT NOT NULL DEFAULT '0'")
                db.execSQL("ALTER TABLE divination_records ADD COLUMN professionalVerificationStatus TEXT NOT NULL DEFAULT 'NOT_AVAILABLE'")
                db.execSQL("ALTER TABLE divination_records ADD COLUMN professionalSnapshotJson TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE divination_records ADD COLUMN castAtEpochMillis INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE divination_records ADD COLUMN timezoneId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE divination_records ADD COLUMN utcOffsetSeconds INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE divination_records ADD COLUMN professionalGenerationStatus TEXT NOT NULL DEFAULT 'NOT_REQUESTED'")
                db.execSQL("ALTER TABLE divination_records ADD COLUMN professionalErrorCode TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS analysis_revisions (
                        id TEXT NOT NULL PRIMARY KEY,
                        divinationId TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        modelId TEXT,
                        promptVersion TEXT NOT NULL,
                        analysisSchemaVersion INTEGER NOT NULL,
                        requestId TEXT,
                        payloadJson TEXT,
                        errorCode TEXT,
                        FOREIGN KEY(divinationId) REFERENCES divination_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_analysis_revisions_divinationId ON analysis_revisions(divinationId)")
                db.execSQL(
                    """INSERT INTO analysis_revisions (
                        id, divinationId, createdAtEpochMillis, status, providerId, modelId,
                        promptVersion, analysisSchemaVersion, requestId, payloadJson, errorCode
                    )
                    SELECT 'legacy-' || id, id, createdAtEpochMillis, 'SUCCESS',
                           'legacy_v1', NULL, 'legacy-v1', 1, NULL, NULL, NULL
                    FROM divination_records
                    WHERE analysisStatus IN ('SUCCESS', 'DEMO') AND analysisSummary != ''
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE analysis_revisions ADD COLUMN factsVersion INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE analysis_revisions ADD COLUMN professionalRulesetId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE analysis_revisions ADD COLUMN professionalRulesetVersion TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE analysis_revisions ADD COLUMN validationProfile TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE analysis_revisions ADD COLUMN sentCapabilities TEXT DEFAULT NULL")
            }
        }
    }
}
