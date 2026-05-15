package com.luxboy.mysecretary.data.calendar

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.luxboy.mysecretary.data.local.EventDao
import com.luxboy.mysecretary.data.local.EventEntity
import com.luxboy.mysecretary.data.local.toDomain
import com.luxboy.mysecretary.data.widget.MySecretaryWidget
import com.luxboy.mysecretary.domain.model.Event
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class SystemCalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String?,
    val color: Int,
)

data class SystemCalendarImportResult(
    val imported: Int,
    val skipped: Int,
)

@Singleton
class SystemCalendarImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: EventDao,
) {

    suspend fun listCalendars(): Result<List<SystemCalendarSource>> = withContext(Dispatchers.IO) {
        runCatching {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.VISIBLE,
            )
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            ) ?: throw IOException("캘린더 목록을 읽을 수 없습니다")

            val list = mutableListOf<SystemCalendarSource>()
            cursor.use { c ->
                val idIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIdx = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val colorIdx = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                while (c.moveToNext()) {
                    list.add(
                        SystemCalendarSource(
                            id = c.getLong(idIdx),
                            displayName = c.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: "(이름 없음)",
                            accountName = if (accountIdx >= 0 && !c.isNull(accountIdx)) c.getString(accountIdx) else null,
                            color = if (colorIdx >= 0 && !c.isNull(colorIdx)) c.getInt(colorIdx) else 0,
                        )
                    )
                }
            }
            list
        }
    }

    suspend fun import(
        calendarIds: Set<Long>,
        rangeStart: LocalDate = LocalDate.now().minusMonths(1),
        rangeEnd: LocalDate = LocalDate.now().plusMonths(6),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Result<SystemCalendarImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (calendarIds.isEmpty()) return@runCatching SystemCalendarImportResult(0, 0)

            val startMillis = rangeStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endMillis = rangeEnd.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.DELETED,
                CalendarContract.Events.CALENDAR_ID,
            )

            val placeholders = calendarIds.joinToString(",") { "?" }
            val selection = "${CalendarContract.Events.DTSTART} < ? AND " +
                "((${CalendarContract.Events.DTEND} > ?) OR " +
                "(${CalendarContract.Events.RRULE} IS NOT NULL)) AND " +
                "${CalendarContract.Events.DELETED} = 0 AND " +
                "${CalendarContract.Events.TITLE} IS NOT NULL AND " +
                "${CalendarContract.Events.CALENDAR_ID} IN ($placeholders)"
            val selectionArgs = (listOf(endMillis.toString(), startMillis.toString()) +
                calendarIds.map { it.toString() }).toTypedArray()

            val cursor: Cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC",
            ) ?: throw IOException("캘린더를 읽을 수 없습니다")

            val existing = dao.getAll().map { it.toDomain() }
            val existingKeys = existing.map { it.duplicateKey() }.toMutableSet()

            var imported = 0
            var skipped = 0

            cursor.use { c ->
                val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val dtstartIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val dtendIdx = c.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayIdx = c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val rruleIdx = c.getColumnIndex(CalendarContract.Events.RRULE)

                while (c.moveToNext()) {
                    val title = c.getString(titleIdx)?.takeIf { it.isNotBlank() } ?: continue
                    val description = descIdx.takeIf { it >= 0 && !c.isNull(it) }
                        ?.let { c.getString(it) }
                    val dtstart = c.getLong(dtstartIdx)
                    val dtend = (dtendIdx.takeIf { it >= 0 && !c.isNull(it) }
                        ?.let { c.getLong(it) }) ?: (dtstart + 3_600_000L)
                    val isAllDay = c.getInt(allDayIdx) == 1
                    val rrule = rruleIdx.takeIf { it >= 0 && !c.isNull(it) }
                        ?.let { c.getString(it) }

                    val key = "$title|$dtstart|$dtend|${rrule.orEmpty()}"
                    if (key in existingKeys) {
                        skipped++
                        continue
                    }
                    existingKeys.add(key)

                    val now = System.currentTimeMillis()
                    val entity = EventEntity(
                        id = 0,
                        title = title,
                        description = description,
                        startEpochMillis = dtstart,
                        endEpochMillis = dtend,
                        isAllDay = isAllDay,
                        rrule = rrule,
                        colorTag = 0,
                        notificationMinutesBefore = null,
                        createdAt = now,
                        updatedAt = now,
                        reminderMinutesCsv = null,
                        location = null,
                    )
                    dao.insert(entity)
                    imported++
                }
            }

            MySecretaryWidget.refresh(context)
            SystemCalendarImportResult(imported, skipped)
        }
    }

    private fun Event.duplicateKey(): String {
        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return "$title|$startMillis|$endMillis|${rrule.orEmpty()}"
    }
}
