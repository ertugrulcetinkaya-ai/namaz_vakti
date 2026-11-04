package com.example.namazvakti.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.domain.policy.PrayerNotificationDeliveryPolicy
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        val container = context.appContainer()
        val settings = container.notificationSettingsStore.read()
        if (!settings.notificationsEnabled) {
            PrayerNotificationAlarm.cancel(context)
            return
        }

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
        val leadMinutes = intent.getIntExtra(PrayerNotificationAlarm.EXTRA_LEAD_MINUTES, -1)

        if (prayer == null || type == null || date == null ||
            triggerMillis == Long.MIN_VALUE || prayerMillis == Long.MIN_VALUE
        ) {
            PrayerNotificationScheduler.scheduleNext(context)
            return
        }

        val location = container.store.readLocation()
        val cache = container.store.readCache(date, location, container.settings)
        val currentPrayerTime = cache?.let {
            date.atTime(prayer.timeIn(it)).atZone(it.timezone).toInstant()
        }
        val locationMatches = cache != null &&
            cache.location.city == intent.getStringExtra(PrayerNotificationAlarm.EXTRA_LOCATION_CITY) &&
            cache.location.country == intent.getStringExtra(PrayerNotificationAlarm.EXTRA_LOCATION_COUNTRY)
        val scheduledTrigger = Instant.ofEpochMilli(triggerMillis)
        val scheduledPrayer = Instant.ofEpochMilli(prayerMillis)
        val allowed = PrayerNotificationDeliveryPolicy.shouldShow(
            type = type,
            now = container.timeProvider.now().toInstant(),
            scheduledTriggerAt = scheduledTrigger,
            scheduledPrayerTime = scheduledPrayer,
            currentPrayerTime = currentPrayerTime,
            currentLeadMinutes = settings.leadMinutes,
            prayerEnabled = prayer in settings.enabledPrayers,
            locationMatches = locationMatches
        )

        if (allowed && cache != null) {
            PrayerNotificationPublisher.show(
                context,
                prayer = prayer,
                type = type,
                leadMinutes = settings.leadMinutes,
                prayerTime = scheduledPrayer.atZone(cache.timezone),
                displayCity = cache.location.displayCity
            )
        }
        PrayerNotificationScheduler.scheduleNext(context)
        PrayerWidgetScheduler.enqueueRefresh(context, force = false)
    }

    private companion object {
        const val TAG = "PrayerNotification"
    }
}

private fun PrayerNotificationPrayer.timeIn(cache: com.example.namazvakti.domain.model.CachedPrayerDay) =
    when (this) {
        PrayerNotificationPrayer.IMSAK -> cache.prayerTimes.fajr
        PrayerNotificationPrayer.SUNRISE -> cache.prayerTimes.sunrise
        PrayerNotificationPrayer.DHUHR -> cache.prayerTimes.dhuhr
        PrayerNotificationPrayer.ASR -> cache.prayerTimes.asr
        PrayerNotificationPrayer.MAGHRIB -> cache.prayerTimes.maghrib
        PrayerNotificationPrayer.ISHA -> cache.prayerTimes.isha
    }
