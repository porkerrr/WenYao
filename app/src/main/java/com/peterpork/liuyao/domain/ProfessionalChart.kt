package com.peterpork.liuyao.domain

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class VerificationStatus { NOT_AVAILABLE, EXPERIMENTAL, CROSS_VALIDATED, VERIFIED }
enum class ProfessionalCapability { STATIC_CORE, CALENDAR_CONTEXT, CHANGED_LINE_RELATION }
enum class ProfessionalGenerationStatus { NOT_REQUESTED, SUCCEEDED, FAILED }
enum class ProfessionalErrorCode { CALENDAR_FAILED, SNAPSHOT_FAILED, PROFESSIONAL_ENGINE_FAILED }

data class RulesetRef(val id: String, val version: String)

data class ProfessionalChartInput(
    val basic: PaipanResult,
    val castAt: Instant,
    val zoneId: ZoneId
)

data class ProfessionalChartSnapshot(val payloadJson: String) {
    init { require(payloadJson.isNotBlank()) { "专业排盘快照不能为空" } }
}

data class ProfessionalGenerationResult(
    val generationStatus: ProfessionalGenerationStatus,
    val ruleset: RulesetRef?,
    val verificationStatus: VerificationStatus?,
    val snapshot: ProfessionalChartSnapshot?,
    val errorCode: ProfessionalErrorCode?
) {
    init {
        when (generationStatus) {
            ProfessionalGenerationStatus.NOT_REQUESTED -> require(
                ruleset == null && verificationStatus == null && snapshot == null && errorCode == null
            )
            ProfessionalGenerationStatus.SUCCEEDED -> require(
                ruleset != null && verificationStatus != null &&
                    verificationStatus != VerificationStatus.NOT_AVAILABLE && snapshot != null && errorCode == null
            )
            ProfessionalGenerationStatus.FAILED -> require(
                ruleset != null && verificationStatus != null &&
                    verificationStatus != VerificationStatus.NOT_AVAILABLE && snapshot == null && errorCode != null
            )
        }
    }

    companion object {
        fun notRequested() = ProfessionalGenerationResult(
            ProfessionalGenerationStatus.NOT_REQUESTED, null, null, null, null
        )
        fun failed(code: ProfessionalErrorCode) = ProfessionalGenerationResult(
            ProfessionalGenerationStatus.FAILED,
            ExperimentalProfessionalChartEngine.RULESET,
            VerificationStatus.EXPERIMENTAL,
            null,
            code
        )
    }
}

fun interface ProfessionalChartEngine {
    fun calculate(input: ProfessionalChartInput): ProfessionalGenerationResult
}

object UnavailableProfessionalChartEngine : ProfessionalChartEngine {
    override fun calculate(input: ProfessionalChartInput): ProfessionalGenerationResult =
        ProfessionalGenerationResult.notRequested()
}

@Serializable
data class ProfessionalLineSnapshot(
    val position: Int,
    val ganZhi: String,
    val element: String,
    val relation: String,
    val spirit: String,
    val shiYing: String? = null,
    val moving: Boolean
)

@Serializable
data class ChangedLineSnapshot(
    val position: Int,
    val ganZhi: String,
    val element: String,
    val relation: String
)

@Serializable
data class ExperimentalProfessionalSnapshot(
    val schemaVersion: Int,
    val rulesetId: String,
    val rulesetVersion: String,
    val verificationStatus: String,
    val castAt: String,
    val userZoneId: String,
    val utcOffsetSeconds: Int,
    val solarTermZoneId: String,
    val calendarAdapterVersion: String,
    val monthGanZhi: String,
    val dayGanZhi: String,
    val xunkong: List<String>,
    val originalName: String,
    val originalPalace: String,
    val originalClass: String,
    val shiPosition: Int,
    val yingPosition: Int,
    val originalLines: List<ProfessionalLineSnapshot>,
    val changedName: String,
    val changedPatternKey: Int,
    val changedLines: List<ChangedLineSnapshot>
) {
    fun validatedCapabilities(): Set<ProfessionalCapability> = when (verificationStatus) {
        VerificationStatus.CROSS_VALIDATED.name -> setOf(ProfessionalCapability.STATIC_CORE)
        VerificationStatus.VERIFIED.name -> ProfessionalCapability.entries.toSet()
        else -> emptySet()
    }
}

