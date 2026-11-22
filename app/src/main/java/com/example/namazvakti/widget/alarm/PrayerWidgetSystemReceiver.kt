package com.example.namazvakti.widget.alarm

import com.example.namazvakti.widget.PrayerWidgetUpdater
import com.example.namazvakti.widget.PrayerWidgetSnapshotLoader
import com.example.namazvakti.notification.PrayerNotificationScheduler
import com.example.namazvakti.notification.ReligiousDayNotificationScheduler
import com.example.namazvakti.domain.policy.needsBackgroundRefresh
import com.example.namazvakti.app.appContainer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.namazvakti.widget.launchAsync

class PrayerWidgetSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val appContext = context.applicationContext
        launchAsync(appContext, TAG, "system-triggered widget refresh failed") {
            val container = appContext.appContainer()
            val widgetsExist = PrayerWidgetScheduler.hasWidgets(appContext)
            val notificationSettings = container.notificationSettingsStore.read()
            val notificationsEnabled = notificationSettings.notificationsEnabled
            ReligiousDayNotificationScheduler.scheduleNext(appContext)
            if (!needsBackgroundRefresh(widgetsExist, notificationsEnabled)) {
                PrayerWidgetScheduler.cancelAll(appContext)
                return@launchAsync
            }
            val snapshot = if (widgetsExist) {
                PrayerWidgetUpdater.updateAllAndReturnSnapshot(appContext).also {
                    PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(
                        appContext,
                        snapshot = it
                    )
                }
            } else {
                PrayerWidgetSnapshotLoader.load(appContext)
            }
            if (notificationsEnabled) PrayerNotificationScheduler.scheduleNext(appContext)
            PrayerWidgetScheduler.enqueueRefresh(
                appContext,
                force = false,
                snapshot = snapshot
            )
        }
    }

    private companion object {
        const val TAG = "NamazWidget"
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        const val ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
