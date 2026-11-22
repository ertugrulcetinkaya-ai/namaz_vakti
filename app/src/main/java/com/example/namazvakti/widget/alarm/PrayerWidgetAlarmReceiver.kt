package com.example.namazvakti.widget.alarm

import com.example.namazvakti.widget.PrayerWidgetUpdater
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.namazvakti.widget.launchAsync

class PrayerWidgetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PRAYER_BOUNDARY) return
        val appContext = context.applicationContext
        launchAsync(appContext, TAG, "boundary alarm widget update failed") {
            if (!PrayerWidgetScheduler.hasWidgets(appContext)) {
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(appContext)
                PrayerWidgetScheduler.enqueueRefresh(appContext, force = false)
                return@launchAsync
            }
            val snapshot = PrayerWidgetUpdater.updateAllAndReturnSnapshot(appContext)
            PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(
                appContext,
                snapshot = snapshot
            )
            PrayerWidgetScheduler.enqueueRefresh(appContext, snapshot = snapshot)
        }
    }

    companion object {
        const val ACTION_PRAYER_BOUNDARY =
            "io.github.ertugrulcetinkaya.namazvakti.action.PRAYER_BOUNDARY"
        private const val TAG = "NamazWidget"
    }
}
