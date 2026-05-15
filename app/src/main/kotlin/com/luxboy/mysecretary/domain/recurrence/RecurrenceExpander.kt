package com.luxboy.mysecretary.domain.recurrence

import java.time.LocalDate
import java.time.LocalDateTime

object RecurrenceExpander {

    private const val SAFETY_LIMIT = 1000

    /**
     * Returns occurrence start instants within [rangeStart, rangeEndExclusive) for the given base
     * event start, expanded by [rule]. The base start itself is included if it falls in range.
     */
    fun occurrencesInRange(
        baseStart: LocalDateTime,
        rule: RecurrenceRule,
        rangeStart: LocalDateTime,
        rangeEndExclusive: LocalDateTime,
    ): List<LocalDateTime> {
        if (!rangeEndExclusive.isAfter(rangeStart)) return emptyList()
        val result = mutableListOf<LocalDateTime>()
        var iterations = 0

        sequence(baseStart, rule).forEach { occurrence ->
            if (++iterations > SAFETY_LIMIT) return@forEach
            if (!occurrence.isBefore(rangeEndExclusive)) return result
            if (!occurrence.isBefore(rangeStart)) result.add(occurrence)
        }
        return result
    }

    /**
     * The next occurrence at or after [from], or null if recurrence has ended.
     */
    fun nextOccurrenceAtOrAfter(
        baseStart: LocalDateTime,
        rule: RecurrenceRule,
        from: LocalDateTime,
    ): LocalDateTime? {
        var iterations = 0
        for (occurrence in sequence(baseStart, rule)) {
            if (++iterations > SAFETY_LIMIT) return null
            if (!occurrence.isBefore(from)) return occurrence
        }
        return null
    }

    private fun sequence(baseStart: LocalDateTime, rule: RecurrenceRule): Sequence<LocalDateTime> =
        when (rule.frequency) {
            Frequency.DAILY -> dailySequence(baseStart, rule)
            Frequency.WEEKLY -> weeklySequence(baseStart, rule)
            Frequency.MONTHLY -> monthlySequence(baseStart, rule)
            Frequency.YEARLY -> yearlySequence(baseStart, rule)
        }.takeWhile { occurrence ->
            rule.until?.let { occurrence.toLocalDate() <= it } ?: true
        }

    private fun dailySequence(baseStart: LocalDateTime, rule: RecurrenceRule): Sequence<LocalDateTime> =
        generateSequence(baseStart) { it.plusDays(rule.interval.toLong()) }

    private fun weeklySequence(baseStart: LocalDateTime, rule: RecurrenceRule): Sequence<LocalDateTime> {
        if (rule.byDays.isEmpty()) {
            return generateSequence(baseStart) { it.plusWeeks(rule.interval.toLong()) }
        }
        val orderedDays = rule.byDays.sortedBy { it.value }
        return sequence {
            var weekAnchor: LocalDate = baseStart.toLocalDate().with(java.time.DayOfWeek.MONDAY)
            // weekAnchor is the Monday of the week that contains baseStart
            val time = baseStart.toLocalTime()
            while (true) {
                for (dow in orderedDays) {
                    val candidate = weekAnchor.with(dow).atTime(time)
                    if (!candidate.isBefore(baseStart)) yield(candidate)
                }
                weekAnchor = weekAnchor.plusWeeks(rule.interval.toLong())
            }
        }
    }

    private fun monthlySequence(baseStart: LocalDateTime, rule: RecurrenceRule): Sequence<LocalDateTime> =
        generateSequence(0L) { it + rule.interval.toLong() }
            .map { offset ->
                val target = baseStart.toLocalDate().plusMonths(offset)
                val adjustedDay = minOf(baseStart.dayOfMonth, target.lengthOfMonth())
                target.withDayOfMonth(adjustedDay).atTime(baseStart.toLocalTime())
            }

    private fun yearlySequence(baseStart: LocalDateTime, rule: RecurrenceRule): Sequence<LocalDateTime> =
        generateSequence(0L) { it + rule.interval.toLong() }
            .map { offset ->
                val target = baseStart.toLocalDate().plusYears(offset)
                val adjustedDay = minOf(baseStart.dayOfMonth, target.lengthOfMonth())
                target.withDayOfMonth(adjustedDay).atTime(baseStart.toLocalTime())
            }
}
