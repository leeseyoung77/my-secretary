package com.luxboy.mysecretary.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luxboy.mysecretary.R
import com.luxboy.mysecretary.data.backup.ImportMode
import com.luxboy.mysecretary.data.voice.WakeWordService
import com.luxboy.mysecretary.domain.model.CalendarViewMode
import com.luxboy.mysecretary.ui.components.AppAlertDialog
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenContacts: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val defaultView by viewModel.defaultView.collectAsStateWithLifecycle()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsStateWithLifecycle()
    val summaryViewEnabled by viewModel.summaryViewEnabled.collectAsStateWithLifecycle()
    val lunarDisplayEnabled by viewModel.lunarDisplayEnabled.collectAsStateWithLifecycle()
    val widgetOpacity by viewModel.widgetOpacity.collectAsStateWithLifecycle()
    val voiceFeedbackEnabled by viewModel.voiceFeedbackEnabled.collectAsStateWithLifecycle()
    val soundFeedbackEnabled by viewModel.soundFeedbackEnabled.collectAsStateWithLifecycle()
    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setWakeWordEnabled(true)
            WakeWordService.start(context)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.wake_word_permission_denied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startCalendarSelection()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.system_calendar_permission_denied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingImportUri = uri }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        val text = when (msg) {
            is BackupMessage.ExportSuccess -> "백업 완료 (${msg.count}건)"
            is BackupMessage.ImportSuccess -> "가져오기 완료 (${msg.count}건)"
            is BackupMessage.SystemCalendarImported ->
                "시스템 캘린더 가져오기 완료 (${msg.imported}건 추가, ${msg.skipped}건 중복)"
            is BackupMessage.Error -> "오류: ${msg.message}"
        }
        scope.launch { snackbarHostState.showSnackbar(text) }
        viewModel.clearMessage()
    }

    CalendarSelectionDialog(
        state = selectionState,
        onToggle = viewModel::toggleCalendar,
        onSelectAll = viewModel::setAllCalendars,
        onConfirm = viewModel::confirmCalendarImport,
        onDismiss = viewModel::cancelCalendarSelection,
    )

    pendingImportUri?.let { uri ->
        AppAlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.import(uri, ImportMode.REPLACE)
                    pendingImportUri = null
                }) { Text(stringResource(R.string.import_replace)) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        viewModel.import(uri, ImportMode.MERGE)
                        pendingImportUri = null
                    }) { Text(stringResource(R.string.import_merge)) }
                    TextButton(onClick = { pendingImportUri = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.section_default_view),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.default_view_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = defaultView == CalendarViewMode.MONTH,
                    onClick = { viewModel.setDefaultView(CalendarViewMode.MONTH) },
                    label = { Text(stringResource(R.string.view_month)) },
                )
                FilterChip(
                    selected = defaultView == CalendarViewMode.WEEK,
                    onClick = { viewModel.setDefaultView(CalendarViewMode.WEEK) },
                    label = { Text(stringResource(R.string.view_week)) },
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.section_summary_view),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.summary_view_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.summary_view_toggle_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = summaryViewEnabled,
                    onCheckedChange = { viewModel.setSummaryViewEnabled(it) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.lunar_display_toggle_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = lunarDisplayEnabled,
                    onCheckedChange = { viewModel.setLunarDisplayEnabled(it) },
                )
            }

            HorizontalDivider()

            WidgetOpacitySection(
                value = widgetOpacity,
                onChangeFinished = viewModel::setWidgetOpacity,
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.section_register_feedback),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.register_feedback_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.register_feedback_voice_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = voiceFeedbackEnabled,
                    onCheckedChange = viewModel::setVoiceFeedbackEnabled,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.register_feedback_sound_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = soundFeedbackEnabled,
                    onCheckedChange = viewModel::setSoundFeedbackEnabled,
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.section_wake_word),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.wake_word_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.wake_word_toggle_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = wakeWordEnabled,
                    onCheckedChange = { wanted ->
                        if (wanted) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.setWakeWordEnabled(true)
                                WakeWordService.start(context)
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            viewModel.setWakeWordEnabled(false)
                            WakeWordService.stop(context)
                        }
                    },
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.section_system_calendar),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.system_calendar_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CALENDAR,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.startCalendarSelection()
                    } else {
                        calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                },
                enabled = !state.isWorking && selectionState == CalendarSelectionState.Idle,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_choose_calendars)) }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.section_contacts),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedButton(
                onClick = onOpenContacts,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_manage_contacts)) }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.section_backup),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.backup_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    val name = "my-secretary-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
                    exportLauncher.launch(name)
                },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_export)) }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_import)) }

            if (state.isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CalendarSelectionDialog(
    state: CalendarSelectionState,
    onToggle: (Long) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        CalendarSelectionState.Idle -> Unit
        CalendarSelectionState.Loading -> AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.calendar_select_title)) },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        is CalendarSelectionState.Selecting -> AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.calendar_select_title)) },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onSelectAll(true) }) {
                            Text(stringResource(R.string.calendar_select_all))
                        }
                        TextButton(onClick = { onSelectAll(false) }) {
                            Text(stringResource(R.string.calendar_select_none))
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        items(state.calendars, key = { it.id }) { cal ->
                            CalendarRow(
                                calendar = cal,
                                checked = cal.id in state.selected,
                                onToggle = { onToggle(cal.id) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    enabled = state.selected.isNotEmpty(),
                ) {
                    Text(
                        stringResource(R.string.calendar_select_import, state.selected.size)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CalendarRow(
    calendar: com.luxboy.mysecretary.data.calendar.SystemCalendarSource,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    if (calendar.color != 0) Color(calendar.color or 0xFF000000.toInt())
                    else MaterialTheme.colorScheme.primary
                ),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = calendar.displayName, style = MaterialTheme.typography.bodyLarge)
            calendar.accountName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WidgetOpacitySection(
    value: Float,
    onChangeFinished: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.section_widget_opacity),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.widget_opacity_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.widget_opacity_label),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${(sliderValue * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChangeFinished(sliderValue) },
            valueRange = 0.3f..1f,
        )
    }
}
