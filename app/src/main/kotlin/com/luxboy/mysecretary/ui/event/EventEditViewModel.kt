package com.luxboy.mysecretary.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxboy.mysecretary.data.repository.EventRepository
import com.luxboy.mysecretary.domain.model.Event
import com.luxboy.mysecretary.domain.recurrence.Frequency
import com.luxboy.mysecretary.domain.recurrence.RecurrenceParser
import com.luxboy.mysecretary.domain.recurrence.RecurrenceRule
import com.luxboy.mysecretary.domain.voice.VoiceCommandParser
import com.luxboy.mysecretary.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class EventEditUiState(
    val id: Long = 0L,
    val title: String = "",
    val description: String = "",
    val start: LocalDateTime = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1),
    val end: LocalDateTime = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(2),
    val isAllDay: Boolean = false,
    val reminderMinutes: List<Int> = listOf(15),
    val recurrence: RecurrenceRule? = null,
    val colorTag: Int = 0,
    val isDday: Boolean = false,
    val emoji: String? = null,
    val location: String = "",
    val naturalLanguageText: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = title.isNotBlank() && end.isAfter(start)
}

@HiltViewModel
class EventEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EventRepository,
) : ViewModel() {

    private val eventId: Long = savedStateHandle.get<Long>(Routes.ARG_EVENT_ID) ?: 0L
    private val initialDate: LocalDate? = savedStateHandle.get<Long>(Routes.ARG_DATE)
        ?.takeIf { it > 0L }
        ?.let(LocalDate::ofEpochDay)
    private val initialTime: LocalTime? = savedStateHandle.get<Long>(Routes.ARG_TIME)
        ?.takeIf { it in 0..86399 }
        ?.let { LocalTime.ofSecondOfDay(it) }
    private val initialTitle: String = savedStateHandle.get<String>(Routes.ARG_TITLE).orEmpty()

    private val _state = MutableStateFlow(buildInitialState(eventId, initialDate, initialTime, initialTitle))
    val state: StateFlow<EventEditUiState> = _state.asStateFlow()

    init {
        if (eventId != 0L) loadEvent(eventId)
    }

    private fun loadEvent(id: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val event = repository.findById(id)
            if (event != null) {
                _state.update {
                    it.copy(
                        id = event.id,
                        title = event.title,
                        description = event.description.orEmpty(),
                        start = event.start,
                        end = event.end,
                        isAllDay = event.isAllDay,
                        reminderMinutes = event.reminderMinutes,
                        recurrence = event.rrule?.let { rule -> RecurrenceParser.parse(rule) },
                        colorTag = event.colorTag,
                        isDday = event.isDday,
                        emoji = event.emoji,
                        location = event.location.orEmpty(),
                        isLoading = false,
                    )
                }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateTitle(value: String) = _state.update { it.copy(title = value) }
    fun updateDescription(value: String) = _state.update { it.copy(description = value) }
    fun updateStart(value: LocalDateTime) = _state.update {
        val newEnd = if (it.end.isBefore(value)) value.plusHours(1) else it.end
        it.copy(start = value, end = newEnd)
    }
    fun updateEnd(value: LocalDateTime) = _state.update { it.copy(end = value) }
    fun updateAllDay(value: Boolean) = _state.update { it.copy(isAllDay = value) }
    fun toggleReminderMinutes(value: Int) = _state.update { current ->
        val newList = if (value in current.reminderMinutes) {
            current.reminderMinutes - value
        } else {
            (current.reminderMinutes + value).sorted()
        }
        current.copy(reminderMinutes = newList)
    }
    fun clearReminderMinutes() = _state.update { it.copy(reminderMinutes = emptyList()) }
    fun updateLocation(value: String) = _state.update { it.copy(location = value) }
    fun updateColorTag(value: Int) = _state.update { it.copy(colorTag = value) }
    fun updateIsDday(value: Boolean) = _state.update { it.copy(isDday = value) }
    fun updateEmoji(value: String?) = _state.update { it.copy(emoji = value) }
    fun updateNaturalLanguageText(value: String) = _state.update { it.copy(naturalLanguageText = value) }

    fun applyNaturalLanguage() {
        val text = _state.value.naturalLanguageText.trim()
        if (text.isBlank()) return
        val parsed = VoiceCommandParser.parse(text)
        val current = _state.value
        val duration = java.time.Duration.between(current.start, current.end)
            .takeIf { !it.isZero && !it.isNegative } ?: java.time.Duration.ofHours(1)
        if (parsed.relativeFromNow != null) {
            val newStart = LocalDateTime.now().plus(parsed.relativeFromNow)
            _state.update {
                it.copy(
                    title = parsed.title.ifBlank { it.title.ifBlank { "알람" } },
                    start = newStart,
                    end = newStart.plus(duration),
                    reminderMinutes = listOf(0),
                )
            }
        } else {
            val newTime = parsed.time ?: current.start.toLocalTime()
            val newStart = LocalDateTime.of(parsed.date, newTime)
            _state.update {
                it.copy(
                    title = parsed.title.ifBlank { it.title },
                    start = newStart,
                    end = newStart.plus(duration),
                )
            }
        }
    }

    fun updateRecurrenceFrequency(frequency: Frequency?) = _state.update { current ->
        if (frequency == null) {
            current.copy(recurrence = null)
        } else {
            val existing = current.recurrence
            val byDays = if (frequency == Frequency.WEEKLY && existing?.frequency == Frequency.WEEKLY) {
                existing.byDays
            } else {
                emptySet()
            }
            current.copy(
                recurrence = RecurrenceRule(
                    frequency = frequency,
                    interval = 1,
                    byDays = byDays,
                    until = existing?.until,
                )
            )
        }
    }

    fun toggleRecurrenceByDay(day: DayOfWeek) = _state.update { current ->
        val rule = current.recurrence ?: return@update current
        val updated = if (day in rule.byDays) rule.byDays - day else rule.byDays + day
        current.copy(recurrence = rule.copy(byDays = updated))
    }

    fun updateRecurrenceUntil(until: LocalDate?) = _state.update { current ->
        val rule = current.recurrence ?: return@update current
        current.copy(recurrence = rule.copy(until = until))
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repository.upsert(
                Event(
                    id = s.id,
                    title = s.title.trim(),
                    description = s.description.takeIf { it.isNotBlank() },
                    start = s.start,
                    end = s.end,
                    isAllDay = s.isAllDay,
                    rrule = s.recurrence?.let { RecurrenceParser.serialize(it) },
                    colorTag = s.colorTag,
                    reminderMinutes = s.reminderMinutes,
                    isDday = s.isDday,
                    emoji = s.emoji,
                    location = s.location.trim().takeIf { it.isNotBlank() },
                )
            )
            _state.update { it.copy(isSaved = true) }
        }
    }

    fun delete() {
        val id = _state.value.id
        if (id == 0L) return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(isSaved = true) }
        }
    }

    companion object {
        private fun buildInitialState(
            eventId: Long,
            initialDate: LocalDate?,
            initialTime: LocalTime?,
            initialTitle: String,
        ): EventEditUiState {
            val baseStart = LocalDateTime.now()
                .plusHours(1)
                .withMinute(0).withSecond(0).withNano(0)
            val withDate = initialDate?.let { baseStart.with(it) } ?: baseStart
            val start = initialTime?.let { withDate.with(it) } ?: withDate
            return EventEditUiState(
                id = eventId,
                title = if (eventId == 0L) initialTitle else "",
                start = start,
                end = start.plusHours(1),
                reminderMinutes = if (eventId == 0L) listOf(15) else emptyList(),
            )
        }
    }
}
