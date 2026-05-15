package com.luxboy.mysecretary.data.voice

import com.luxboy.mysecretary.domain.voice.VoiceParseResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide bus that carries voice commands from the wake-word service to UI layer.
 * Replay = 0; we only deliver to currently-active subscribers.
 * Buffered with DROP_OLDEST to avoid back-pressure issues if no UI is listening.
 */
@Singleton
class VoiceCommandBus @Inject constructor() {

    private val _commands = MutableSharedFlow<VoiceParseResult>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<VoiceParseResult> = _commands.asSharedFlow()

    fun tryEmit(parsed: VoiceParseResult): Boolean = _commands.tryEmit(parsed)
}
