package com.peterpork.liuyao.domain

import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.time.Instant
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEngineTest {
    private val adapter = LunarJavaCalendarAdapter()
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun `all 60 ganzhi match frozen xunkong fixture`() {
        val rows = javaClass.getResourceAsStream("/professional/xunkong_golden.csv")!!
            .bufferedReader().readLines().drop(1)
        assertEquals(60, rows.size)
        rows.forEach { row ->
            val (ganzhi, expected) = row.split(",")
            assertEquals(expected.toList(), XunkongEngine.calculate(GanZhi.parse(ganzhi)).map { it.glyph })
        }
    }

    @Test
    fun `same instant has same month builder in every user zone`() {
        val instant = Instant.parse("2026-03-05T20:30:00Z")
        val zones = listOf("Asia/Shanghai", "UTC", "America/New_York", "Europe/London")
        val months = zones.map { adapter.calculate(instant, ZoneId.of(it), CalendarConvention()).monthGanZhi }
        assertEquals(1, months.toSet().size)
    }

    @Test
    fun `local day changes at midnight and not at 23`() {
        fun day(local: String) = adapter.calculate(
            LocalDateTime.parse(local).atZone(shanghai).toInstant(), shanghai, CalendarConvention()
        ).dayGanZhi
        assertEquals(day("2026-09-13T22:59:59"), day("2026-09-13T23:59:59"))
        assertNotEquals(day("2026-09-13T23:59:59"), day("2026-09-14T00:00:00"))
        assertEquals(day("2026-09-14T00:00:00"), day("2026-09-14T00:00:01"))
    }

    @Test
    fun `every precise jie switches month at its UTC8 instant`() {
        val lunar = Solar.fromYmd(2026, 6, 1).lunar
        val jieNames = Lunar.JIE_QI_IN_USE.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        val boundaries = lunar.jieQiTable.filterKeys { it in jieNames }.values.distinctBy { it.toYmdHms() }
        boundaries.forEach { solar ->
            val boundary = LocalDateTime.of(
                solar.year, solar.month, solar.day, solar.hour, solar.minute, solar.second
            ).atZone(shanghai).toInstant()
            val before = adapter.calculate(boundary.minusSeconds(1), ZoneId.of("America/New_York"), CalendarConvention())
            val at = adapter.calculate(boundary, ZoneId.of("America/New_York"), CalendarConvention())
            val after = adapter.calculate(boundary.plusSeconds(1), ZoneId.of("America/New_York"), CalendarConvention())
            assertNotEquals(solar.toYmdHms(), before.monthGanZhi, at.monthGanZhi)
            assertEquals(solar.toYmdHms(), at.monthGanZhi, after.monthGanZhi)
        }
    }

    @Test
    fun `representative jie instants agree with independent HKO almanac minutes`() {
        // Independent minute-level oracle: Hong Kong Observatory Almanac 2026.
        // lunar-java supplies seconds; truncating keeps this independent of its second value.
        val expectedUtcMinutes = mapOf(
            "小寒" to Instant.parse("2026-01-05T08:23:00Z"),
            "立春" to Instant.parse("2026-02-03T20:02:00Z"),
            "立夏" to Instant.parse("2026-05-05T11:49:00Z"),
            "立秋" to Instant.parse("2026-08-07T11:43:00Z"),
            "大雪" to Instant.parse("2026-12-07T02:53:00Z")
        )
        val table = Solar.fromYmd(2026, 6, 1).lunar.jieQiTable
        expectedUtcMinutes.forEach { (term, expected) ->
            val solar = table.getValue(term)
            val actual = LocalDateTime.of(
                solar.year, solar.month, solar.day, solar.hour, solar.minute, solar.second
            ).toInstant(ZoneOffset.ofHours(8)).truncatedTo(ChronoUnit.MINUTES)
            val differenceSeconds = kotlin.math.abs(Duration.between(expected, actual).seconds)
            assertTrue("$term expected HKO minute $expected but provider returned $actual", differenceSeconds <= 60)
        }
    }

    @Test
    fun `feeding New York wall clock directly is a proven counterexample`() {
        val lunar = Solar.fromYmd(2026, 6, 1).lunar
        val solar = lunar.jieQiTable.getValue("立春")
        val boundary = LocalDateTime.of(solar.year, solar.month, solar.day, solar.hour, solar.minute, solar.second)
            .atZone(shanghai).toInstant()
        val correct = adapter.calculate(boundary, ZoneId.of("America/New_York"), CalendarConvention()).monthGanZhi
        val ny = boundary.atZone(ZoneId.of("America/New_York"))
        val wronglyFed = GanZhi.parse(Solar.fromYmdHms(ny.year, ny.monthValue, ny.dayOfMonth, ny.hour, ny.minute, ny.second).lunar.monthInGanZhiExact)
        assertNotEquals(correct, wronglyFed)
    }
}
