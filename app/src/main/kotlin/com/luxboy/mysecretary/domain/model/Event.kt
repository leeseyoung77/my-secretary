package com.luxboy.mysecretary.domain.model

import java.time.LocalDateTime

data class Event(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean = false,
    val rrule: String? = null,
    val colorTag: Int = 0,
    val reminderMinutes: List<Int> = emptyList(),
    val isDday: Boolean = false,
    val emoji: String? = null,
    val location: String? = null,
)
