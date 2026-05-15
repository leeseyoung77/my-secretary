package com.luxboy.mysecretary.ui.calendar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luxboy.mysecretary.R
import com.luxboy.mysecretary.domain.holiday.KoreanHolidays
import com.luxboy.mysecretary.domain.lunar.LunarFormatter
import com.luxboy.mysecretary.domain.model.CalendarViewMode
import com.luxboy.mysecretary.domain.model.Contact
import com.luxboy.mysecretary.domain.model.Event
import com.luxboy.mysecretary.domain.voice.VoiceCommandParser
import com.luxboy.mysecretary.domain.voice.VoiceIntent
import com.luxboy.mysecretary.ui.components.AppAlertDialog
import com.luxboy.mysecretary.ui.contacts.sanitizePhone
import com.luxboy.mysecretary.ui.theme.EventCategoryPalette
import com.luxboy.mysecretary.ui.voice.rememberTtsController
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onAddEvent: (LocalDate) -> Unit,
    onEditEvent: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val visibleAnchor by viewModel.visibleAnchor.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val voiceDeleteState by viewModel.voiceDeleteState.collectAsStateWithLifecycle()
    val voiceCallState by viewModel.voiceCallState.collectAsStateWithLifecycle()
    val summaryViewEnabled by viewModel.summaryViewEnabled.collectAsStateWithLifecycle()
    val lunarDisplayEnabled by viewModel.lunarDisplayEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tts = rememberTtsController()

    LaunchedEffect(Unit) {
        viewModel.speakEvents.collect { text ->
            tts.speak(text)
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.callActions.collect { action ->
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${sanitizePhone(action.phoneNumber)}")
            }
            runCatching { context.startActivity(intent) }.onFailure {
                Toast.makeText(context, "전화 앱을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@rememberLauncherForActivityResult
        val parsed = VoiceCommandParser.parse(spoken)
        when (parsed.intent) {
            VoiceIntent.ADD -> viewModel.saveVoiceEvent(parsed)
            VoiceIntent.DELETE -> viewModel.findDeleteCandidates(parsed)
            VoiceIntent.QUERY -> viewModel.handleVoiceQuery(parsed)
            VoiceIntent.CALL -> viewModel.handleVoiceCall(parsed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_calendar)) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    ViewModeIcon(
                        icon = Icons.Default.CalendarMonth,
                        label = stringResource(R.string.view_month),
                        selected = viewMode == CalendarViewMode.MONTH,
                        onClick = { viewModel.setViewMode(CalendarViewMode.MONTH) },
                    )
                    ViewModeIcon(
                        icon = Icons.Default.CalendarViewWeek,
                        label = stringResource(R.string.view_week),
                        selected = viewMode == CalendarViewMode.WEEK,
                        onClick = { viewModel.setViewMode(CalendarViewMode.WEEK) },
                    )
                    ViewModeIcon(
                        icon = Icons.Default.CalendarViewDay,
                        label = stringResource(R.string.view_day),
                        selected = viewMode == CalendarViewMode.DAY,
                        onClick = { viewModel.setViewMode(CalendarViewMode.DAY) },
                    )
                    ViewModeIcon(
                        icon = Icons.AutoMirrored.Filled.ViewList,
                        label = stringResource(R.string.view_agenda),
                        selected = viewMode == CalendarViewMode.AGENDA,
                        onClick = { viewModel.setViewMode(CalendarViewMode.AGENDA) },
                    )
                    IconButton(onClick = {
                        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.voice_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@IconButton
                        }
                        voiceLauncher.launch(buildRecognizeIntent(context.getString(R.string.voice_prompt)))
                    }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = stringResource(R.string.action_voice),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddEvent(selectedDate) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_event)) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            RangeHeader(
                viewMode = viewMode,
                anchor = visibleAnchor,
                onPrevious = viewModel::showPrevious,
                onNext = viewModel::showNext,
            )
            if (viewMode == CalendarViewMode.MONTH || viewMode == CalendarViewMode.WEEK) {
                WeekdayHeader()
            }
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.96f))
                        .togetherWith(
                            fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.96f)
                        )
                },
                label = "viewModeContent",
            ) { mode ->
                when (mode) {
                    CalendarViewMode.MONTH -> MonthGrid(
                        month = YearMonth.from(visibleAnchor),
                        selectedDate = selectedDate,
                        events = events,
                        summaryEnabled = summaryViewEnabled,
                        lunarEnabled = lunarDisplayEnabled,
                        onDateClick = viewModel::selectDate,
                    )
                    CalendarViewMode.WEEK -> WeekGrid(
                        weekStart = CalendarViewModel.weekStartOf(visibleAnchor),
                        selectedDate = selectedDate,
                        events = events,
                        summaryEnabled = summaryViewEnabled,
                        lunarEnabled = lunarDisplayEnabled,
                        onDateClick = viewModel::selectDate,
                    )
                    CalendarViewMode.DAY -> DayTimeline(
                        date = visibleAnchor,
                        events = events,
                        onEventClick = onEditEvent,
                    )
                    CalendarViewMode.AGENDA -> AgendaList(
                        startDate = visibleAnchor,
                        events = events,
                        onEventClick = onEditEvent,
                    )
                }
            }
            if (viewMode == CalendarViewMode.MONTH || viewMode == CalendarViewMode.WEEK) {
                HorizontalDivider()
                DayEventList(
                    date = selectedDate,
                    events = events.filter { it.start.toLocalDate() == selectedDate },
                    onEventClick = onEditEvent,
                )
            }
        }
    }

    VoiceDeleteDialog(
        state = voiceDeleteState,
        onConfirmDelete = viewModel::confirmDelete,
        onDismiss = viewModel::dismissVoiceDelete,
    )

    VoiceCallDialog(
        state = voiceCallState,
        onConfirmCall = viewModel::confirmCall,
        onDismiss = viewModel::dismissVoiceCall,
    )
}

