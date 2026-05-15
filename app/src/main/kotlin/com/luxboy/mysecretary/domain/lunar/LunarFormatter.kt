package com.luxboy.mysecretary.domain.lunar

import com.github.usingsky.calendar.KoreanLunarCalendar
import java.time.LocalDate

/**
 * Wrapper around KoreanLunarCalendar that converts a solar [LocalDate] to a short
 * Korean lunar label like "음 5.10" or "윤 5.10" (leap month).
 */
object LunarFormatter {

    fun shortLabel(date: LocalDate): String? {
        return runCatching {
            val cal = KoreanLunarCalendar.getInstance()
            cal.setSolarDate(date.year, date.monthValue, date.dayOfMonth)
            val prefix = if (cal.isIntercalation) "윤" else "음"
            "$prefix ${cal.lunarMonth}.${cal.lunarDay}"
        }.getOrNull()
    }
}
