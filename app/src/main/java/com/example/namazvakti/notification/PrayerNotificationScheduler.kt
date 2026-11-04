package com.example.namazvakti.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.policy.PrayerNotificationPlanner
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import com.example.namazvakti.widget.alarm.PrayerExactAlarmAccess
import java.time.LocalTime
import java.time.ZonedDateTime

object PrayerNotificationScheduler {
    suspend fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val container = appContext.appContainer()
        val settings = container.notificationSettingsStore.read()
        if (!settings.notificationsEnabled) {
            PrayerNotificationAlarm.cancel(appContext)
            PrayerWidgetScheduler.enqueueRefresh(appContext, force = false)
            return
        }

        val location = container.store.readLocation()
        val now = container.timeProvider.now().withZoneSameInstant(location.timezone)
        val today = now.toLocalDate()
        val todayCache = container.store.readCache(today, location, container.settings)
        val tomorrowCache = container.store.readCache(today.plusDays(1), location, container.settings)
        val event = PrayerNotificationPlanner.nextEvent(
            todayCache,
            tomorrowCache,
            settings,
            now
        )
        if (event != null) {
            PrayerNotificationAlarm.schedule(appContext, event)
            PrayerWidgetScheduler.enqueueRefresh(appContext, force = false)
        } else {
            PrayerNotificationAlarm.scheduleReplan(
                appContext,
                today.plusDays(1).atTime(LocalTime.of(0, 1)).atZone(location.timezone)
            )
            PrayerWidgetScheduler.requestBackgroundRefresh(appContext)
        }
    }
}

/** Keeps notification alarms independent from the widget alarm and its PendingIntent. */
internal object PrayerNotificationAlarm {
    const val ACTION = "com.example.namazvakti.action.PRAYER_NOTIFICATION"
    const val EXTRA_PRAYER = "notification_prayer"
    const val EXTRA_TYPE = "notification_type"
    const val EXTRA_TRIGGER_AT = "notification_trigger_at"
    const val EXTRA_PRAYER_TIME = "notification_prayer_time"
    const val EXTRA_PRAYER_DATE = "notification_prayer_date"
    const val EXTRA_LOCATION_CITY = "notification_location_city"
    const val EXTRA_LOCATION_COUNTRY = "notification_location_country"
    const val EXTRA_LEAD_MINUTES = "notification_lead_minutes"
    const val EXTRA_REPLAN_ONLY = "notification_replan_only"
    private const val REQUEST_CODE = 6103
    private const val TAG = "PrayerNotification"

    fun schedule(context: Context, event: com.example.namazvakti.domain.policy.PrayerNotificationEvent) {
        scheduleAt(
            context,
            event.triggerAt,
            intent(context).apply {
                putExtra(EXTRA_PRAYER, event.prayer.name)
                putExtra(EXTRA_TYPE, event.type.name)
                putExtra(EXTRA_TRIGGER_AT, event.triggerAt.toInstant().toEpochMilli())
                putExtra(EXTRA_PRAYER_TIME, event.prayerTime.toInstant().toEpochMilli())
                putExtra(EXTRA_PRAYER_DATE, event.prayerTime.toLocalDate().toString())
                putExtra(EXTRA_LOCATION_CITY, event.locationCity)
                putExtra(EXTRA_LOCATION_COUNTRY, event.locationCountry)
                putExtra(EXTRA_LEAD_MINUTES, event.leadMinutes)
                putExtra(EXTRA_REPLAN_ONLY, false)
            }
        )
    }

    fun scheduleReplan(context: Context, target: ZonedDateTime) {
        scheduleAt(
            context,
            target,
            intent(context).putExtra(EXTRA_REPLAN_ONLY, true)
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntent(appContext))
    }

    private fun scheduleAt(context: Context, target: ZonedDateTime, intent: Intent) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(operation)
        if (PrayerExactAlarmAccess.canScheduleExactAlarms(appContext)) {
            scheduleExactOrFallback(manager, target, operation)
        } else {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.toInstant().toEpochMilli(),
                operation
            )
        }
        Log.d(TAG, "next notification alarm scheduled at=$target")
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleExactOrFallback(
        manager: AlarmManager,
        target: ZonedDateTime,
        operation: PendingIntent
    ) {
        try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.toInstant().toEpochMilli(),
                operation
            )
        } catch (exception: SecurityException) {
            Log.w(TAG, "exact notification alarm access changed; using inexact alarm", exception)
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.toInstant().toEpochMilli(),
                operation
            )
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun intent(context: Context) = Intent(context, PrayerNotificationReceiver::class.java)
        .setAction(ACTION)
        .setData("prayer-notification://next".toUri())
}
