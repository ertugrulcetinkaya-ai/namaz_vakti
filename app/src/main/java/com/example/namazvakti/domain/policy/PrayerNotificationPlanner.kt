package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.PrayerNotificationType
import java.time.Instant
import java.time.ZonedDateTime

data class PrayerNotificationEvent(
    val prayer: PrayerNotificationPrayer,
    val type: PrayerNotificationType,
    val triggerAt: ZonedDateTime,
    val prayerTime: ZonedDateTime,
    val displayCity: String,
    val locationCity: String,
    val locationCountry: String,
    val leadMinutes: Int
)

object PrayerNotificationPlanner {
    fun nextEvent(
        today: CachedPrayerDay?,
        tomorrow: CachedPrayerDay?,
        settings: PrayerNotificationSettings,
        now: ZonedDateTime
    ): PrayerNotificationEvent? {
        if (!settings.notificationsEnabled || settings.enabledPrayers.isEmpty()) return null

        val candidates = listOfNotNull(today, tomorrow)
            .distinctBy(CachedPrayerDay::date)
            .flatMap { cache ->
                val localToday = now.withZoneSameInstant(cache.timezone).toLocalDate()
                if (cache.date < localToday || cache.date > localToday.plusDays(1)) {
                    return@flatMap emptyList()
                }

                PrayerNotificationPrayer.entries.mapNotNull { prayer ->
                    if (prayer !in settings.enabledPrayers) return@mapNotNull null
                    val localTime = prayer.timeIn(cache)
                    val prayerTime = cache.date.atTime(localTime).atZone(cache.timezone)
                    if (!prayerTime.toInstant().isAfter(now.toInstant())) return@mapNotNull null

                    val reminderAt = prayerTime.minusMinutes(settings.leadMinutes.toLong())
                    val reminderIsUpcoming = settings.leadMinutes > 0 &&
                        reminderAt.toInstant().isAfter(now.toInstant())
                    PrayerNotificationEvent(
                        prayer = prayer,
                        type = if (reminderIsUpcoming) PrayerNotificationType.REMINDER
                        else PrayerNotificationType.AT_TIME,
                        triggerAt = if (reminderIsUpcoming) reminderAt else prayerTime,
                        prayerTime = prayerTime,
                        displayCity = cache.location.displayCity,
                        locationCity = cache.location.city,
                        locationCountry = cache.location.country,
                        leadMinutes = settings.leadMinutes
                    )
                }
            }

        return candidates.minByOrNull { it.triggerAt.toInstant() }
    }
}

private fun PrayerNotificationPrayer.timeIn(cache: CachedPrayerDay) =
    when (this) {
        PrayerNotificationPrayer.IMSAK -> cache.prayerTimes.fajr
        PrayerNotificationPrayer.SUNRISE -> cache.prayerTimes.sunrise
        PrayerNotificationPrayer.DHUHR -> cache.prayerTimes.dhuhr
        PrayerNotificationPrayer.ASR -> cache.prayerTimes.asr
        PrayerNotificationPrayer.MAGHRIB -> cache.prayerTimes.maghrib
        PrayerNotificationPrayer.ISHA -> cache.prayerTimes.isha
    }

object PrayerNotificationDeliveryPolicy {
    private const val MAX_LATE_MINUTES = 10L

    fun shouldShow(
        type: PrayerNotificationType,
        now: Instant,
        scheduledTriggerAt: Instant,
        scheduledPrayerTime: Instant,
        currentPrayerTime: Instant?,
        currentLeadMinutes: Int,
        prayerEnabled: Boolean,
        locationMatches: Boolean = true
    ): Boolean {
        if (!prayerEnabled || !locationMatches || currentPrayerTime != scheduledPrayerTime) return false
        if (now.isBefore(scheduledTriggerAt)) return false
        if (now.isAfter(scheduledTriggerAt.plusSeconds(MAX_LATE_MINUTES * 60))) return false

        val expectedTrigger = when (type) {
            PrayerNotificationType.AT_TIME -> scheduledPrayerTime
            PrayerNotificationType.REMINDER -> {
                if (currentLeadMinutes <= 0) return false
                scheduledPrayerTime.minusSeconds(currentLeadMinutes * 60L)
            }
        }
        if (expectedTrigger != scheduledTriggerAt) return false
        if (type == PrayerNotificationType.REMINDER && !now.isBefore(currentPrayerTime)) return false
        return true
    }
}
