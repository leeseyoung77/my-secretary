package com.luxboy.mysecretary.domain.model

import java.time.LocalDate
import java.time.LocalTime

val Event.firstDay: LocalDate
    get() = start.toLocalDate()

/**
 * Inclusive last day. Events ending exactly at next-day midnight are treated as ending the
 * previous day (Google Calendar convention) so an event 5/1 00:00 → 5/2 00:00 spans 5/1 only.
 */
val Event.lastDay: LocalDate
    get() {
        val endDate = end.toLocalDate()
        val endTime = end.toLocalTime()
        return if (endTime == LocalTime.MIDNIGHT && endDate.isAfter(firstDay)) {
            endDate.minusDays(1)
        } else {
            endDate
        }
    }

val Event.isMultiDay: Boolean
    get() = firstDay != lastDay

fun Event.overlapsDay(date: LocalDate): Boolean =
    !firstDay.isAfter(date) && !lastDay.isBefore(date)

fun Event.overlapsRange(rangeStart: LocalDate, rangeEndInclusive: LocalDate): Boolean =
    !firstDay.isAfter(rangeEndInclusive) && !lastDay.isBefore(rangeStart)
