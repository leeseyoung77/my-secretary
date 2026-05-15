package com.luxboy.mysecretary.data.backup

import android.content.Context
import android.net.Uri
import com.luxboy.mysecretary.data.local.EventDao
import com.luxboy.mysecretary.data.local.toDomain
import com.luxboy.mysecretary.data.notification.AlarmScheduler
import com.luxboy.mysecretary.data.widget.MySecretaryWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: EventDao,
    private val alarmScheduler: AlarmScheduler,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val events = dao.getAll().map { it.toBackup() }
            val backup = BackupFile(
                exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                events = events,
            )
            val text = json.encodeToString(BackupFile.serializer(), backup)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("출력 스트림을 열 수 없습니다")
            events.size
        }
    }

    suspend fun import(uri: Uri, mode: ImportMode): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: throw IOException("입력 스트림을 열 수 없습니다")
            val backup = json.decodeFromString(BackupFile.serializer(), text)

            if (mode == ImportMode.REPLACE) {
                dao.getAllIds().forEach { alarmScheduler.cancel(it) }
                dao.deleteAll()
            }
            val now = System.currentTimeMillis()
            backup.events.forEach { evt -> dao.insert(evt.toEntity(now)) }
            scheduleAlarmsForAll()
            MySecretaryWidget.refresh(context)
            backup.events.size
        }
    }

    private suspend fun scheduleAlarmsForAll() {
        val now = System.currentTimeMillis()
        val candidates = (dao.getUpcoming(now) + dao.getAllRecurring())
            .distinctBy { it.id }
            .map { it.toDomain() }
            .filter { it.reminderMinutes.isNotEmpty() }
        candidates.forEach { alarmScheduler.scheduleEventOrNextOccurrence(it) }
    }
}

enum class ImportMode { MERGE, REPLACE }
