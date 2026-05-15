package com.luxboy.mysecretary.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luxboy.mysecretary.data.repository.EventRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: EventRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in REBOOT_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val upcoming = repository.getUpcoming()
                val recurring = repository.getAllRecurring()
                (upcoming + recurring)
                    .distinctBy { it.id }
                    .filter { it.reminderMinutes.isNotEmpty() }
                    .forEach { alarmScheduler.scheduleEventOrNextOccurrence(it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val REBOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
