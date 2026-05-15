package com.luxboy.mysecretary.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.luxboy.mysecretary.domain.model.Event
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String?,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isAllDay: Boolean,
    val rrule: String?,
    val colorTag: Int,
    val notificationMinutesBefore: Int?, // legacy, replaced by reminderMinutesCsv
    val createdAt: Long,
    val updatedAt: Long,
    val isDday: Boolean = false,
    val emoji: String? = null,
    val reminderMinutesCsv: String? = null,
    val location: String? = null,
)

fun EventEntity.toDomain(zoneId: ZoneId = ZoneId.systemDefault()): Event {
    val reminders = reminderMinutesCsv
        ?.split(',')
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(notificationMinutesBefore)
    return Event(
        id = id,
        title = title,
        description = description,
        start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startEpochMillis), zoneId),
        end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endEpochMillis), zoneId),
        isAllDay = isAllDay,
        rrule = rrule,
        colorTag = colorTag,
        reminderMinutes = reminders,
        isDday = isDday,
        emoji = emoji,
        location = location,
    )
}

fun Event.toEntity(
    now: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): EventEntity = EventEntity(
    id = id,
    title = title,
    description = description,
    startEpochMillis = start.atZone(zoneId).toInstant().toEpochMilli(),
    endEpochMillis = end.atZone(zoneId).toInstant().toEpochMilli(),
    isAllDay = isAllDay,
    rrule = rrule,
    colorTag = colorTag,
    notificationMinutesBefore = null, // legacy column, no longer written
    createdAt = now,
    updatedAt = now,
    isDday = isDday,
    emoji = emoji,
    reminderMinutesCsv = reminderMinutes
        .takeIf { it.isNotEmpty() }
        ?.joinToString(","),
    location = location,
)
