package com.example.namazvakti.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import com.example.namazvakti.domain.policy.ReligiousDayNotificationDeliveryPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReligiousDayNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReligiousDayNotificationAlarm.ACTION) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, intent)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "religious-day notification alarm handling failed", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val container = context.appContainer()
        val enabled = container.notificationSettingsStore.read().religiousDaysEnabled
        val date = intent.getStringExtra(ReligiousDayNotificationAlarm.EXTRA_EVENT_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val name = intent.getStringExtra(ReligiousDayNotificationAlarm.EXTRA_EVENT_NAME)
        val triggerMillis = intent.getLongExtra(ReligiousDayNotificationAlarm.EXTRA_TRIGGER_AT, Long.MIN_VALUE)
        if (date == null || name == null || triggerMillis == Long.MIN_VALUE) {
            ReligiousDayNotificationScheduler.scheduleNext(context)
            return
        }

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val scheduled = Instant.ofEpochMilli(triggerMillis)
        val event = DiyanetReligiousDays.on(date).firstOrNull { it.name == name }
        if (event != null) {
            val allowed = ReligiousDayNotificationDeliveryPolicy.shouldShow(
                now = now.toInstant(),
                scheduledTriggerAt = scheduled,
                eventDate = date,
                deviceZone = zone,
                enabled = enabled
            )
            if (allowed) PrayerNotificationPublisher.show(context, event)
        }
        ReligiousDayNotificationScheduler.scheduleNext(context)
    }

    private companion object {
        const val TAG = "ReligiousDayNotification"
    }
}
