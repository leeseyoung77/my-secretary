package com.luxboy.mysecretary.data.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.color.ColorProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.luxboy.mysecretary.MainActivity
import com.luxboy.mysecretary.domain.model.Event
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MySecretaryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val repository = entryPoint.repository()
        val preferences = entryPoint.preferences()

        val today = LocalDate.now()
        val events = repository.getForDate(today)
        val opacity = preferences.widgetOpacity.first()

        provideContent {
            WidgetContent(today = today, events = events, opacity = opacity)
        }
    }

    companion object {
        /**
         * Forces a fresh widget render. Uses three independent paths in case one is throttled
         * by the system or cached by Glance: (1) explicit per-id update via GlanceAppWidgetManager
         * which is the most direct API contract, (2) Glance's updateAll convenience, (3) a manual
         * APPWIDGET_UPDATE broadcast as a final fallback.
         *
         * Returns a short diagnostic string describing what each path saw — used by the settings
         * "force refresh" button so the user can report what's actually happening.
         */
        suspend fun refresh(context: Context): String {
            val widget = MySecretaryWidget()

            // (1) Direct per-id update via Glance's manager.
            val r1 = runCatching {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(MySecretaryWidget::class.java)
                glanceIds.forEach { id -> widget.update(context, id) }
                glanceIds.size
            }
            val perIdLabel = r1.fold(
                onSuccess = { count -> "perId=$count" },
                onFailure = { e -> "perId=ERR:${e.javaClass.simpleName}" },
            )

            // (2) Glance updateAll.
            val r2 = runCatching { widget.updateAll(context) }
            val updateAllLabel = if (r2.isSuccess) "updateAll=OK"
            else "updateAll=ERR:${r2.exceptionOrNull()?.javaClass?.simpleName}"

            // (3) Legacy broadcast path.
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MySecretaryWidgetReceiver::class.java)
            val ids = runCatching { appWidgetManager.getAppWidgetIds(component) }.getOrNull()
                ?: IntArray(0)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, MySecretaryWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
            return "$perIdLabel $updateAllLabel bcast=${ids.size}"
        }
    }
}

@Composable
private fun WidgetContent(today: LocalDate, events: List<Event>, opacity: Float) {
    val alpha = opacity.coerceIn(0.3f, 1f)
    val background = ColorProvider(
        day = Color.White.copy(alpha = alpha),
        night = Color(0xFF1A1B1F).copy(alpha = alpha),
    )
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(background)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "오늘",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = today.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                        ),
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))

                if (events.isEmpty()) {
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "오늘 일정이 없습니다",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp,
                            ),
                        )
                    }
                } else {
                    events.take(7).forEach { event ->
                        EventLine(event)
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }
                    if (events.size > 7) {
                        Text(
                            text = "외 ${events.size - 7}건",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLine(event: Event) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(16.dp)
                .background(GlanceTheme.colors.primary)
                .cornerRadius(2.dp),
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = if (event.isAllDay) "종일" else event.start.toLocalTime()
                .format(DateTimeFormatter.ofPattern("HH:mm")),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.width(44.dp),
        )
        Text(
            text = event.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
    }
}
