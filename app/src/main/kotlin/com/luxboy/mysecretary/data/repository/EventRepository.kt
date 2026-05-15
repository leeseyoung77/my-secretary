package com.luxboy.mysecretary.data.repository

import android.content.Context
import com.luxboy.mysecretary.data.local.EventDao
import com.luxboy.mysecretary.data.local.toDomain
import com.luxboy.mysecretary.data.local.toEntity
import com.luxboy.mysecretary.data.notification.AlarmScheduler
import com.luxboy.mysecretary.data.widget.MySecretaryWidget
import com.luxboy.mysecretary.domain.model.Event
import com.luxboy.mysecretary.domain.recurrence.RecurrenceExpander
import com.luxboy.mysecretary.domain.recurrence.RecurrenceParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<Event>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForDate(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<Event>> =
        observeForRange(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), zoneId)

    fun observeForMonth(year: Int, month: Int, zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<Event>> {
        val firstDay = LocalDate.of(year, month, 1)
        return observeForRange(firstDay.atStartOfDay(), firstDay.plusMonths(1).atStartOfDay(), zoneId)
    }

    fun observeForWeek(weekStart: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<Event>> =
        observeForRange(weekStart.atStartOfDay(), weekStart.plusWeeks(1).atStartOfDay(), zoneId)

    fun observeForDateRange(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<Event>> = observeForRange(
        startDate.atStartOfDay(),
        endDateExclusive.atStartOfDay(),
        zoneId,
    )

    private fun observeForRange(
        rangeStart: LocalDateTime,
        rangeEndExclusive: LocalDateTime,
        zoneId: ZoneId,
    ): Flow<List<Event>> {
        val startMillis = rangeStart.atZone(zoneId).toInstant().toEpochMilli()
        val endMillis = rangeEndExclusive.atZone(zoneId).toInstant().toEpochMilli()

        return combine(
            dao.observeNonRecurringInRange(startMillis, endMillis),
            dao.observeAllRecurring(),
        ) { regular, recurring ->
            val expanded = recurring.flatMap { entity ->
                val base = entity.toDomain(zoneId)
                val rule = base.rrule?.let { RecurrenceParser.parse(it) } ?: return@flatMap emptyList()
                val duration = Duration.between(base.start, base.end)
                RecurrenceExpander.occurrencesInRange(
                    baseStart = base.start,
                    rule = rule,
                    rangeStart = rangeStart,
                    rangeEndExclusive = rangeEndExclusive,
                ).map { occurrenceStart ->
                    base.copy(start = occurrenceStart, end = occurrenceStart.plus(duration))
                }
            }
            (regular.map { it.toDomain(zoneId) } + expanded).sortedBy { it.start }
        }
    }

    suspend fun findById(id: Long): Event? = dao.findById(id)?.toDomain()

    suspend fun search(keyword: String): List<Event> {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return emptyList()
        return dao.search("%$trimmed%").map { it.toDomain() }
    }

    suspend fun getUpcoming(nowMillis: Long = System.currentTimeMillis()): List<Event> =
        dao.getUpcoming(nowMillis).map { it.toDomain() }

    suspend fun getAllRecurring(): List<Event> =
        dao.getAllRecurring().map { it.toDomain() }

    suspend fun upsert(event: Event): Long {
        val entity = event.toEntity()
        val id = if (event.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            event.id
        }
        val saved = event.copy(id = id)
        alarmScheduler.cancel(id)
        alarmScheduler.scheduleEventOrNextOccurrence(saved)
        MySecretaryWidget.refresh(context)
        return id
    }

    suspend fun delete(id: Long) {
        alarmScheduler.cancel(id)
        dao.deleteById(id)
        MySecretaryWidget.refresh(context)
    }
}
