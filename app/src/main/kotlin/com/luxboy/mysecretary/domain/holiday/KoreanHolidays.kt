package com.luxboy.mysecretary.domain.holiday

import java.time.LocalDate
import java.time.MonthDay

/**
 * Korean public holidays. Solar-based holidays are computed annually,
 * lunar-based holidays (설날, 부처님오신날, 추석) are looked up from a hand-curated
 * table covering 2025-2030.
 */
object KoreanHolidays {

    // Fixed solar-date holidays (recur every year)
    private val fixedSolar: List<Pair<MonthDay, String>> = listOf(
        MonthDay.of(1, 1) to "신정",
        MonthDay.of(3, 1) to "삼일절",
        MonthDay.of(5, 5) to "어린이날",
        MonthDay.of(6, 6) to "현충일",
        MonthDay.of(8, 15) to "광복절",
        MonthDay.of(10, 3) to "개천절",
        MonthDay.of(10, 9) to "한글날",
        MonthDay.of(12, 25) to "성탄절",
    )

    // Lunar-based holidays — pre-computed solar dates for the supported range.
    // Each entry: solar date → display label.
    private val variable: Map<LocalDate, String> = listOf(
        // 2025
        LocalDate.of(2025, 1, 28) to "설날 연휴",
        LocalDate.of(2025, 1, 29) to "설날",
        LocalDate.of(2025, 1, 30) to "설날 연휴",
        LocalDate.of(2025, 5, 5) to "부처님 오신 날",
        LocalDate.of(2025, 10, 5) to "추석 연휴",
        LocalDate.of(2025, 10, 6) to "추석",
        LocalDate.of(2025, 10, 7) to "추석 연휴",
        LocalDate.of(2025, 10, 8) to "대체 공휴일",

        // 2026
        LocalDate.of(2026, 2, 16) to "설날 연휴",
        LocalDate.of(2026, 2, 17) to "설날",
        LocalDate.of(2026, 2, 18) to "설날 연휴",
        LocalDate.of(2026, 5, 24) to "부처님 오신 날",
        LocalDate.of(2026, 5, 25) to "대체 공휴일",
        LocalDate.of(2026, 9, 24) to "추석 연휴",
        LocalDate.of(2026, 9, 25) to "추석",
        LocalDate.of(2026, 9, 26) to "추석 연휴",

        // 2027
        LocalDate.of(2027, 2, 6) to "설날 연휴",
        LocalDate.of(2027, 2, 7) to "설날",
        LocalDate.of(2027, 2, 8) to "설날 연휴",
        LocalDate.of(2027, 5, 13) to "부처님 오신 날",
        LocalDate.of(2027, 9, 14) to "추석 연휴",
        LocalDate.of(2027, 9, 15) to "추석",
        LocalDate.of(2027, 9, 16) to "추석 연휴",

        // 2028
        LocalDate.of(2028, 1, 26) to "설날 연휴",
        LocalDate.of(2028, 1, 27) to "설날",
        LocalDate.of(2028, 1, 28) to "설날 연휴",
        LocalDate.of(2028, 5, 2) to "부처님 오신 날",
        LocalDate.of(2028, 10, 2) to "추석 연휴",
        LocalDate.of(2028, 10, 3) to "추석",
        LocalDate.of(2028, 10, 4) to "추석 연휴",

        // 2029
        LocalDate.of(2029, 2, 12) to "설날 연휴",
        LocalDate.of(2029, 2, 13) to "설날",
        LocalDate.of(2029, 2, 14) to "설날 연휴",
        LocalDate.of(2029, 5, 20) to "부처님 오신 날",
        LocalDate.of(2029, 9, 21) to "추석 연휴",
        LocalDate.of(2029, 9, 22) to "추석",
        LocalDate.of(2029, 9, 23) to "추석 연휴",

        // 2030
        LocalDate.of(2030, 2, 2) to "설날 연휴",
        LocalDate.of(2030, 2, 3) to "설날",
        LocalDate.of(2030, 2, 4) to "설날 연휴",
        LocalDate.of(2030, 5, 9) to "부처님 오신 날",
        LocalDate.of(2030, 9, 11) to "추석 연휴",
        LocalDate.of(2030, 9, 12) to "추석",
        LocalDate.of(2030, 9, 13) to "추석 연휴",
    ).toMap()

    fun nameOf(date: LocalDate): String? {
        variable[date]?.let { return it }
        val md = MonthDay.from(date)
        return fixedSolar.firstOrNull { it.first == md }?.second
    }

    fun isHoliday(date: LocalDate): Boolean = nameOf(date) != null
}
