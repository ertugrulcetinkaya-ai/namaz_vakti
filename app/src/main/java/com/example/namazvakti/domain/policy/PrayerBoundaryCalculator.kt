package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.CachedPrayerDay
import java.time.ZonedDateTime

object PrayerBoundaryCalculator {
    fun calculateNextBoundary(
        cache: CachedPrayerDay,
        now: ZonedDateTime,
        nextDayCache: CachedPrayerDay? = null
    ): ZonedDateTime {
        val localNow = now.withZoneSameInstant(cache.timezone)
        require(cache.date == localNow.toLocalDate()) { "Cache must belong to the current local date" }
        val boundary = cache.prayerTimes.nextPrayerBoundary(localNow.toLocalTime())
        return if (boundary != null) {
            localNow.toLocalDate().atTime(boundary.time).atZone(cache.timezone)
        } else {
            val tomorrow = localNow.toLocalDate().plusDays(1)
            if (nextDayCache?.date == tomorrow) {
                tomorrow.atTime(nextDayCache.prayerTimes.fajr).atZone(nextDayCache.timezone)
            } else {
                tomorrow.atStartOfDay(cache.timezone).plusMinutes(1)
            }
        }
    }
}
