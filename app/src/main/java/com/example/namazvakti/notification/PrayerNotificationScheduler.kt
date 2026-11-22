package com.example.namazvakti.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.example.namazvakti.app.AlarmScheduling
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.policy.PrayerNotificationDeliveryPolicy
import com.example.namazvakti.domain.policy.PrayerNotificationEvent
import com.example.namazvakti.domain.policy.PrayerNotificationEventId
import com.example.namazvakti.domain.policy.PrayerNotificationPlanner
import com.example.namazvakti.widget.alarm.PrayerExactAlarmAccess
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Serialize replanning with the read, publish, and record steps of alarm delivery.
internal val prayerNotificationMutex = Mutex()

object PrayerNotificationScheduler {
    suspend fun scheduleNext(context: Context) = scheduleNext(context, attemptedEvent = null)

    internal suspend fun scheduleNext(
        context: Context,
        attemptedEvent: PrayerNotificationEventId?,
        scheduleAlarm: (Context, PrayerNotificationEvent, Boolean) -> Intent = PrayerNotificationAlarm::schedule
    ) = prayerNotificationMutex.withLock {
        val appContext = context.applicationContext
        val container = appContext.appContainer()
        val settings = container.notificationSettingsStore.read()
        if (!settings.notificationsEnabled) {
            PrayerNotificationAlarm.cancel(appContext)
            PrayerWidgetScheduler.enqueueRefresh(appContext, force = false)
            return@withLock
        }

        val location = container.store.readLocation()
        val now = container.timeProvider.now().withZoneSameInstant(location.timezone)
        val today = now.toLocalDate()
        val todayCache = container.store.readCache(today, location, container.settings)
        val tomorrowCache = container.store.readCache(today.plusDays(1), location, container.settings)
        val exact = PrayerExactAlarmAccess.canScheduleExactAlarms(appContext)
        val windowStart = now.minus(PrayerNotificationDeliveryPolicy.lateTolerance(exact)).toLocalDate()
        val yesterdayCache = if (windowStart < today) {
            container.store.readCache(windowStart, location, container.settings)
        } else {
            null
        }
        val shownEvents = container.notificationSettingsStore.readShownEvents()
        val event = PrayerNotificationPlanner.nextEvent(
            todayCache,
            tomorrowCache,
            settings,
            now,
            exact = exact,
            shownEvents = shownEvents,
            yesterday = yesterdayCache,
            attemptedEvent = attemptedEvent
        )?.let { planned ->
            if (planned.prayerTime.toInstant().isAfter(now.toInstant())) return@let planned

            PrayerNotificationReceiver.deliverIfAllowed(
                appContext,
                planned.id,
                planned.triggerAt.toInstant(),
                planned.prayerTime.toInstant(),
                planned.locationCity,
                planned.locationCountry,
                exact
            )
            PrayerNotificationPlanner.nextEvent(
                todayCache, tomorrowCache, settings, now,
                shownEvents = shownEvents,
                recoverMissed = false
            )
        }
        if (event != null) {
            scheduleAlarm(appContext, event, exact)
            PrayerWidgetScheduler.enqueueRefresh(appContext, force = false)
        } else {
            PrayerNotificationAlarm.scheduleReplan(
                appContext,
                today.plusDays(1).atTime(LocalTime.of(0, 1)).atZone(location.timezone),
                exact
            )
            PrayerWidgetScheduler.requestBackgroundRefresh(appContext)
        }
    }
}

/** Keeps notification alarms independent from the widget alarm and its PendingIntent. */
internal object PrayerNotificationAlarm {
    const val ACTION = "io.github.ertugrulcetinkaya.namazvakti.action.PRAYER_NOTIFICATION"
    const val EXTRA_PRAYER = "notification_prayer"
    const val EXTRA_TYPE = "notification_type"
    const val EXTRA_TRIGGER_AT = "notification_trigger_at"
    const val EXTRA_PRAYER_TIME = "notification_prayer_time"
    const val EXTRA_PRAYER_DATE = "notification_prayer_date"
    const val EXTRA_LOCATION_CITY = "notification_location_city"
    const val EXTRA_LOCATION_COUNTRY = "notification_location_country"
    const val EXTRA_LEAD_MINUTES = "notification_lead_minutes"
    const val EXTRA_EXACT = "notification_exact"
    const val EXTRA_REPLAN_ONLY = "notification_replan_only"
    internal const val REQUEST_CODE = 6103
    private const val TAG = "PrayerNotification"

    fun schedule(
        context: Context,
        event: PrayerNotificationEvent,
        exact: Boolean,
        setAlarm: (Context, Long, PendingIntent, Boolean) -> Boolean = AlarmScheduling::set
    ): Intent {
        return scheduleAt(
            context,
            event.triggerAt,
            exact,
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
            },
            setAlarm
        )
    }

    fun scheduleReplan(context: Context, target: ZonedDateTime, exact: Boolean): Intent {
        return scheduleAt(
            context,
            target,
            exact,
            intent(context).putExtra(EXTRA_REPLAN_ONLY, true),
            AlarmScheduling::set
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntent(appContext))
    }

    private fun scheduleAt(
        context: Context,
        target: ZonedDateTime,
        exact: Boolean,
        intent: Intent,
        setAlarm: (Context, Long, PendingIntent, Boolean) -> Boolean
    ): Intent {
        val manager = context.getSystemService(AlarmManager::class.java)
        val triggerMillis = target.toInstant().toEpochMilli()
        val initialIntent = intent.putExtra(EXTRA_EXACT, exact)
        val operation = pendingIntent(context, initialIntent)
        manager.cancel(operation)
        val scheduledExact = setAlarm(context, triggerMillis, operation, exact)
        val scheduledIntent = initialIntent.putExtra(EXTRA_EXACT, scheduledExact)
        if (exact && !scheduledExact) {
            // PendingIntent extras do not affect its identity; update the token already scheduled inexactly.
            pendingIntent(context, scheduledIntent)
        }
        val precision = if (scheduledExact) "exact" else "inexact"
        Log.d(TAG, "next $precision notification alarm scheduled at=$target")
        return scheduledIntent
    }

    private fun pendingIntent(context: Context, intent: Intent = intent(context)): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun intent(context: Context) = Intent(context, PrayerNotificationReceiver::class.java)
        .setAction(ACTION)
        .setData("prayer-notification://next".toUri())
}
