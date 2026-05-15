package com.luxboy.mysecretary.ui.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

class TtsController(context: Context) {

    @Volatile private var ready = false
    private val pending = mutableListOf<String>()
    private val lock = Any()
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val langResult = tts.setLanguage(Locale.KOREAN)
            ready = langResult != TextToSpeech.LANG_MISSING_DATA &&
                langResult != TextToSpeech.LANG_NOT_SUPPORTED
            if (ready) {
                synchronized(lock) {
                    pending.forEach { tts.speak(it, TextToSpeech.QUEUE_ADD, null, null) }
                    pending.clear()
                }
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "msc-${System.currentTimeMillis()}")
        } else {
            synchronized(lock) { pending.add(text) }
        }
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }
}

@Composable
fun rememberTtsController(): TtsController {
    val context = LocalContext.current
    val controller = remember { TtsController(context) }
    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }
    return controller
}
