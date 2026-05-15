package com.luxboy.mysecretary.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceParserTest {

    @Test
    fun `parses simple FREQ=DAILY`() {
        val rule = RecurrenceParser.parse("FREQ=DAILY")
        assertEquals(Frequency.DAILY, rule?.frequency)
        assertEquals(1, rule?.interval)
    }

    @Test
    fun `parses FREQ=WEEKLY with BYDAY`() {
        val rule = RecurrenceParser.parse("FREQ=WEEKLY;BYDAY=MO,WE,FR")
        assertEquals(Frequency.WEEKLY, rule?.frequency)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            rule?.byDays,
        )
    }

    @Test
    fun `parses INTERVAL`() {
        val rule = RecurrenceParser.parse("FREQ=MONTHLY;INTERVAL=2")
        assertEquals(2, rule?.interval)
    }

    @Test
    fun `parses UNTIL`() {
        val rule = RecurrenceParser.parse("FREQ=YEARLY;UNTIL=20301231")
        assertEquals(LocalDate.of(2030, 12, 31), rule?.until)
    }

    @Test
    fun `unknown FREQ returns null`() {
        assertNull(RecurrenceParser.parse("FREQ=BOGUS"))
    }

    @Test
    fun `missing FREQ returns null`() {
        assertNull(RecurrenceParser.parse("INTERVAL=2"))
    }

    @Test
    fun `serialize roundtrip preserves rule`() {
        val original = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            interval = 2,
            byDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            until = LocalDate.of(2030, 6, 30),
        )
        val serialized = RecurrenceParser.serialize(original)
        val parsed = RecurrenceParser.parse(serialized)
        assertEquals(original.frequency, parsed?.frequency)
        assertEquals(original.interval, parsed?.interval)
        assertEquals(original.byDays, parsed?.byDays)
        assertEquals(original.until, parsed?.until)
    }
}
