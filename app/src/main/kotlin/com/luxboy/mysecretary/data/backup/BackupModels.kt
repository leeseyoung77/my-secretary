package com.luxboy.mysecretary.data.backup

import com.luxboy.mysecretary.data.local.EventEntity
import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val events: List<BackupEvent>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupEvent(
    val title: String,
    val description: String? = null,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isAllDay: Boolean,
    val rrule: String? = null,
    val colorTag: Int = 0,
    val notificationMinutesBefore: Int? = null, // legacy
    val reminderMinutes: List<Int>? = null,
    val isDday: Boolean = false,
    val emoji: String? = null,
    val location: String? = null,
)

fun EventEntity.toBackup(): BackupEvent = BackupEvent(
    title = title,
    description = description,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    isAllDay = isAllDay,
    rrule = rrule,
    colorTag = colorTag,
    notificationMinutesBefore = notificationMinutesBefore,
    reminderMinutes = reminderMinutesCsv
        ?.split(',')
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.isNotEmpty() },
    isDday = isDday,
    emoji = emoji,
    location = location,
)

fun BackupEvent.toEntity(now: Long): EventEntity = EventEntity(
    id = 0,
    title = title,
    description = description,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    isAllDay = isAllDay,
    rrule = rrule,
    colorTag = colorTag,
    notificationMinutesBefore = null,
    createdAt = now,
    updatedAt = now,
    isDday = isDday,
    emoji = emoji,
    reminderMinutesCsv = (reminderMinutes ?: listOfNotNull(notificationMinutesBefore))
        .takeIf { it.isNotEmpty() }
        ?.joinToString(","),
    location = location,
)
