package com.example.namazvakti.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.policy.ReligiousDayNotificationEvent
import com.example.namazvakti.domain.policy.ReligiousDayNotificationPlanner
import java.time.ZoneId
import java.time.ZonedDateTime

object ReligiousDayNotificationScheduler {
    suspend fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val settings = appContext.appContainer().notificationSettingsStore.read()
        if (!settings.religiousDaysEnabled) {
            ReligiousDayNotificationAlarm.cancel(appContext)
            return
        }
        val event = ReligiousDayNotificationPlanner.nextEvent(ZonedDateTime.now(ZoneId.systemDefault()))
        if (event == null) {
            ReligiousDayNotificationAlarm.cancel(appContext)
        } else {
            ReligiousDayNotificationAlarm.schedule(appContext, event)
        }
    }
}

internal object ReligiousDayNotificationAlarm {
    const val ACTION = "com.example.namazvakti.action.RELIGIOUS_DAY_NOTIFICATION"
    const val EXTRA_EVENT_DATE = "religious_day_event_date"
    const val EXTRA_TRIGGER_AT = "religious_day_trigger_at"
    const val REQUEST_CODE = 6104

    fun schedule(context: Context, event: ReligiousDayNotificationEvent) {
        val intent = intent(context).apply {
            putExtra(EXTRA_EVENT_DATE, event.date.toString())
            putExtra(EXTRA_TRIGGER_AT, event.triggerAt.toInstant().toEpochMilli())
        }
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(operation)
        // Inexact idle-capable alarms avoid requiring exact-alarm access for this reminder.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.triggerAt.toInstant().toEpochMilli(), operation)
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntent(appContext))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun intent(context: Context): Intent = Intent(context, ReligiousDayNotificationReceiver::class.java)
        .setAction(ACTION)
        .setData("religious-days://next".toUri())
}
