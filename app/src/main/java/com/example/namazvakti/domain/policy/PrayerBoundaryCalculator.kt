package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.CachedPrayerDay
import java.time.ZonedDateTime

object PrayerBoundaryCalculator {
    fun calculateNextBoundary(
        cache: CachedPrayerDay,
        now: ZonedDateTime
    ): ZonedDateTime {
        val localNow = now.withZoneSameInstant(cache.timezone)
        require(cache.date == localNow.toLocalDate()) { "Cache must belong to the current local date" }
        val boundary = cache.prayerTimes.nextPrayerBoundary(localNow.toLocalTime())
        return if (boundary != null) {
            localNow.toLocalDate().atTime(boundary.time).atZone(cache.timezone)
        } else {
            localNow.toLocalDate().plusDays(1)
                .atTime(0, 0, 30)
                .atZone(cache.timezone)
        }
    }
}
