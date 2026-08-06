package com.example.namazvakti

import java.time.ZonedDateTime

object PrayerBoundaryCalculator {
    fun calculateNextBoundary(cache: CachedPrayerDay, now: ZonedDateTime): ZonedDateTime {
        val localNow = now.withZoneSameInstant(cache.timezone)
        val boundary = cache.prayerTimes.nextPrayerBoundary(localNow.toLocalTime())
        val target = if (boundary != null) {
            localNow.toLocalDate().atTime(boundary.time).atZone(cache.timezone)
        } else {
            localNow.toLocalDate().plusDays(1).atTime(cache.prayerTimes.fajr).atZone(cache.timezone)
        }
        return if (target.isAfter(localNow)) target else target.plusDays(1)
    }
}
