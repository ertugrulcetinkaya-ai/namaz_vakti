package com.example.namazvakti.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import com.example.namazvakti.domain.policy.ReligiousDayNotificationDeliveryPolicy
import com.example.namazvakti.widget.launchAsync
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReligiousDayNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReligiousDayNotificationAlarm.ACTION) return
        val appContext = context.applicationContext
        launchAsync(appContext, TAG, "religious-day notification alarm handling failed") {
            handle(appContext, intent)
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val container = context.appContainer()
        val enabled = container.notificationSettingsStore.read().religiousDaysEnabled
        val date = intent.getStringExtra(ReligiousDayNotificationAlarm.EXTRA_EVENT_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val triggerMillis = intent.getLongExtra(ReligiousDayNotificationAlarm.EXTRA_TRIGGER_AT, Long.MIN_VALUE)
        if (date == null || triggerMillis == Long.MIN_VALUE) {
            ReligiousDayNotificationScheduler.scheduleNext(context)
            return
        }

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val scheduled = Instant.ofEpochMilli(triggerMillis)
        val events = DiyanetReligiousDays.on(date)
        if (events.isNotEmpty()) {
            val allowed = ReligiousDayNotificationDeliveryPolicy.shouldShow(
                now = now.toInstant(),
                scheduledTriggerAt = scheduled,
                eventDate = date,
                deviceZone = zone,
                enabled = enabled
            )
            if (allowed) PrayerNotificationPublisher.show(context, events)
        }
        ReligiousDayNotificationScheduler.scheduleNext(context)
    }

    private companion object {
        const val TAG = "ReligiousDayNotification"
    }
}
