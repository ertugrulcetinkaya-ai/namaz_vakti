package com.example.namazvakti

import com.example.namazvakti.data.local.PrayerDayEntity
import com.example.namazvakti.data.local.toDomain
import com.example.namazvakti.data.local.toEntity
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerDayEntityTest {
    @Test
    fun domainEntityRoundTripPreservesCacheIdentityAndMetadata() {
        val cache = CachedPrayerDay(
            date = LocalDate.of(2026, 8, 23),
            location = PrayerLocation(
                "Ankara", "Turkey", "ANKARA", ZoneId.of("Europe/Istanbul")
            ),
            timezone = ZoneId.of("Europe/Istanbul"),
            settings = PrayerCalculationSettings(13, 1),
            prayerTimes = PrayerTimes(
                LocalTime.of(4, 29), LocalTime.of(6, 1), LocalTime.of(12, 56),
                LocalTime.of(16, 41), LocalTime.of(19, 42), LocalTime.of(21, 7)
            ),
            hijriText = "10 Rebiülevvel 1448",
            fetchedAt = Instant.parse("2026-08-23T08:00:00Z"),
            source = PrayerDataSource.ALADHAN
        )

        assertEquals(cache, cache.toEntity().toDomain())
    }

    @Test
    fun unknownStoredSourceFallsBackToLegacyLabel() {
        val entity = sampleEntity().copy(source = "REMOVED_PROVIDER")

        assertEquals(PrayerDataSource.LEGACY_CACHE, entity.toDomain().source)
    }

    private fun sampleEntity() = PrayerDayEntity(
        date = "2026-08-23",
        city = "Ankara",
        country = "Turkey",
        method = 13,
        school = 0,
        displayCity = "ANKARA",
        locationTimezone = "Europe/Istanbul",
        prayerTimezone = "Europe/Istanbul",
        fajr = "04:29",
        sunrise = "06:01",
        dhuhr = "12:56",
        asr = "16:41",
        maghrib = "19:42",
        isha = "21:07",
        hijriText = "10 Rebiülevvel 1448",
        fetchedAtEpochMillis = 1_777_105_600_000,
        source = PrayerDataSource.ALADHAN.name
    )
}
