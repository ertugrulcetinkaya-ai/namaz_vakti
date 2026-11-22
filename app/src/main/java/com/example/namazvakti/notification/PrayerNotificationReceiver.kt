package com.example.namazvakti.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.domain.policy.PrayerNotificationDeliveryPolicy
import com.example.namazvakti.domain.policy.PrayerNotificationEventId
import com.example.namazvakti.domain.policy.timeIn
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

class PrayerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerNotificationAlarm.ACTION) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, intent)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "notification alarm handling failed", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(PrayerNotificationAlarm.EXTRA_REPLAN_ONLY, false)) {
            PrayerNotificationScheduler.scheduleNext(context)
            PrayerWidgetScheduler.enqueueRefresh(context, force = false)
            return
        }

        val prayer = intent.getStringExtra(PrayerNotificationAlarm.EXTRA_PRAYER)
            ?.let { runCatching { PrayerNotificationPrayer.valueOf(it) }.getOrNull() }
        val type = intent.getStringExtra(PrayerNotificationAlarm.EXTRA_TYPE)
            ?.let { runCatching { PrayerNotificationType.valueOf(it) }.getOrNull() }
        val date = intent.getStringExtra(PrayerNotificationAlarm.EXTRA_PRAYER_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val triggerMillis = intent.getLongExtra(PrayerNotificationAlarm.EXTRA_TRIGGER_AT, Long.MIN_VALUE)
        val prayerMillis = intent.getLongExtra(PrayerNotificationAlarm.EXTRA_PRAYER_TIME, Long.MIN_VALUE)

        if (prayer == null || type == null || date == null ||
            triggerMillis == Long.MIN_VALUE || prayerMillis == Long.MIN_VALUE
        ) {
            PrayerNotificationScheduler.scheduleNext(context)
            return
        }

        val event = PrayerNotificationEventId(prayer, date, type)
        prayerNotificationMutex.withLock {
            deliverIfAllowed(
                context,
                event,
                Instant.ofEpochMilli(triggerMillis),
                Instant.ofEpochMilli(prayerMillis),
                intent.getStringExtra(PrayerNotificationAlarm.EXTRA_LOCATION_CITY),
                intent.getStringExtra(PrayerNotificationAlarm.EXTRA_LOCATION_COUNTRY),
                intent.getBooleanExtra(PrayerNotificationAlarm.EXTRA_EXACT, true)
            )
        }
        PrayerNotificationScheduler.scheduleNext(context, attemptedEvent = event)
        PrayerWidgetScheduler.enqueueRefresh(context, force = false)
    }

    companion object {
        private const val TAG = "PrayerNotification"

        internal suspend fun deliverIfAllowed(
            context: Context,
            event: PrayerNotificationEventId,
            scheduledTrigger: Instant,
            scheduledPrayer: Instant,
            locationCity: String?,
            locationCountry: String?,
            exact: Boolean
        ) {
            val container = context.appContainer()
            val settingsStore = container.notificationSettingsStore
            val settings = settingsStore.read()
            if (!settings.notificationsEnabled ||
                !NotificationManagerCompat.from(context).areNotificationsEnabled()
            ) return

            val location = container.store.readLocation()
            val cache = container.store.readCache(event.date, location, container.settings) ?: return
            val currentPrayerTime = event.date.atTime(event.prayer.timeIn(cache)).atZone(cache.timezone)
            val allowed = PrayerNotificationDeliveryPolicy.shouldShow(
                type = event.type,
                now = container.timeProvider.now().toInstant(),
                scheduledTriggerAt = scheduledTrigger,
                scheduledPrayerTime = scheduledPrayer,
                currentPrayerTime = currentPrayerTime.toInstant(),
                currentLeadMinutes = settings.leadMinutes,
                prayerEnabled = event.prayer in settings.enabledPrayers,
                locationMatches = cache.location.cacheKey == locationCity && cache.location.country == locationCountry,
                exact = exact,
                alreadyShown = event in settingsStore.readShownEvents()
            )
            if (!allowed) return

            PrayerNotificationPublisher.show(
                context,
                prayer = event.prayer,
                type = event.type,
                leadMinutes = settings.leadMinutes,
                prayerTime = currentPrayerTime,
                displayCity = cache.location.displayCity
            )
            settingsStore.recordShownEvent(event)
        }
    }
}
