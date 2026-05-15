package com.luxboy.mysecretary.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.luxboy.mysecretary.data.backup.BackupManager
import com.luxboy.mysecretary.data.backup.ImportMode
import com.luxboy.mysecretary.data.calendar.SystemCalendarImporter
import com.luxboy.mysecretary.data.calendar.SystemCalendarSource
import com.luxboy.mysecretary.data.preferences.AppPreferences
import com.luxboy.mysecretary.data.widget.MySecretaryWidget
import com.luxboy.mysecretary.domain.model.CalendarViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupMessage {
    data class ExportSuccess(val count: Int) : BackupMessage
    data class ImportSuccess(val count: Int) : BackupMessage
    data class SystemCalendarImported(val imported: Int, val skipped: Int) : BackupMessage
    data class Error(val message: String) : BackupMessage
}

sealed interface CalendarSelectionState {
    data object Idle : CalendarSelectionState
    data object Loading : CalendarSelectionState
    data class Selecting(
        val calendars: List<SystemCalendarSource>,
        val selected: Set<Long>,
    ) : CalendarSelectionState
}

data class SettingsUiState(
    val isWorking: Boolean = false,
    val message: BackupMessage? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val preferences: AppPreferences,
    private val systemCalendarImporter: SystemCalendarImporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val defaultView: StateFlow<CalendarViewMode> = preferences.defaultCalendarView
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarViewMode.MONTH)

    val wakeWordEnabled: StateFlow<Boolean> = preferences.wakeWordEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val summaryViewEnabled: StateFlow<Boolean> = preferences.summaryViewEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val voiceFeedbackEnabled: StateFlow<Boolean> = preferences.voiceFeedbackEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val soundFeedbackEnabled: StateFlow<Boolean> = preferences.soundFeedbackEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val lunarDisplayEnabled: StateFlow<Boolean> = preferences.lunarDisplayEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val widgetOpacity: StateFlow<Float> = preferences.widgetOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.8f)

    fun setDefaultView(mode: CalendarViewMode) {
        viewModelScope.launch { preferences.setDefaultCalendarView(mode) }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setWakeWordEnabled(enabled) }
    }

    fun setSummaryViewEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setSummaryViewEnabled(enabled) }
    }

    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setVoiceFeedbackEnabled(enabled) }
    }

    fun setSoundFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setSoundFeedbackEnabled(enabled) }
    }

    fun setLunarDisplayEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setLunarDisplayEnabled(enabled) }
    }

    fun setWidgetOpacity(value: Float) {
        viewModelScope.launch {
            preferences.setWidgetOpacity(value)
            MySecretaryWidget.refresh(context)
        }
    }

    suspend fun currentWakeWordEnabled(): Boolean = preferences.wakeWordEnabled.first()

    fun export(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            val result = backupManager.exportTo(uri)
            _state.update {
                it.copy(
                    isWorking = false,
                    message = result.fold(
                        onSuccess = { count -> BackupMessage.ExportSuccess(count) },
                        onFailure = { e -> BackupMessage.Error(e.message ?: "내보내기 실패") },
                    )
                )
            }
        }
    }

    fun import(uri: Uri, mode: ImportMode) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            val result = backupManager.import(uri, mode)
            _state.update {
                it.copy(
                    isWorking = false,
                    message = result.fold(
                        onSuccess = { count -> BackupMessage.ImportSuccess(count) },
                        onFailure = { e -> BackupMessage.Error(e.message ?: "가져오기 실패") },
                    )
                )
            }
        }
    }

    private val _selectionState = MutableStateFlow<CalendarSelectionState>(CalendarSelectionState.Idle)
    val selectionState: StateFlow<CalendarSelectionState> = _selectionState.asStateFlow()

    fun startCalendarSelection() {
        viewModelScope.launch {
            _selectionState.value = CalendarSelectionState.Loading
            val result = systemCalendarImporter.listCalendars()
            result.fold(
                onSuccess = { calendars ->
                    if (calendars.isEmpty()) {
                        _selectionState.value = CalendarSelectionState.Idle
                        _state.update {
                            it.copy(message = BackupMessage.Error("기기에서 사용할 수 있는 캘린더가 없습니다"))
                        }
                    } else {
                        _selectionState.value = CalendarSelectionState.Selecting(
                            calendars = calendars,
                            selected = calendars.map { it.id }.toSet(),
                        )
                    }
                },
                onFailure = { e ->
                    _selectionState.value = CalendarSelectionState.Idle
                    _state.update {
                        it.copy(message = BackupMessage.Error(e.message ?: "캘린더 목록을 가져올 수 없습니다"))
                    }
                },
            )
        }
    }

    fun toggleCalendar(id: Long) {
        _selectionState.update { current ->
            if (current !is CalendarSelectionState.Selecting) return@update current
            val newSelected = if (id in current.selected) current.selected - id else current.selected + id
            current.copy(selected = newSelected)
        }
    }

    fun setAllCalendars(checked: Boolean) {
        _selectionState.update { current ->
            if (current !is CalendarSelectionState.Selecting) return@update current
            current.copy(
                selected = if (checked) current.calendars.map { it.id }.toSet() else emptySet(),
            )
        }
    }

    fun confirmCalendarImport() {
        val current = _selectionState.value as? CalendarSelectionState.Selecting ?: return
        val selectedIds = current.selected
        _selectionState.value = CalendarSelectionState.Idle
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            val result = systemCalendarImporter.import(selectedIds)
            _state.update {
                it.copy(
                    isWorking = false,
                    message = result.fold(
                        onSuccess = { r -> BackupMessage.SystemCalendarImported(r.imported, r.skipped) },
                        onFailure = { e -> BackupMessage.Error(e.message ?: "캘린더 가져오기 실패") },
                    )
                )
            }
        }
    }

    fun cancelCalendarSelection() {
        _selectionState.value = CalendarSelectionState.Idle
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
