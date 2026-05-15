package com.luxboy.mysecretary.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val byDays: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
)
