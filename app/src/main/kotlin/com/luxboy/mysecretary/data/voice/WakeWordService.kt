package com.luxboy.mysecretary.data.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.luxboy.mysecretary.MainActivity
import com.luxboy.mysecretary.R
import com.luxboy.mysecretary.data.preferences.AppPreferences
import com.luxboy.mysecretary.data.repository.ContactRepository
import com.luxboy.mysecretary.data.repository.EventRepository
import com.luxboy.mysecretary.domain.voice.VoiceCommandParser
import com.luxboy.mysecretary.domain.voice.VoiceIntent
import com.luxboy.mysecretary.domain.voice.VoiceParseResult
import com.luxboy.mysecretary.domain.voice.VoiceResponseBuilder
import com.luxboy.mysecretary.ui.voice.TtsController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject lateinit var voiceCommandBus: VoiceCommandBus
    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var contactRepository: ContactRepository
    @Inject lateinit var preferences: AppPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TtsController
    @Volatile private var stopped = false
    @Volatile private var processingCommand = false

    override fun onCreate() {
        super.onCreate()
        tts = TtsController(applicationContext)
        startForegroundWithNotification()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }
        // Prefer on-device recognizer (API 31+) for lower latency, offline support, privacy.
        val useOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(this) }.getOrDefault(false)
        recognizer = if (useOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }.apply { setRecognitionListener(listener) }
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { preferences.setWakeWordEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopped = true
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        tts.shutdown()
        scope.cancel()
    }

    private fun startForegroundWithNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "음성 호출어",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "호출어를 감지하는 동안 표시됩니다"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val stopPi = PendingIntent.getService(
            this,
            0,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentPi = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("음성 호출어 대기 중")
            .setContentText("\"비서야\" 라고 말한 뒤 명령을 이어서 말하세요")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .addAction(0, "중지", stopPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startListening() {
        if (stopped || processingCommand) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Longer silence tolerance so commands after wake word aren't cut short
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
        }
        runCatching { recognizer?.startListening(intent) }
    }

    private fun scheduleRestart(delayMs: Long = 400L) {
        if (stopped) return
        scope.launch {
            delay(delayMs)
            startListening()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val candidates = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: emptyList()
            // Among the recognizer's N candidates, prefer the one that contains the wake word.
            val withWake = candidates.firstOrNull { c -> WAKE_WORDS.any { it in c } }
            val text = withWake ?: candidates.firstOrNull()
            if (text != null) handleUtterance(text)
            scheduleRestart()
        }

        override fun onError(error: Int) {
            scheduleRestart(if (error == SpeechRecognizer.ERROR_NO_MATCH) 200L else 800L)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleUtterance(text: String) {
        val command = extractCommand(text) ?: return
        processingCommand = true
        runCatching { recognizer?.stopListening() }
        if (command.isBlank()) {
            tts.speak("네, 말씀하세요")
            processingCommand = false
            return
        }
        val parsed = VoiceCommandParser.parse(command)
        when (parsed.intent) {
            VoiceIntent.QUERY -> handleQuery(parsed)
            VoiceIntent.CALL -> handleCall(parsed)
            VoiceIntent.ADD, VoiceIntent.DELETE -> {
                voiceCommandBus.tryEmit(parsed)
                if (parsed.intent == VoiceIntent.DELETE) {
                    tts.speak("일정을 확인합니다")
                }
                // ADD: ViewModel saves and speaks "일정이 추가되었습니다" after success.
                processingCommand = false
            }
        }
    }

    private fun handleQuery(parsed: VoiceParseResult) {
        scope.launch {
            val events = eventRepository.observeForDate(parsed.date).first()
            val response = VoiceResponseBuilder.build(
                date = parsed.date,
                events = events,
                titleFilter = parsed.title,
            )
            tts.speak(response)
            processingCommand = false
        }
    }

    private fun handleCall(parsed: VoiceParseResult) {
        scope.launch {
            val name = parsed.title.trim()
            if (name.isBlank()) {
                tts.speak("누구에게 전화 걸까요?")
                processingCommand = false
                return@launch
            }
            val matches = contactRepository.findByName(name)
            when (matches.size) {
                0 -> tts.speak("${name} 연락처를 찾을 수 없습니다")
                1 -> {
                    val target = matches[0]
                    tts.speak("${target.name}에게 전화를 겁니다")
                    val sanitized = target.phoneNumber.filter { it.isDigit() || it == '+' }
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$sanitized")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { applicationContext.startActivity(dialIntent) }
                }
                else -> voiceCommandBus.tryEmit(parsed)
            }
            processingCommand = false
        }
    }

    private fun extractCommand(text: String): String? {
        for (wake in WAKE_WORDS) {
            val idx = text.indexOf(wake, ignoreCase = true)
            if (idx >= 0) {
                return text.substring(idx + wake.length).trimStart(',', '.', '!', ' ', '\t').trim()
            }
        }
        return null
    }

    companion object {
        const val ACTION_STOP = "com.luxboy.mysecretary.WAKE_WORD_STOP"
        private const val CHANNEL_ID = "wake_word_channel"
        private const val NOTIF_ID = 4242
        private val WAKE_WORDS = listOf("비서야", "헤이 비서", "안녕 비서")

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
