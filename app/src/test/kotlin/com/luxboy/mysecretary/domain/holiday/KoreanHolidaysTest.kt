package com.luxboy.mysecretary.domain.holiday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KoreanHolidaysTest {

    @Test
    fun `신정 is detected every year`() {
        assertEquals("신정", KoreanHolidays.nameOf(LocalDate.of(2025, 1, 1)))
        assertEquals("신정", KoreanHolidays.nameOf(LocalDate.of(2026, 1, 1)))
        assertEquals("신정", KoreanHolidays.nameOf(LocalDate.of(2030, 1, 1)))
    }

    @Test
    fun `삼일절 March 1`() {
        assertEquals("삼일절", KoreanHolidays.nameOf(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun `광복절 August 15`() {
        assertEquals("광복절", KoreanHolidays.nameOf(LocalDate.of(2026, 8, 15)))
    }

    @Test
    fun `한글날 October 9`() {
        assertEquals("한글날", KoreanHolidays.nameOf(LocalDate.of(2026, 10, 9)))
    }

    @Test
    fun `성탄절 December 25`() {
        assertEquals("성탄절", KoreanHolidays.nameOf(LocalDate.of(2026, 12, 25)))
    }

    @Test
    fun `설날 2026 is February 17`() {
        assertEquals("설날", KoreanHolidays.nameOf(LocalDate.of(2026, 2, 17)))
        assertEquals("설날 연휴", KoreanHolidays.nameOf(LocalDate.of(2026, 2, 16)))
        assertEquals("설날 연휴", KoreanHolidays.nameOf(LocalDate.of(2026, 2, 18)))
    }

    @Test
    fun `추석 2026 is September 25`() {
        assertEquals("추석", KoreanHolidays.nameOf(LocalDate.of(2026, 9, 25)))
    }

    @Test
    fun `부처님 오신 날 2026 is May 24`() {
        assertEquals("부처님 오신 날", KoreanHolidays.nameOf(LocalDate.of(2026, 5, 24)))
    }

    @Test
    fun `non-holiday returns null`() {
        assertNull(KoreanHolidays.nameOf(LocalDate.of(2026, 5, 15)))
        assertNull(KoreanHolidays.nameOf(LocalDate.of(2026, 7, 8)))
    }

    @Test
    fun `isHoliday matches nameOf`() {
        assertTrue(KoreanHolidays.isHoliday(LocalDate.of(2026, 1, 1)))
        assertTrue(KoreanHolidays.isHoliday(LocalDate.of(2026, 12, 25)))
        assertFalse(KoreanHolidays.isHoliday(LocalDate.of(2026, 5, 15)))
    }
}
