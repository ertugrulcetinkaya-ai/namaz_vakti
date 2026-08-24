package com.example.namazvakti

import java.time.ZonedDateTime

/** Single source of truth for deciding whether cached prayer data is usable as today's data. */
class PrayerCachePolicy {
    fun isFresh(
        cache: CachedPrayerDay?,
        location: PrayerLocation,
        settings: PrayerCalculationSettings,
        now: ZonedDateTime
    ): Boolean {
        if (cache == null) return false
        val dateInCacheZone = now.withZoneSameInstant(cache.timezone).toLocalDate()
        return cache.matches(dateInCacheZone, location, settings)
    }
}