@Composable
private fun VoiceCallDialog(
    state: VoiceCallState,
    onConfirmCall: (Contact) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        VoiceCallState.Idle -> Unit
        is VoiceCallState.NotFound -> AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.voice_call_title)) },
            text = { Text(stringResource(R.string.voice_call_not_found, state.name)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_save)) }
            },
        )
        is VoiceCallState.Choose -> AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.voice_call_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.voice_call_choose, state.name),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    state.contacts.forEach { contact ->
                        TextButton(
                            onClick = { onConfirmCall(contact) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "${contact.name}  ${contact.phoneNumber}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun buildRecognizeIntent(prompt: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
    }

@Composable
private fun VoiceDeleteDialog(
    state: VoiceDeleteState,
    onConfirmDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        VoiceDeleteState.Idle -> Unit
        is VoiceDeleteState.NotFound -> AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.voice_delete_title)) },
            text = {
                val keyword = state.keyword.ifBlank { stringResource(R.string.voice_delete_no_keyword) }
                Text(stringResource(R.string.voice_delete_not_found, state.dateLabel, keyword))
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_save)) }
            },
        )
        is VoiceDeleteState.Candidates -> AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.voice_delete_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            R.string.voice_delete_choose,
                            state.dateLabel,
                            state.keyword.ifBlank { stringResource(R.string.voice_delete_no_keyword) },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    state.events.forEach { event ->
                        TextButton(
                            onClick = { onConfirmDelete(event.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val time = if (event.isAllDay) "종일" else event.start.toLocalTime()
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                            Text(
                                text = "$time  ${event.title}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ViewModeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
private fun RangeHeader(
    viewMode: CalendarViewMode,
    anchor: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val title = when (viewMode) {
        CalendarViewMode.MONTH -> anchor.format(DateTimeFormatter.ofPattern("yyyy년 M월"))
        CalendarViewMode.WEEK -> {
            val weekStart = CalendarViewModel.weekStartOf(anchor)
            val weekEnd = weekStart.plusDays(6)
            if (weekStart.year == weekEnd.year && weekStart.month == weekEnd.month) {
                "${weekStart.year}년 ${weekStart.monthValue}월 ${weekStart.dayOfMonth}일 - ${weekEnd.dayOfMonth}일"
            } else if (weekStart.year == weekEnd.year) {
                "${weekStart.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))} - ${weekEnd.format(DateTimeFormatter.ofPattern("M월 d일"))}"
            } else {
                "${weekStart.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))} - ${weekEnd.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))}"
            }
        }
        CalendarViewMode.DAY ->
            anchor.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN))
        CalendarViewMode.AGENDA -> {
            val end = anchor.plusDays(CalendarViewModel.AGENDA_RANGE_DAYS.toLong() - 1)
            "${anchor.format(DateTimeFormatter.ofPattern("M월 d일"))} - " +
                end.format(DateTimeFormatter.ofPattern("M월 d일"))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.action_previous))
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.action_next))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val weekdays = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdays.forEach { dow ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dow.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    color = when (dow) {
                        DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
                        DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    events: List<Event>,
    summaryEnabled: Boolean,
    lunarEnabled: Boolean,
    onDateClick: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    val leadingEmptyDays = (firstDay.dayOfWeek.value % 7)
    val totalCells = 42
    val today = LocalDate.now()
    val eventsByDate = events.groupBy { it.start.toLocalDate() }

    Column(modifier = Modifier.fillMaxWidth()) {
        for (week in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val cellIndex = week * 7 + dow
                    val dayOfMonth = cellIndex - leadingEmptyDays + 1
                    if (dayOfMonth in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayOfMonth)
                        DayCell(
                            modifier = Modifier.weight(1f),
                            date = date,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            dayEvents = eventsByDate[date].orEmpty(),
                            summaryEnabled = summaryEnabled,
                            summaryMaxLines = MONTH_SUMMARY_LINES,
                            lunarEnabled = lunarEnabled,
                            onClick = { onDateClick(date) },
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (summaryEnabled) Modifier.height(summaryCellHeight(MONTH_SUMMARY_LINES))
                                    else Modifier.aspectRatio(1f)
                                )
                        )
                    }
                    if (cellIndex >= totalCells - 1) break
                }
            }
        }
    }
}

