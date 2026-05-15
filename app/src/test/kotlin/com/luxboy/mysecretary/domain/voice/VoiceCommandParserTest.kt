package com.luxboy.mysecretary.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class VoiceCommandParserTest {

    private val today: LocalDate = LocalDate.of(2026, 5, 15) // Friday

    // ─── Intent classification ────────────────────────────

    @Test
    fun `add intent for simple title and time`() {
        val r = VoiceCommandParser.parse("내일 오후 3시 회의", today)
        assertEquals(VoiceIntent.ADD, r.intent)
    }

    @Test
    fun `delete intent when keyword present`() {
        val r = VoiceCommandParser.parse("오늘 회의 삭제", today)
        assertEquals(VoiceIntent.DELETE, r.intent)
    }

    @Test
    fun `query intent for explicit query keyword`() {
        val r = VoiceCommandParser.parse("오늘 일정 알려줘", today)
        assertEquals(VoiceIntent.QUERY, r.intent)
    }

    @Test
    fun `call intent for phone keyword`() {
        val r = VoiceCommandParser.parse("철수에게 전화", today)
        assertEquals(VoiceIntent.CALL, r.intent)
        assertEquals("철수", r.title)
    }

    @Test
    fun `add intent when 있어 used as statement filler`() {
        // 이전에 QUERY로 잘못 분류되었던 케이스가 ADD로 처리되어야 함
        val r = VoiceCommandParser.parse("오늘 오후 3시 약속 있어", today)
        assertEquals(VoiceIntent.ADD, r.intent)
        assertEquals("약속", r.title)
    }

    @Test
    fun `query intent preserved for 있나`() {
        val r = VoiceCommandParser.parse("오늘 회의 있나", today)
        assertEquals(VoiceIntent.QUERY, r.intent)
    }

    // ─── Date parsing ────────────────────────────

    @Test
    fun `오늘 maps to today`() {
        val r = VoiceCommandParser.parse("오늘 회의", today)
        assertEquals(today, r.date)
    }

    @Test
    fun `내일 maps to today plus 1`() {
        val r = VoiceCommandParser.parse("내일 회의", today)
        assertEquals(today.plusDays(1), r.date)
    }

    @Test
    fun `모레 maps to today plus 2`() {
        val r = VoiceCommandParser.parse("모레 회의", today)
        assertEquals(today.plusDays(2), r.date)
    }

    @Test
    fun `다음주 월요일 maps to next week Monday`() {
        // today = Friday 2026-05-15. 이번주 월요일은 5/11, 다음주 월요일은 5/18.
        val r = VoiceCommandParser.parse("다음주 월요일 회의", today)
        assertEquals(LocalDate.of(2026, 5, 18), r.date)
    }

    @Test
    fun `이번주 토요일 maps to this Saturday`() {
        val r = VoiceCommandParser.parse("이번주 토요일 회의", today)
        assertEquals(LocalDate.of(2026, 5, 16), r.date)
    }

    @Test
    fun `M월 D일 maps to that date this year`() {
        val r = VoiceCommandParser.parse("8월 15일 휴가", today)
        assertEquals(LocalDate.of(2026, 8, 15), r.date)
    }

    // ─── Time parsing ────────────────────────────

    @Test
    fun `오후 3시 maps to 15h00`() {
        val r = VoiceCommandParser.parse("오후 3시 회의", today)
        assertEquals(LocalTime.of(15, 0), r.time)
    }

    @Test
    fun `오전 9시 maps to 09h00`() {
        val r = VoiceCommandParser.parse("오전 9시 회의", today)
        assertEquals(LocalTime.of(9, 0), r.time)
    }

    @Test
    fun `3시 with no am-pm defaults to PM for small hours`() {
        val r = VoiceCommandParser.parse("3시 회의", today)
        assertEquals(LocalTime.of(15, 0), r.time)
    }

    @Test
    fun `9시 반 = 09h30`() {
        val r = VoiceCommandParser.parse("9시 반 회의", today)
        assertEquals(LocalTime.of(9, 30), r.time)
    }

    @Test
    fun `정오 maps to noon`() {
        val r = VoiceCommandParser.parse("정오 약속", today)
        assertEquals(LocalTime.of(12, 0), r.time)
    }

    @Test
    fun `자정 maps to midnight`() {
        val r = VoiceCommandParser.parse("자정 자정 모임", today)
        assertEquals(LocalTime.of(0, 0), r.time)
    }

    // ─── Title extraction ────────────────────────────

    @Test
    fun `title strips date and time keywords`() {
        val r = VoiceCommandParser.parse("내일 오후 3시 회의", today)
        assertEquals("회의", r.title)
    }

    @Test
    fun `title strips trailing 에 particle`() {
        val r = VoiceCommandParser.parse("내일 점심에 약속", today)
        // "점심" gets stripped as a time keyword? Actually 점심 is in time list
        // After stripping: "약속"
        assertEquals("약속", r.title)
    }

    @Test
    fun `title empty when only structural words`() {
        val r = VoiceCommandParser.parse("오늘 일정 알려줘", today)
        assertEquals("", r.title)
    }

    // ─── Relative time (new alarm feature) ────────────────────────────

    @Test
    fun `10분 뒤 produces 10-minute duration`() {
        val r = VoiceCommandParser.parse("10분 뒤 알려줘", today)
        assertEquals(Duration.ofMinutes(10), r.relativeFromNow)
        assertEquals(VoiceIntent.ADD, r.intent) // 알려줘에도 ADD 우선
    }

    @Test
    fun `30분 뒤 깨워줘 produces 30-minute duration with ADD`() {
        val r = VoiceCommandParser.parse("30분 뒤 깨워줘", today)
        assertEquals(Duration.ofMinutes(30), r.relativeFromNow)
        assertEquals(VoiceIntent.ADD, r.intent)
    }

    @Test
    fun `1시간 뒤 produces 60-minute duration`() {
        val r = VoiceCommandParser.parse("1시간 뒤 알려줘", today)
        assertEquals(Duration.ofMinutes(60), r.relativeFromNow)
    }

    @Test
    fun `한 시간 뒤 (Korean numeral) produces 60-minute duration`() {
        val r = VoiceCommandParser.parse("한 시간 뒤 회의", today)
        assertEquals(Duration.ofMinutes(60), r.relativeFromNow)
    }

    @Test
    fun `두 시간 뒤 produces 120-minute duration`() {
        val r = VoiceCommandParser.parse("두 시간 뒤", today)
        assertEquals(Duration.ofMinutes(120), r.relativeFromNow)
    }

    @Test
    fun `한 시간 반 뒤 produces 90-minute duration`() {
        val r = VoiceCommandParser.parse("한 시간 반 뒤 약 먹기", today)
        assertEquals(Duration.ofMinutes(90), r.relativeFromNow)
    }

    @Test
    fun `relative time uses 후 alternate suffix`() {
        val r = VoiceCommandParser.parse("15분 후", today)
        assertEquals(Duration.ofMinutes(15), r.relativeFromNow)
    }

    @Test
    fun `relative time strips from title`() {
        val r = VoiceCommandParser.parse("10분 뒤 회의 시작 알려줘", today)
        assertEquals(Duration.ofMinutes(10), r.relativeFromNow)
        assertEquals("회의 시작", r.title)
    }

    @Test
    fun `relative time leaves time as null`() {
        // "1시간 뒤"가 "1시"로 잘못 매칭되지 않아야 함
        val r = VoiceCommandParser.parse("1시간 뒤", today)
        assertNotNull(r.relativeFromNow)
        assertNull(r.time)
    }

    @Test
    fun `no relative time when no 뒤 or 후 suffix`() {
        val r = VoiceCommandParser.parse("오후 3시 회의", today)
        assertNull(r.relativeFromNow)
    }

    @Test
    fun `깨워줘 keyword counted as filler not query`() {
        // 알람 의도이므로 절대 시간이 없으면 ADD
        val r = VoiceCommandParser.parse("아침 7시 깨워줘", today)
        assertEquals(VoiceIntent.ADD, r.intent)
    }
}
