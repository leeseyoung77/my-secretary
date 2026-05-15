package com.luxboy.mysecretary.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceExpanderTest {

    private val baseStart = LocalDateTime.of(2026, 5, 15, 10, 0) // Friday

    @Test
    fun `daily expands into range`() {
        val rule = RecurrenceRule(Frequency.DAILY, interval = 1)
        val occurrences = RecurrenceExpander.occurrencesInRange(
            baseStart,
            rule,
            rangeStart = LocalDateTime.of(2026, 5, 15, 0, 0),
            rangeEndExclusive = LocalDateTime.of(2026, 5, 18, 0, 0),
        )
        assertEquals(3, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 5, 15, 10, 0), occurrences[0])
        assertEquals(LocalDateTime.of(2026, 5, 16, 10, 0), occurrences[1])
        assertEquals(LocalDateTime.of(2026, 5, 17, 10, 0), occurrences[2])
    }

    @Test
    fun `daily with interval=3 skips days`() {
        val rule = RecurrenceRule(Frequency.DAILY, interval = 3)
        val occurrences = RecurrenceExpander.occurrencesInRange(
            baseStart,
            rule,
            rangeStart = LocalDateTime.of(2026, 5, 15, 0, 0),
            rangeEndExclusive = LocalDateTime.of(2026, 5, 26, 0, 0),
        )
        assertEquals(4, occurrences.size) // 15, 18, 21, 24
    }

    @Test
    fun `weekly with BYDAY produces correct dates`() {
        val rule = RecurrenceRule(
            Frequency.WEEKLY,
            interval = 1,
            byDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        val occurrences = RecurrenceExpander.occurrencesInRange(
            baseStart,
            rule,
            rangeStart = LocalDateTime.of(2026, 5, 15, 0, 0),
            rangeEndExclusive = LocalDateTime.of(2026, 5, 22, 0, 0),
        )
        // 5/15 Fri, 5/18 Mon, 5/20 Wed
        assertEquals(3, occurrences.size)
        assertEquals(DayOfWeek.FRIDAY, occurrences[0].dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, occurrences[1].dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, occurrences[2].dayOfWeek)
    }

    @Test
    fun `monthly preserves day of month`() {
        val rule = RecurrenceRule(Frequency.MONTHLY, interval = 1)
        val occurrences = RecurrenceExpander.occurrencesInRange(
            baseStart,
            rule,
            rangeStart = LocalDateTime.of(2026, 5, 1, 0, 0),
            rangeEndExclusive = LocalDateTime.of(2026, 8, 1, 0, 0),
        )
        assertEquals(3, occurrences.size)
        assertTrue(occurrences.all { it.dayOfMonth == 15 })
    }

    @Test
    fun `monthly clamps day when month is shorter`() {
        val janEnd = LocalDateTime.of(2026, 1, 31, 9, 0)
        val rule = RecurrenceRule(Frequency.MONTHLY, interval = 1)
        val occurrences = RecurrenceExpander.occurrencesInRange(
            janEnd,
            rule,
            rangeStart = LocalDateTime.of(2026, 2, 1, 0, 0),
            rangeEndExclusive = LocalDateTime.of(2026, 3, 1, 0, 0),
        )
        assertEquals(1, occurrences.size)
        // February 2026 has 28 days
        assertEquals(28, occurrences[0].dayOfMonth)
    }

    @Test
    fun `UNTIL terminates the sequence`() {
        val rule = RecurrenceRule(
            Frequency.DAILY,
            interval = 1,
            until = LocalDate.of(2026, 5, 17),
        )
        val occurrences = RecurrenceExpander.occurrencesInRange(
            baseStart,
            rule,
            rangeStart = LocalDateTime.of(2026, 5, 15, 0, 0),
            rangeEndExclusive = LocalDateTime.of(2026, 5, 22, 0, 0),
        )
        assertEquals(3, occurrences.size) // 15, 16, 17
    }

    @Test
    fun `nextOccurrenceAtOrAfter returns next valid date`() {
        val rule = RecurrenceRule(Frequency.WEEKLY, interval = 1)
        val next = RecurrenceExpander.nextOccurrenceAtOrAfter(
            baseStart,
            rule,
            from = LocalDateTime.of(2026, 5, 20, 0, 0),
        )
        assertNotNull(next)
        assertEquals(LocalDateTime.of(2026, 5, 22, 10, 0), next) // Next Friday
    }
}