@Composable
private fun WeekGrid(
    weekStart: LocalDate,
    selectedDate: LocalDate,
    events: List<Event>,
    summaryEnabled: Boolean,
    lunarEnabled: Boolean,
    onDateClick: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val eventsByDate = events.groupBy { it.start.toLocalDate() }

    Row(modifier = Modifier.fillMaxWidth()) {
        for (offset in 0 until 7) {
            val date = weekStart.plusDays(offset.toLong())
            DayCell(
                modifier = Modifier.weight(1f),
                date = date,
                isSelected = date == selectedDate,
                isToday = date == today,
                dayEvents = eventsByDate[date].orEmpty(),
                summaryEnabled = summaryEnabled,
                summaryMaxLines = WEEK_SUMMARY_LINES,
                lunarEnabled = lunarEnabled,
                onClick = { onDateClick(date) },
            )
        }
    }
}

private const val MONTH_SUMMARY_LINES = 2
private const val WEEK_SUMMARY_LINES = 5
private fun summaryCellHeight(maxLines: Int) = 48.dp + 12.dp * (maxLines + 1)

@Composable
private fun DayCell(
    modifier: Modifier,
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    dayEvents: List<Event>,
    summaryEnabled: Boolean,
    summaryMaxLines: Int,
    lunarEnabled: Boolean,
    onClick: () -> Unit,
) {
    val sizeModifier =
        if (summaryEnabled) Modifier.height(summaryCellHeight(summaryMaxLines)) else Modifier.aspectRatio(1f)

    val targetBg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "dayCellBg",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "dayCellScale",
    )
    val holidayName = KoreanHolidays.nameOf(date)
    val isHoliday = holidayName != null
    val targetTextColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        isHoliday || date.dayOfWeek == DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
        date.dayOfWeek == DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val textColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(220),
        label = "dayCellText",
    )

    Box(
        modifier = modifier
            .then(sizeModifier)
            .clickable(onClick = onClick),
        contentAlignment = if (summaryEnabled) Alignment.TopCenter else Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (summaryEnabled) 4.dp else 0.dp),
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(bgColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = textColor,
                )
            }
            if (lunarEnabled) {
                LunarFormatter.shortLabel(date)?.let { label ->
                    Text(
                        text = label,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 1.dp, start = 2.dp, end = 2.dp),
                    )
                }
            }

            if (summaryEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                if (holidayName != null) {
                    Text(
                        text = holidayName,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                    )
                }
                val remainingLines = (summaryMaxLines - (if (holidayName != null) 1 else 0)).coerceAtLeast(0)
                dayEvents.take(remainingLines).forEach { event ->
                    Text(
                        text = event.title,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        color = EventCategoryPalette.colorOf(event.colorTag),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                    )
                }
                if (dayEvents.size > remainingLines) {
                    Text(
                        text = "외 ${dayEvents.size - remainingLines}건",
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                    )
                }
            } else if (holidayName != null) {
                Text(
                    text = holidayName,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                )
                if (dayEvents.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(3.dp)
                    )
                }
            } else if (dayEvents.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(3.dp)
                )
            }
        }
    }
}

