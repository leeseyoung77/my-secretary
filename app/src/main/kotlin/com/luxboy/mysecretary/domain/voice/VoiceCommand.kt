package com.luxboy.mysecretary.domain.voice

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

enum class VoiceIntent { ADD, DELETE, QUERY, CALL }

data class VoiceParseResult(
    val intent: VoiceIntent,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val rawText: String,
    /** Set when user said "10분 뒤", "1시간 뒤", "한 시간 반 뒤" etc. */
    val relativeFromNow: Duration? = null,
)
