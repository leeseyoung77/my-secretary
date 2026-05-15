package com.luxboy.mysecretary.domain.voice

import com.luxboy.mysecretary.domain.model.Event
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object VoiceResponseBuilder {

    fun build(
        date: LocalDate,
        events: List<Event>,
        titleFilter: String = "",
        today: LocalDate = LocalDate.now(),
    ): String {
        val dateLabel = relativeDateLabel(date, today)

        val filtered = if (titleFilter.isBlank()) events else {
            events.filter { it.title.contains(titleFilter, ignoreCase = true) }
        }

        if (filtered.isEmpty()) {
            return if (titleFilter.isBlank()) {
                "${dateLabel}은 일정이 없습니다."
            } else {
                "${dateLabel}에 ${titleFilter} 일정이 없습니다."
            }
        }

        val items = filtered
            .sortedBy { it.start }
            .joinToString(", ") { event ->
                val time = if (event.isAllDay) "종일" else formatKoreanTime(event.start.toLocalTime())
                "$time ${event.title}"
            }

        return "${dateLabel} 일정은 총 ${filtered.size}건입니다. $items."
    }

    private fun relativeDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        today.plusDays(2) -> "모레"
        today.minusDays(1) -> "어제"
        else -> date.format(DateTimeFormatter.ofPattern("M월 d일"))
    }

    private fun formatKoreanTime(time: LocalTime): String {
        val ampm = if (time.hour < 12) "오전" else "오후"
        val hour12 = when {
            time.hour == 0 -> 12
            time.hour > 12 -> time.hour - 12
            else -> time.hour
        }
        return if (time.minute == 0) "$ampm ${hour12}시" else "$ampm ${hour12}시 ${time.minute}분"
    }
}