@Composable
private fun DayEventList(
    date: LocalDate,
    events: List<Event>,
    onEventClick: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        )
        if (events.isEmpty()) {
            EmptyDayState()
        } else {
            events.forEach { event ->
                EventRow(event = event, onClick = { onEventClick(event.id) })
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmptyDayState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.EventAvailable,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text(
            text = "오늘은 여유로운 하루",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "+ 버튼을 눌러 새 일정을 추가해보세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun EventRow(event: Event, onClick: () -> Unit) {
    val categoryColor = EventCategoryPalette.colorOf(event.colorTag)
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.isDday) {
                        DdayBadge(event = event)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    val displayTitle = if (event.emoji.isNullOrBlank()) event.title
                    else "${event.emoji}  ${event.title}"
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val time = if (event.isAllDay) {
                    "하루 종일"
                } else {
                    val fmt = DateTimeFormatter.ofPattern("HH:mm")
                    "${event.start.toLocalTime().format(fmt)} - ${event.end.toLocalTime().format(fmt)}"
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                event.location?.takeIf { it.isNotBlank() }?.let { loc ->
                    Text(
                        text = "📍 $loc",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { launchMapIntent(context, loc) },
                    )
                }
            }
        }
    }
}

private fun launchMapIntent(context: android.content.Context, location: String) {
    val encoded = android.net.Uri.encode(location)
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=$encoded")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun DdayBadge(event: Event) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), event.start.toLocalDate())
    val label = when {
        days == 0L -> "D-DAY"
        days > 0 -> "D-$days"
        else -> "D+${-days}"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

private val HOUR_HEIGHT = 56.dp
private val HOUR_LABEL_WIDTH = 48.dp

@Composable
private fun DayTimeline(
    date: LocalDate,
    events: List<Event>,
    onEventClick: (Long) -> Unit,
) {
    val dayEvents = events.filter { it.start.toLocalDate() == date }
    val allDay = dayEvents.filter { it.isAllDay }
    val timed = dayEvents.filter { !it.isAllDay }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (allDay.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "종일",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                allDay.forEach { event ->
                    AllDayBanner(event = event, onClick = { onEventClick(event.id) })
                }
            }
            HorizontalDivider()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HOUR_HEIGHT * 24),
        ) {
            HourGrid()

            timed.forEach { event ->
                TimedEventBlock(
                    event = event,
                    date = date,
                    onClick = { onEventClick(event.id) },
                )
            }
        }
    }
}

@Composable
private fun HourGrid() {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(24) { hour ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HOUR_HEIGHT),
            ) {
                Text(
                    text = String.format(Locale.US, "%02d:00", hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(HOUR_LABEL_WIDTH)
                        .padding(start = 8.dp, top = 2.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                )
            }
        }
    }
}

@Composable
private fun TimedEventBlock(event: Event, date: LocalDate, onClick: () -> Unit) {
    val startMin = event.start.toLocalTime().toSecondOfDay() / 60f
    val rawEndMin = if (event.end.toLocalDate().isAfter(date)) 24f * 60f
    else event.end.toLocalTime().toSecondOfDay() / 60f
    val endMin = rawEndMin.coerceAtMost(24f * 60f)
    val durationMin = (endMin - startMin).coerceAtLeast(30f)

    val topDp = (startMin / 60f * HOUR_HEIGHT.value).dp
    val heightDp = (durationMin / 60f * HOUR_HEIGHT.value).dp
    val color = EventCategoryPalette.colorOf(event.colorTag)

    Box(
        modifier = Modifier
            .padding(start = HOUR_LABEL_WIDTH + 4.dp, end = 12.dp)
            .offset(y = topDp)
            .fillMaxWidth()
            .height(heightDp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column {
            val time = DateTimeFormatter.ofPattern("HH:mm")
            Text(
                text = "${event.start.toLocalTime().format(time)} ${event.title.takeIfNotEmpty()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun String.takeIfNotEmpty(): String = ifBlank { "(제목 없음)" }

@Composable
private fun AllDayBanner(event: Event, onClick: () -> Unit) {
    val color = EventCategoryPalette.colorOf(event.colorTag)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        val titleText = if (event.emoji.isNullOrBlank()) event.title else "${event.emoji} ${event.title}"
        Text(
            text = titleText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AgendaList(
    startDate: LocalDate,
    events: List<Event>,
    onEventClick: (Long) -> Unit,
) {
    val today = LocalDate.now()
    val sorted = events
        .filter { !it.end.toLocalDate().isBefore(startDate) }
        .sortedBy { it.start }
    val grouped = sorted.groupBy { it.start.toLocalDate() }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (grouped.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "다가오는 일정이 없습니다",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        grouped.forEach { (date, dayEvents) ->
            AgendaDateHeader(date = date, today = today)
            dayEvents.forEach { event ->
                EventRow(event = event, onClick = { onEventClick(event.id) })
            }
        }
    }
}

@Composable
private fun AgendaDateHeader(date: LocalDate, today: LocalDate) {
    val label = when (date) {
        today -> "오늘 · " + date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
        today.plusDays(1) -> "내일 · " + date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
        else -> date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
