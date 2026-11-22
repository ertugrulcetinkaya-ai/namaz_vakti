package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.PrayerNotificationType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

data class PrayerNotificationEventId(
    val prayer: PrayerNotificationPrayer,
    val date: LocalDate,
    val type: PrayerNotificationType
)

data class PrayerNotificationEvent(
    val prayer: PrayerNotificationPrayer,
    val type: PrayerNotificationType,
    val triggerAt: ZonedDateTime,
    val prayerTime: ZonedDateTime,
    val displayCity: String,
    val locationCity: String,
    val locationCountry: String,
    val leadMinutes: Int
) {
    val id: PrayerNotificationEventId
        get() = PrayerNotificationEventId(prayer, prayerTime.toLocalDate(), type)
}

object PrayerNotificationPlanner {
    fun nextEvent(
        today: CachedPrayerDay?,
        tomorrow: CachedPrayerDay?,
        settings: PrayerNotificationSettings,
        now: ZonedDateTime,
        exact: Boolean = true,
        shownEvents: Set<PrayerNotificationEventId> = emptySet(),
        yesterday: CachedPrayerDay? = null,
        recoverMissed: Boolean = true,
        attemptedEvent: PrayerNotificationEventId? = null
    ): PrayerNotificationEvent? {
        if (!settings.notificationsEnabled || settings.enabledPrayers.isEmpty()) return null

        val nowInstant = now.toInstant()
        val windowStart = nowInstant.minus(PrayerNotificationDeliveryPolicy.lateTolerance(exact))
        val candidates = listOfNotNull(yesterday, today, tomorrow)
            .distinctBy(CachedPrayerDay::date)
            .flatMap { cache ->
                val localToday = now.withZoneSameInstant(cache.timezone).toLocalDate()
                if (cache.date < windowStart.atZone(cache.timezone).toLocalDate() ||
                    cache.date > localToday.plusDays(1)
                ) {
                    return@flatMap emptyList()
                }

                settings.enabledPrayers.mapNotNull { prayer ->
                    val prayerTime = cache.date.atTime(prayer.timeIn(cache)).atZone(cache.timezone)
                    val prayerInstant = prayerTime.toInstant()
                    val isDue = !prayerInstant.isAfter(nowInstant)
                    if (isDue && (!recoverMissed || !prayerInstant.isAfter(windowStart))) {
                        return@mapNotNull null
                    }

                    val reminderAt = prayerTime.minusMinutes(settings.leadMinutes.toLong())
                    val reminderId = PrayerNotificationEventId(prayer, cache.date, PrayerNotificationType.REMINDER)
                    val reminderIsUpcoming = settings.leadMinutes > 0 &&
                        reminderAt.toInstant().isAfter(nowInstant) && reminderId !in shownEvents
                    val type = if (reminderIsUpcoming) PrayerNotificationType.REMINDER
                    else PrayerNotificationType.AT_TIME
                    val triggerAt = when {
                        isDue -> now.withZoneSameInstant(cache.timezone)
                        reminderIsUpcoming -> reminderAt
                        else -> prayerTime
                    }
                    PrayerNotificationEvent(
                        prayer = prayer,
                        type = type,
                        triggerAt = triggerAt,
                        prayerTime = prayerTime,
                        displayCity = cache.location.displayName,
                        locationCity = cache.location.cacheKey,
                        locationCountry = cache.location.country,
                        leadMinutes = settings.leadMinutes
                    ).takeUnless { it.id in shownEvents || (isDue && it.id == attemptedEvent) }
                }
            }

        return candidates
            .filter { !it.prayerTime.toInstant().isAfter(nowInstant) }
            .maxByOrNull { it.prayerTime.toInstant() }
            ?: candidates.minByOrNull { it.triggerAt.toInstant() }
    }
}

internal fun PrayerNotificationPrayer.timeIn(cache: CachedPrayerDay) =
    when (this) {
        PrayerNotificationPrayer.IMSAK -> cache.prayerTimes.fajr
        PrayerNotificationPrayer.SUNRISE -> cache.prayerTimes.sunrise
        PrayerNotificationPrayer.DHUHR -> cache.prayerTimes.dhuhr
        PrayerNotificationPrayer.ASR -> cache.prayerTimes.asr
        PrayerNotificationPrayer.MAGHRIB -> cache.prayerTimes.maghrib
        PrayerNotificationPrayer.ISHA -> cache.prayerTimes.isha
    }

object PrayerNotificationDeliveryPolicy {
    private val maxPrayerTimeShift = Duration.ofMinutes(2)

    fun lateTolerance(exact: Boolean): Duration = Duration.ofMinutes(if (exact) 10 else 30)

    fun shouldShow(
        type: PrayerNotificationType,
        now: Instant,
        scheduledTriggerAt: Instant,
        scheduledPrayerTime: Instant,
        currentPrayerTime: Instant?,
        currentLeadMinutes: Int,
        prayerEnabled: Boolean,
        locationMatches: Boolean = true,
        exact: Boolean = true,
        alreadyShown: Boolean = false
    ): Boolean {
        if (!prayerEnabled || !locationMatches || alreadyShown || currentPrayerTime == null) return false
        if (Duration.between(scheduledPrayerTime, currentPrayerTime).abs() > maxPrayerTimeShift) return false
        if (now.isBefore(scheduledTriggerAt)) return false

        val deadlineBase = when (type) {
            PrayerNotificationType.AT_TIME -> {
                if (scheduledTriggerAt.isBefore(scheduledPrayerTime)) return false
                scheduledPrayerTime
            }
            PrayerNotificationType.REMINDER -> {
                if (currentLeadMinutes <= 0 || !now.isBefore(currentPrayerTime)) return false
                if (scheduledPrayerTime.minusSeconds(currentLeadMinutes * 60L) != scheduledTriggerAt) return false
                scheduledTriggerAt
            }
        }
        return !now.isAfter(deadlineBase.plus(lateTolerance(exact)))
    }
}