object ProfessionalSnapshotCodec {
    const val CURRENT_SCHEMA_VERSION = 1
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    fun encode(value: ExperimentalProfessionalSnapshot): ProfessionalChartSnapshot =
        ProfessionalChartSnapshot(json.encodeToString(ExperimentalProfessionalSnapshot.serializer(), value))
    fun decode(snapshot: ProfessionalChartSnapshot): ExperimentalProfessionalSnapshot =
        json.decodeFromString(ExperimentalProfessionalSnapshot.serializer(), snapshot.payloadJson).also {
            require(it.schemaVersion == CURRENT_SCHEMA_VERSION) { "不支持的专业快照版本：${it.schemaVersion}" }
        }
}

class ExperimentalProfessionalChartEngine(
    private val calendarProvider: CalendarProviderAdapter = LunarJavaCalendarAdapter(),
    private val codec: (ExperimentalProfessionalSnapshot) -> ProfessionalChartSnapshot = ProfessionalSnapshotCodec::encode
) : ProfessionalChartEngine {
    override fun calculate(input: ProfessionalChartInput): ProfessionalGenerationResult {
        val calendar = try {
            calendarProvider.calculate(input.castAt, input.zoneId, CalendarConvention())
        } catch (_: Exception) {
            return ProfessionalGenerationResult.failed(ProfessionalErrorCode.CALENDAR_FAILED)
        }
        val snapshot = try {
            codec(assemble(input.basic, calendar))
        } catch (_: Exception) {
            return ProfessionalGenerationResult.failed(ProfessionalErrorCode.SNAPSHOT_FAILED)
        }
        return ProfessionalGenerationResult(
            ProfessionalGenerationStatus.SUCCEEDED,
            RULESET,
            VerificationStatus.CROSS_VALIDATED,
            snapshot,
            null
        )
    }

    private fun assemble(basic: PaipanResult, calendar: CalendarContext): ExperimentalProfessionalSnapshot {
        val original = StaticChartEngine.calculateOriginal(HexagramPattern.fromBitsBottomUp(basic.original.yangBottomUp))
        val spirits = LiushenEngine.forDayStem(calendar.dayGanZhi.stem)
        val originalLines = original.lines.map { line ->
            val role = when (line.position) {
                original.palace.shiPosition -> "SHI"
                original.palace.yingPosition -> "YING"
                else -> null
            }
            ProfessionalLineSnapshot(
                line.position,
                line.najia.toString(),
                line.najia.element.name,
                line.relation.name,
                spirits[line.position - 1].name,
                role,
                line.position in basic.movingLines
            )
        }
        val changed = ChangedLineEngine.calculate(basic.changed, basic.movingLines, original.palace.palaceElement)
        return ExperimentalProfessionalSnapshot(
            schemaVersion = ProfessionalSnapshotCodec.CURRENT_SCHEMA_VERSION,
            rulesetId = RULESET.id,
            rulesetVersion = RULESET.version,
            verificationStatus = VerificationStatus.CROSS_VALIDATED.name,
            castAt = calendar.instant.toString(),
            userZoneId = calendar.userZoneId,
            utcOffsetSeconds = calendar.utcOffsetSeconds,
            solarTermZoneId = calendar.solarTermZoneId,
            calendarAdapterVersion = calendar.adapterVersion,
            monthGanZhi = calendar.monthGanZhi.toString(),
            dayGanZhi = calendar.dayGanZhi.toString(),
            xunkong = calendar.xunkong.map { it.glyph.toString() },
            originalName = basic.original.displayName,
            originalPalace = original.palace.palace.name,
            originalClass = original.palace.hexagramClass.name,
            shiPosition = original.palace.shiPosition,
            yingPosition = original.palace.yingPosition,
            originalLines = originalLines,
            changedName = changed.name,
            changedPatternKey = changed.pattern.key,
            changedLines = changed.changedLines.map {
                ChangedLineSnapshot(it.position, it.najia.toString(), it.najia.element.name, it.relation.name)
            }
        )
    }

    companion object {
        val RULESET = RulesetRef("wenyao-mainstream", "1.0.0-static-cross-validated")
    }
}
