package com.luxboy.mysecretary.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luxboy.mysecretary.R
import com.luxboy.mysecretary.domain.recurrence.Frequency
import com.luxboy.mysecretary.domain.recurrence.RecurrenceRule
import com.luxboy.mysecretary.ui.theme.EventCategoryPalette
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class PickerTarget { StartDate, StartTime, EndDate, EndTime, UntilDate }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    onDone: () -> Unit,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.title_event_new else R.string.title_event_edit
                        )
                    )
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                    TextButton(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NaturalLanguageInput(
                value = state.naturalLanguageText,
                onValueChange = viewModel::updateNaturalLanguageText,
                onApply = viewModel::applyNaturalLanguage,
            )

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                label = { Text(stringResource(R.string.label_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::updateDescription,
                label = { Text(stringResource(R.string.label_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.label_all_day),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = state.isAllDay, onCheckedChange = viewModel::updateAllDay)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.label_dday),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = state.isDday, onCheckedChange = viewModel::updateIsDday)
            }

            DateTimeRow(
                label = stringResource(R.string.label_start),
                dateTime = state.start,
                showTime = !state.isAllDay,
                onDateClick = { pickerTarget = PickerTarget.StartDate },
                onTimeClick = { pickerTarget = PickerTarget.StartTime },
            )
            DateTimeRow(
                label = stringResource(R.string.label_end),
                dateTime = state.end,
                showTime = !state.isAllDay,
                onDateClick = { pickerTarget = PickerTarget.EndDate },
                onTimeClick = { pickerTarget = PickerTarget.EndTime },
            )

            NotificationRow(
                selectedMinutes = state.reminderMinutes,
                onToggle = viewModel::toggleReminderMinutes,
                onClear = viewModel::clearReminderMinutes,
            )

            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::updateLocation,
                label = { Text(stringResource(R.string.label_location)) },
                placeholder = { Text(stringResource(R.string.location_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryRow(
                selectedTag = state.colorTag,
                onSelect = viewModel::updateColorTag,
            )

            EmojiRow(
                selected = state.emoji,
                onSelect = viewModel::updateEmoji,
            )

            RecurrenceSection(
                rule = state.recurrence,
                onFrequencyChange = viewModel::updateRecurrenceFrequency,
                onToggleByDay = viewModel::toggleRecurrenceByDay,
                onClickUntil = { pickerTarget = PickerTarget.UntilDate },
                onClearUntil = { viewModel.updateRecurrenceUntil(null) },
            )
        }
    }

    when (pickerTarget) {
        PickerTarget.StartDate -> DatePickerSheet(
            initial = state.start.toLocalDate(),
            onDismiss = { pickerTarget = null },
            onConfirm = { date ->
                viewModel.updateStart(state.start.with(date))
                pickerTarget = null
            },
        )
        PickerTarget.EndDate -> DatePickerSheet(
            initial = state.end.toLocalDate(),
            onDismiss = { pickerTarget = null },
            onConfirm = { date ->
                viewModel.updateEnd(state.end.with(date))
                pickerTarget = null
            },
        )
        PickerTarget.StartTime -> TimePickerSheet(
            initial = state.start.toLocalTime(),
            onDismiss = { pickerTarget = null },
            onConfirm = { time ->
                viewModel.updateStart(state.start.with(time))
                pickerTarget = null
            },
        )
        PickerTarget.EndTime -> TimePickerSheet(
            initial = state.end.toLocalTime(),
            onDismiss = { pickerTarget = null },
            onConfirm = { time ->
                viewModel.updateEnd(state.end.with(time))
                pickerTarget = null
            },
        )
        PickerTarget.UntilDate -> DatePickerSheet(
            initial = state.recurrence?.until ?: state.start.toLocalDate().plusMonths(1),
            onDismiss = { pickerTarget = null },
            onConfirm = { date ->
                viewModel.updateRecurrenceUntil(date)
                pickerTarget = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    dateTime: LocalDateTime,
    showTime: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onDateClick,
                label = {
                    Text(dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN)))
                },
            )
            if (showTime) {
                AssistChip(
                    onClick = onTimeClick,
                    label = {
                        Text(dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                    },
                )
            }
        }
    }
}

@Composable
private fun NaturalLanguageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.nl_input_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(R.string.nl_input_placeholder)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onApply,
                enabled = value.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.nl_input_apply),
                    tint = if (value.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiRow(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val presets = listOf(
        "📅", "🎂", "💼", "☕", "🍽️", "🏃", "✈️", "🎓",
        "🏥", "💊", "💰", "🎬", "🎉", "📞", "🛒", "⏰",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_emoji),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.emoji_none)) },
            )
            presets.forEach { emoji ->
                FilterChip(
                    selected = selected == emoji,
                    onClick = { onSelect(emoji) },
                    label = { Text(text = emoji, style = MaterialTheme.typography.titleMedium) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryRow(
    selectedTag: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_category),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventCategoryPalette.categories.forEachIndexed { index, category ->
                FilterChip(
                    selected = selectedTag == index,
                    onClick = { onSelect(index) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(category.color),
                        )
                    },
                    label = { Text(category.displayName) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationRow(
    selectedMinutes: List<Int>,
    onToggle: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val options: List<Pair<Int, String>> = listOf(
        5 to stringResource(R.string.notification_minutes_before, 5),
        15 to stringResource(R.string.notification_minutes_before, 15),
        30 to stringResource(R.string.notification_minutes_before, 30),
        60 to stringResource(R.string.notification_hours_before, 1),
        24 * 60 to stringResource(R.string.notification_hours_before, 24),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_notification),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedMinutes.isEmpty(),
                onClick = onClear,
                label = { Text(stringResource(R.string.notification_none)) },
            )
            options.forEach { (minutes, label) ->
                FilterChip(
                    selected = minutes in selectedMinutes,
                    onClick = { onToggle(minutes) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceSection(
    rule: RecurrenceRule?,
    onFrequencyChange: (Frequency?) -> Unit,
    onToggleByDay: (DayOfWeek) -> Unit,
    onClickUntil: () -> Unit,
    onClearUntil: () -> Unit,
) {
    val frequencyOptions: List<Pair<Frequency?, String>> = listOf(
        null to stringResource(R.string.repeat_none),
        Frequency.DAILY to stringResource(R.string.repeat_daily),
        Frequency.WEEKLY to stringResource(R.string.repeat_weekly),
        Frequency.MONTHLY to stringResource(R.string.repeat_monthly),
        Frequency.YEARLY to stringResource(R.string.repeat_yearly),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_repeat),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            frequencyOptions.forEach { (freq, label) ->
                FilterChip(
                    selected = rule?.frequency == freq,
                    onClick = { onFrequencyChange(freq) },
                    label = { Text(label) },
                )
            }
        }

        if (rule?.frequency == Frequency.WEEKLY) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                weekdayOrder().forEach { dow ->
                    FilterChip(
                        selected = dow in rule.byDays,
                        onClick = { onToggleByDay(dow) },
                        label = { Text(dow.getDisplayName(TextStyle.SHORT, Locale.KOREAN)) },
                    )
                }
            }
        }

        if (rule != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.repeat_until),
                    style = MaterialTheme.typography.bodyLarge,
                )
                AssistChip(
                    onClick = onClickUntil,
                    label = {
                        Text(
                            rule.until?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                ?: stringResource(R.string.repeat_until_unset)
                        )
                    },
                )
                if (rule.until != null) {
                    TextButton(onClick = onClearUntil) {
                        Text(stringResource(R.string.repeat_clear_until))
                    }
                }
            }
        }
    }
}

private fun weekdayOrder(): List<DayOfWeek> = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: initialMillis
                    val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(picked)
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
