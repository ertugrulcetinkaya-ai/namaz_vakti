package com.example.namazvakti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerWidgetSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    PrayerWidgetScheduler.enqueueRefresh(appContext, force = false)
                    PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(appContext)
                } else {
                    PrayerWidgetScheduler.enqueueRefresh(appContext, force = true)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED
        )
    }
}
