package com.luxboy.mysecretary.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luxboy.mysecretary.domain.model.CalendarViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.dataStore

    val defaultCalendarView: Flow<CalendarViewMode> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_VIEW]
            ?.let { runCatching { CalendarViewMode.valueOf(it) }.getOrNull() }
            ?: CalendarViewMode.MONTH
    }

    suspend fun setDefaultCalendarView(mode: CalendarViewMode) {
        dataStore.edit { it[KEY_DEFAULT_VIEW] = mode.name }
    }

    val wakeWordEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WAKE_WORD_ENABLED] ?: false
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_WAKE_WORD_ENABLED] = enabled }
    }

    val summaryViewEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SUMMARY_VIEW_ENABLED] ?: false
    }

    suspend fun setSummaryViewEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SUMMARY_VIEW_ENABLED] = enabled }
    }

    val voiceFeedbackEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_VOICE_FEEDBACK_ENABLED] ?: true
    }

    suspend fun setVoiceFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_VOICE_FEEDBACK_ENABLED] = enabled }
    }

    val soundFeedbackEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SOUND_FEEDBACK_ENABLED] ?: true
    }

    suspend fun setSoundFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SOUND_FEEDBACK_ENABLED] = enabled }
    }

    val lunarDisplayEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LUNAR_DISPLAY_ENABLED] ?: false
    }

    suspend fun setLunarDisplayEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LUNAR_DISPLAY_ENABLED] = enabled }
    }

    val widgetOpacity: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_WIDGET_OPACITY] ?: 0.8f
    }

    suspend fun setWidgetOpacity(opacity: Float) {
        dataStore.edit { it[KEY_WIDGET_OPACITY] = opacity.coerceIn(0.3f, 1f) }
    }

    companion object {
        private val KEY_DEFAULT_VIEW = stringPreferencesKey("default_calendar_view")
        private val KEY_WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        private val KEY_SUMMARY_VIEW_ENABLED = booleanPreferencesKey("summary_view_enabled")
        private val KEY_VOICE_FEEDBACK_ENABLED = booleanPreferencesKey("voice_feedback_enabled")
        private val KEY_SOUND_FEEDBACK_ENABLED = booleanPreferencesKey("sound_feedback_enabled")
        private val KEY_LUNAR_DISPLAY_ENABLED = booleanPreferencesKey("lunar_display_enabled")
        private val KEY_WIDGET_OPACITY = floatPreferencesKey("widget_opacity")
    }
}
