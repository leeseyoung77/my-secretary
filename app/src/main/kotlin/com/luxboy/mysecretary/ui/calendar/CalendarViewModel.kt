package com.luxboy.mysecretary.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxboy.mysecretary.data.notification.SoundFeedbackPlayer
import com.luxboy.mysecretary.data.preferences.AppPreferences
import com.luxboy.mysecretary.data.repository.ContactRepository
import com.luxboy.mysecretary.data.repository.EventRepository
import com.luxboy.mysecretary.data.voice.VoiceCommandBus
import com.luxboy.mysecretary.domain.model.CalendarViewMode
import com.luxboy.mysecretary.domain.model.Contact
import com.luxboy.mysecretary.domain.model.Event
import com.luxboy.mysecretary.domain.voice.VoiceIntent
import com.luxboy.mysecretary.domain.voice.VoiceParseResult
import com.luxboy.mysecretary.domain.voice.VoiceResponseBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: EventRepository,
    private val contactRepository: ContactRepository,
    private val preferences: AppPreferences,
    private val soundFeedbackPlayer: SoundFeedbackPlayer,
    voiceCommandBus: VoiceCommandBus,
) : ViewModel() {

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    private val _visibleAnchor = MutableStateFlow(LocalDate.now())
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()
    val visibleAnchor: StateFlow<LocalDate> = _visibleAnchor.asStateFlow()
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val summaryViewEnabled: StateFlow<Boolean> = preferences.summaryViewEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val lunarDisplayEnabled: StateFlow<Boolean> = preferences.lunarDisplayEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            _viewMode.value = preferences.defaultCalendarView.first()
        }
        viewModelScope.launch {
            voiceCommandBus.commands.collect { parsed ->
                when (parsed.intent) {
                    VoiceIntent.ADD -> saveVoiceEvent(parsed)
                    VoiceIntent.DELETE -> findDeleteCandidates(parsed)
                    VoiceIntent.CALL -> handleVoiceCall(parsed)
                    VoiceIntent.QUERY -> handleVoiceQuery(parsed)
                }
            }
        }
    }

    /**
     * Voice ADD path: save the event immediately using the spoken text as title/description.
     * Schedule/time fields use defaults; user can edit later if needed.
     */
    fun saveVoiceEvent(parsed: VoiceParseResult) {
        viewModelScope.launch {
            val text = parsed.rawText.trim()
            val isRelative = parsed.relativeFromNow != null

            val start: LocalDateTime
            val title: String
            val reminders: List<Int>
            val confirmMessage: String

            if (isRelative) {
                start = LocalDateTime.now().plus(parsed.relativeFromNow)
                title = parsed.title.trim().ifBlank { "알람" }
                reminders = listOf(0) // fire at the exact event start time
                confirmMessage = formatRelativeConfirm(parsed.relativeFromNow)
            } else {
                val today = LocalDate.now()
                start = when {
                    parsed.time != null -> LocalDateTime.of(parsed.date, parsed.time)
                    parsed.date == today -> LocalDateTime.now()
                        .plusHours(1)
                        .withMinute(0).withSecond(0).withNano(0)
                    // 날짜만 지정된 경우 기본 오전 9시
                    else -> LocalDateTime.of(parsed.date, LocalTime.of(9, 0))
                }
                title = text.ifBlank { "음성 일정" }
                reminders = listOf(15)
                confirmMessage = formatAbsoluteConfirm(start, today)
            }

            val event = Event(
                id = 0,
                title = title,
                description = text.takeIf { it.isNotBlank() },
                start = start,
                end = start.plusHours(1),
                isAllDay = false,
                rrule = null,
                colorTag = 0,
                reminderMinutes = reminders,
            )
            repository.upsert(event)

            val soundOn = preferences.soundFeedbackEnabled.first()
            val voiceOn = preferences.voiceFeedbackEnabled.first()
            if (soundOn) soundFeedbackPlayer.play()
            if (voiceOn) _speakEvents.send(confirmMessage)
        }
    }

    private fun formatAbsoluteConfirm(start: LocalDateTime, today: LocalDate): String {
        val date = start.toLocalDate()
        val dateLabel = when (date) {
            today -> "오늘"
            today.plusDays(1) -> "내일"
            today.plusDays(2) -> "모레"
            else -> date.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN))
        }
        val time = start.toLocalTime()
        val ampm = if (time.hour < 12) "오전" else "오후"
        val hour12 = when {
            time.hour == 0 -> 12
            time.hour > 12 -> time.hour - 12
            else -> time.hour
        }
        val timeLabel = if (time.minute == 0) "$ampm ${hour12}시"
        else "$ampm ${hour12}시 ${time.minute}분"
        return "$dateLabel $timeLabel 에 등록되었습니다"
    }

    private fun formatRelativeConfirm(duration: java.time.Duration): String {
        val totalMinutes = duration.toMinutes()
        val label = when {
            totalMinutes >= 60 && totalMinutes % 60 == 0L -> "${totalMinutes / 60}시간 뒤"
            totalMinutes >= 60 -> "${totalMinutes / 60}시간 ${totalMinutes % 60}분 뒤"
            else -> "${totalMinutes}분 뒤"
        }
        return "$label 알려드릴게요"
    }

    val events: StateFlow<List<Event>> =
        combine(_viewMode, _visibleAnchor) { mode, anchor -> mode to anchor }
            .flatMapLatest { (mode, anchor) ->
                when (mode) {
                    CalendarViewMode.MONTH -> repository.observeForMonth(anchor.year, anchor.monthValue)
                    CalendarViewMode.WEEK -> repository.observeForWeek(weekStartOf(anchor))
                    CalendarViewMode.DAY -> repository.observeForDate(anchor)
                    CalendarViewMode.AGENDA -> repository.observeForDateRange(
                        anchor,
                        anchor.plusDays(AGENDA_RANGE_DAYS.toLong()),
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showPrevious() {
        val mode = _viewMode.value
        _visibleAnchor.update { current ->
            when (mode) {
                CalendarViewMode.MONTH -> current.minusMonths(1).withDayOfMonth(1)
                CalendarViewMode.WEEK -> current.minusWeeks(1)
                CalendarViewMode.DAY -> current.minusDays(1)
                CalendarViewMode.AGENDA -> current.minusDays(AGENDA_RANGE_DAYS.toLong())
            }
        }
        when (mode) {
            CalendarViewMode.WEEK -> _selectedDate.update { it.minusWeeks(1) }
            CalendarViewMode.DAY -> _selectedDate.update { it.minusDays(1) }
            else -> Unit
        }
    }

    fun showNext() {
        val mode = _viewMode.value
        _visibleAnchor.update { current ->
            when (mode) {
                CalendarViewMode.MONTH -> current.plusMonths(1).withDayOfMonth(1)
                CalendarViewMode.WEEK -> current.plusWeeks(1)
                CalendarViewMode.DAY -> current.plusDays(1)
                CalendarViewMode.AGENDA -> current.plusDays(AGENDA_RANGE_DAYS.toLong())
            }
        }
        when (mode) {
            CalendarViewMode.WEEK -> _selectedDate.update { it.plusWeeks(1) }
            CalendarViewMode.DAY -> _selectedDate.update { it.plusDays(1) }
            else -> Unit
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _visibleAnchor.value = date
    }

    fun setViewMode(mode: CalendarViewMode) {
        if (_viewMode.value != mode) {
            _viewMode.value = mode
            _visibleAnchor.value = _selectedDate.value
        }
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        _selectedDate.value = today
        _visibleAnchor.value = today
    }

    private val _voiceDeleteState = MutableStateFlow<VoiceDeleteState>(VoiceDeleteState.Idle)
    val voiceDeleteState: StateFlow<VoiceDeleteState> = _voiceDeleteState.asStateFlow()

    fun findDeleteCandidates(parsed: VoiceParseResult) {
        viewModelScope.launch {
            val titleKeyword = parsed.title.trim()
            val candidates = repository.observeForDate(parsed.date).first()
                .let { events ->
                    if (titleKeyword.isBlank()) events
                    else events.filter { it.title.contains(titleKeyword, ignoreCase = true) }
                }
            val dateLabel = parsed.date.format(
                DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN),
            )
            _voiceDeleteState.value = if (candidates.isEmpty()) {
                VoiceDeleteState.NotFound(dateLabel, titleKeyword)
            } else {
                VoiceDeleteState.Candidates(dateLabel, titleKeyword, candidates)
            }
        }
    }

    fun confirmDelete(eventId: Long) {
        viewModelScope.launch {
            repository.delete(eventId)
            _voiceDeleteState.value = VoiceDeleteState.Idle
        }
    }

    fun dismissVoiceDelete() {
        _voiceDeleteState.value = VoiceDeleteState.Idle
    }

    private val _speakEvents = Channel<String>(capacity = Channel.BUFFERED)
    val speakEvents: Flow<String> = _speakEvents.receiveAsFlow()

    fun handleVoiceQuery(parsed: VoiceParseResult) {
        viewModelScope.launch {
            val events = repository.observeForDate(parsed.date).first()
            val response = VoiceResponseBuilder.build(
                date = parsed.date,
                events = events,
                titleFilter = parsed.title,
            )
            _speakEvents.send(response)
        }
    }

    private val _voiceCallState = MutableStateFlow<VoiceCallState>(VoiceCallState.Idle)
    val voiceCallState: StateFlow<VoiceCallState> = _voiceCallState.asStateFlow()

    private val _callActions = Channel<CallAction>(capacity = Channel.BUFFERED)
    val callActions: Flow<CallAction> = _callActions.receiveAsFlow()

    fun handleVoiceCall(parsed: VoiceParseResult) {
        viewModelScope.launch {
            val name = parsed.title.trim()
            if (name.isBlank()) {
                _speakEvents.send("누구에게 전화 걸까요?")
                return@launch
            }
            val matches = contactRepository.findByName(name)
            when {
                matches.isEmpty() -> {
                    _voiceCallState.value = VoiceCallState.NotFound(name)
                    _speakEvents.send("${name} 연락처를 찾을 수 없습니다")
                }
                matches.size == 1 -> {
                    val target = matches[0]
                    _speakEvents.send("${target.name}에게 전화를 겁니다")
                    _callActions.send(CallAction(target.phoneNumber, target.name))
                }
                else -> {
                    _voiceCallState.value = VoiceCallState.Choose(name, matches)
                }
            }
        }
    }

    fun confirmCall(contact: Contact) {
        viewModelScope.launch {
            _voiceCallState.value = VoiceCallState.Idle
            _callActions.send(CallAction(contact.phoneNumber, contact.name))
        }
    }

    fun dismissVoiceCall() {
        _voiceCallState.value = VoiceCallState.Idle
    }

    companion object {
        const val AGENDA_RANGE_DAYS = 30

        fun weekStartOf(date: LocalDate): LocalDate {
            val daysFromSunday = date.dayOfWeek.value % 7
            return date.minusDays(daysFromSunday.toLong())
        }
    }
}

sealed interface VoiceDeleteState {
    data object Idle : VoiceDeleteState
    data class NotFound(val dateLabel: String, val keyword: String) : VoiceDeleteState
    data class Candidates(
        val dateLabel: String,
        val keyword: String,
        val events: List<Event>,
    ) : VoiceDeleteState
}

sealed interface VoiceCallState {
    data object Idle : VoiceCallState
    data class NotFound(val name: String) : VoiceCallState
    data class Choose(val name: String, val contacts: List<Contact>) : VoiceCallState
}

data class CallAction(val phoneNumber: String, val displayName: String)
