package com.peterpork.liuyao.domain

import com.nlf.calendar.Solar
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

enum class DayBoundary { LOCAL_MIDNIGHT }
enum class MonthBoundary { PRECISE_JIE_INSTANT_UTC8 }

data class CalendarConvention(
    val dayBoundary: DayBoundary = DayBoundary.LOCAL_MIDNIGHT,
    val monthBoundary: MonthBoundary = MonthBoundary.PRECISE_JIE_INSTANT_UTC8
)

data class CalendarContext(
    val instant: Instant,
    val userZoneId: String,
    val utcOffsetSeconds: Int,
    val monthGanZhi: GanZhi,
    val dayGanZhi: GanZhi,
    val xunkong: List<EarthlyBranch>,
    val solarTermZoneId: String,
    val adapterVersion: String
) {
    val monthBuilder: EarthlyBranch get() = monthGanZhi.branch
    val dayBranch: EarthlyBranch get() = dayGanZhi.branch
}

fun interface CalendarProviderAdapter {
    fun calculate(instant: Instant, userZone: ZoneId, convention: CalendarConvention): CalendarContext
}

object XunkongEngine {
    private val pairs = listOf(
        listOf(EarthlyBranch.XU, EarthlyBranch.HAI),
        listOf(EarthlyBranch.SHEN, EarthlyBranch.YOU),
        listOf(EarthlyBranch.WU, EarthlyBranch.WEI),
        listOf(EarthlyBranch.CHEN, EarthlyBranch.SI),
        listOf(EarthlyBranch.YIN, EarthlyBranch.MAO),
        listOf(EarthlyBranch.ZI, EarthlyBranch.CHOU)
    )

    fun calculate(day: GanZhi): List<EarthlyBranch> {
        val index = (0 until 60).firstOrNull {
            HeavenlyStem.entries[it % 10] == day.stem && EarthlyBranch.entries[it % 12] == day.branch
        } ?: throw IllegalArgumentException("干支不属于六十甲子：$day")
        return pairs[index / 10]
    }
}

/** lunar-java's zone-less solar-term clock values are always interpreted in UTC+8. */
class LunarJavaCalendarAdapter : CalendarProviderAdapter {
    override fun calculate(instant: Instant, userZone: ZoneId, convention: CalendarConvention): CalendarContext {
        require(convention.dayBoundary == DayBoundary.LOCAL_MIDNIGHT)
        require(convention.monthBoundary == MonthBoundary.PRECISE_JIE_INSTANT_UTC8)

        val local = instant.atZone(userZone)
        val termClock = instant.atOffset(SOLAR_TERM_OFFSET)
        val localLunar = solar(local.year, local.monthValue, local.dayOfMonth, local.hour, local.minute, local.second).lunar
        val termLunar = solar(
            termClock.year,
            termClock.monthValue,
            termClock.dayOfMonth,
            termClock.hour,
            termClock.minute,
            termClock.second
        ).lunar
        val month = GanZhi.parse(termLunar.monthInGanZhiExact)
        val day = GanZhi.parse(localLunar.dayInGanZhiExact2)
        return CalendarContext(
            instant = instant,
            userZoneId = userZone.id,
            utcOffsetSeconds = local.offset.totalSeconds,
            monthGanZhi = month,
            dayGanZhi = day,
            xunkong = XunkongEngine.calculate(day),
            solarTermZoneId = SOLAR_TERM_OFFSET.id,
            adapterVersion = ADAPTER_VERSION
        )
    }

    private fun solar(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Solar =
        Solar.fromYmdHms(year, month, day, hour, minute, second)

    companion object {
        val SOLAR_TERM_OFFSET: ZoneOffset = ZoneOffset.ofHours(8)
        const val ADAPTER_VERSION = "lunar-java:1.7.7/utc8-v1"
    }
}
