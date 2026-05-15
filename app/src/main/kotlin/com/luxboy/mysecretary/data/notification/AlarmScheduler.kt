package com.luxboy.mysecretary.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.luxboy.mysecretary.domain.model.Event
import com.luxboy.mysecretary.domain.recurrence.RecurrenceExpander
import com.luxboy.mysecretary.domain.recurrence.RecurrenceParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-reminder alarm scheduling.
 *
 * Each event can have up to [MAX_REMINDERS_PER_EVENT] reminders. We map each reminder
 * to a PendingIntent requestCode derived from the event id + reminder index, so that
 * cancelling and re-scheduling per event remains deterministic.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Cancels existing alarms for [event] and schedules new ones for the next-future occurrence
     * (taking recurrence into account) with all the event's reminders.
     */
    fun scheduleEventOrNextOccurrence(event: Event) {
        cancel(event.id)
        if (event.reminderMinutes.isEmpty()) return

        val occurrenceStart = nextOccurrenceStartAtOrAfterNow(event) ?: return
        val duration = Duration.between(event.start, event.end)
        val occurrence = event.copy(
            start = occurrenceStart,
            end = occurrenceStart.plus(duration),
        )
        event.reminderMinutes.distinct().take(MAX_REMINDERS_PER_EVENT)
            .forEachIndexed { index, minutes ->
                scheduleOne(occurrence, minutes, index)
            }
    }

    fun cancel(eventId: Long) {
        repeat(MAX_REMINDERS_PER_EVENT) { idx ->
            val pi = PendingIntent.getBroadcast(
                context,
                requestCodeOf(eventId, idx),
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            pi?.let { alarmManager.cancel(it) }
        }
    }

    private fun nextOccurrenceStartAtOrAfterNow(event: Event): LocalDateTime? {
        val now = LocalDateTime.now()
        if (event.rrule == null) {
            return event.start.takeIf { !it.isBefore(now) }
        }
        val rule = RecurrenceParser.parse(event.rrule) ?: return null
        return RecurrenceExpander.nextOccurrenceAtOrAfter(event.start, rule, now)
    }

    private fun scheduleOne(event: Event, minutesBefore: Int, reminderIndex: Int) {
        val triggerAtMillis = event.start
            .minusMinutes(minutesBefore.toLong())
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val pendingIntent = buildPendingIntent(event, minutesBefore, reminderIndex)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun buildPendingIntent(event: Event, minutesBefore: Int, reminderIndex: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(AlarmReceiver.EXTRA_TITLE, event.title)
            putExtra(AlarmReceiver.EXTRA_BODY, formatBody(event, minutesBefore))
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeOf(event.id, reminderIndex),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCodeOf(eventId: Long, reminderIndex: Int): Int {
        return eventId.toInt() * MAX_REMINDERS_PER_EVENT + reminderIndex
    }

    private fun formatBody(event: Event, minutesBefore: Int): String {
        val whenLabel = if (event.isAllDay) "하루 종일" else {
            val fmt = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
            event.start.format(fmt)
        }
        val leadIn = when {
            minutesBefore == 0 -> "지금"
            minutesBefore < 60 -> "${minutesBefore}분 전"
            minutesBefore % (24 * 60) == 0 -> "${minutesBefore / (24 * 60)}일 전"
            minutesBefore % 60 == 0 -> "${minutesBefore / 60}시간 전"
            else -> "${minutesBefore}분 전"
        }
        return "$whenLabel · $leadIn 알림"
    }

    companion object {
        private const val MAX_REMINDERS_PER_EVENT = 8
    }
}
