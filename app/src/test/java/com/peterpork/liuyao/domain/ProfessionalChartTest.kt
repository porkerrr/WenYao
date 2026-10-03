package com.peterpork.liuyao.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfessionalChartTest {
    private val basic = LiuyaoEngine.calculate(
        listOf(LineValue.YOUNG_YANG, LineValue.OLD_YANG, LineValue.YOUNG_YIN,
            LineValue.YOUNG_YIN, LineValue.YOUNG_YIN, LineValue.YOUNG_YIN)
    )
    private val input = ProfessionalChartInput(basic, Instant.parse("2026-09-13T04:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test
    fun `not requested has no ruleset verification or snapshot`() {
        val result = ProfessionalGenerationResult.notRequested()
        assertEquals(ProfessionalGenerationStatus.NOT_REQUESTED, result.generationStatus)
        assertNull(result.verificationStatus)
        assertNull(result.snapshot)
    }

    @Test
    fun `calendar failure is separated from experimental verification`() {
        val engine = ExperimentalProfessionalChartEngine(CalendarProviderAdapter { _, _, _ -> error("calendar") })
        val result = engine.calculate(input)
        assertEquals(ProfessionalGenerationStatus.FAILED, result.generationStatus)
        assertEquals(VerificationStatus.EXPERIMENTAL, result.verificationStatus)
        assertEquals(ProfessionalErrorCode.CALENDAR_FAILED, result.errorCode)
        assertNull(result.snapshot)
    }

    @Test
    fun `snapshot failure is classified without exposing partial chart`() {
        val engine = ExperimentalProfessionalChartEngine(FixedCalendarProvider) { error("codec") }
        val result = engine.calculate(input)
        assertEquals(ProfessionalGenerationStatus.FAILED, result.generationStatus)
        assertEquals(ProfessionalErrorCode.SNAPSHOT_FAILED, result.errorCode)
        assertNull(result.snapshot)
    }

    @Test
    fun `successful snapshot is stable round trip and changed side contains moving lines only`() {
        val engine = ExperimentalProfessionalChartEngine(FixedCalendarProvider)
        val first = engine.calculate(input)
        val second = engine.calculate(input)
        assertEquals(ProfessionalGenerationStatus.SUCCEEDED, first.generationStatus)
        assertEquals(first.snapshot, second.snapshot)
        val decoded = ProfessionalSnapshotCodec.decode(first.snapshot!!)
        assertEquals(listOf(2), decoded.changedLines.map { it.position })
        assertEquals(6, decoded.originalLines.size)
        assertEquals(decoded, ProfessionalSnapshotCodec.decode(ProfessionalSnapshotCodec.encode(decoded)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `failed state rejects a snapshot`() {
        ProfessionalGenerationResult(
            ProfessionalGenerationStatus.FAILED,
            ExperimentalProfessionalChartEngine.RULESET,
            VerificationStatus.CROSS_VALIDATED,
            ProfessionalChartSnapshot("{}"),
            ProfessionalErrorCode.CALENDAR_FAILED
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown snapshot schema is rejected at the codec boundary`() {
        val snapshot = ExperimentalProfessionalChartEngine(FixedCalendarProvider).calculate(input).snapshot!!
        ProfessionalSnapshotCodec.decode(
            ProfessionalChartSnapshot(snapshot.payloadJson.replace("\"schemaVersion\":1", "\"schemaVersion\":99"))
        )
    }

    private object FixedCalendarProvider : CalendarProviderAdapter {
        override fun calculate(instant: Instant, userZone: ZoneId, convention: CalendarConvention) = CalendarContext(
            instant, userZone.id, 28800, GanZhi.parse("乙酉"), GanZhi.parse("庚寅"),
            listOf(EarthlyBranch.WU, EarthlyBranch.WEI), "Asia/Shanghai", "test"
        )
    }
}
