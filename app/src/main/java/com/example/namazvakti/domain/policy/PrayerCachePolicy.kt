package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
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
