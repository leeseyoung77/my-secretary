package com.luxboy.mysecretary.data.notification

import android.content.Context
import android.media.RingtoneManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the device's system notification sound as a short "ding" feedback.
 * Uses RingtoneManager so it respects the user's chosen notification sound
 * and the system volume.
 */
@Singleton
class SoundFeedbackPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun play() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.play()
        }
    }
}
