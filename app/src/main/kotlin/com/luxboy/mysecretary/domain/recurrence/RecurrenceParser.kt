package com.luxboy.mysecretary.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Minimal RFC 5545 RRULE subset: FREQ, INTERVAL, BYDAY, UNTIL.
 * UNTIL is serialized in UTC date form (YYYYMMDD) for date-only granularity.
 */
object RecurrenceParser {

    private val byDayMap = mapOf(
        "MO" to DayOfWeek.MONDAY,
        "TU" to DayOfWeek.TUESDAY,
        "WE" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY,
        "SA" to DayOfWeek.SATURDAY,
        "SU" to DayOfWeek.SUNDAY,
    )
    private val byDayReverse = byDayMap.entries.associate { (k, v) -> v to k }

    private val untilFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(rrule: String): RecurrenceRule? {
        val parts = rrule.split(';')
            .mapNotNull { p ->
                val eq = p.indexOf('=')
                if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1)
            }
            .toMap()

        val freq = parts["FREQ"]?.let {
            runCatching { Frequency.valueOf(it.uppercase()) }.getOrNull()
        } ?: return null

        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val byDays = parts["BYDAY"]?.split(',')
            ?.mapNotNull { byDayMap[it.uppercase()] }
            ?.toSet()
            ?: emptySet()
        val until = parts["UNTIL"]?.let { raw ->
            val datePart = raw.substringBefore('T')
            runCatching { LocalDate.parse(datePart, untilFormatter) }.getOrNull()
        }

        return RecurrenceRule(
            frequency = freq,
            interval = interval,
            byDays = byDays,
            until = until,
        )
    }

    fun serialize(rule: RecurrenceRule): String = buildString {
        append("FREQ=").append(rule.frequency.name)
        if (rule.interval > 1) append(";INTERVAL=").append(rule.interval)
        if (rule.byDays.isNotEmpty()) {
            append(";BYDAY=")
            append(rule.byDays.sortedBy { it.value }.joinToString(",") { byDayReverse.getValue(it) })
        }
        rule.until?.let { append(";UNTIL=").append(it.format(untilFormatter)) }
    }
}
