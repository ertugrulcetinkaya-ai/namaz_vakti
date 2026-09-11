package com.example.namazvakti.widget.alarm

import com.example.namazvakti.widget.PrayerWidgetUpdater
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerWidgetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PRAYER_BOUNDARY) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                if (!PrayerWidgetScheduler.hasWidgets(appContext)) {
                    PrayerWidgetScheduler.cancelAll(appContext)
                    return@launch
                }
                PrayerWidgetUpdater.updateAll(appContext)
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(appContext)
                PrayerWidgetScheduler.enqueueRefresh(appContext)
            } catch (exception: Exception) {
                Log.e(TAG, "boundary alarm widget update failed", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PRAYER_BOUNDARY =
            "com.example.namazvakti.action.PRAYER_BOUNDARY"
        private const val TAG = "NamazWidget"
    }
}
