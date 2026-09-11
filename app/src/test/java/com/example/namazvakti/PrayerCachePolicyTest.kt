package com.example.namazvakti

import com.example.namazvakti.app.*
import com.example.namazvakti.data.local.*
import com.example.namazvakti.data.remote.*
import com.example.namazvakti.data.repository.*
import com.example.namazvakti.domain.model.*
import com.example.namazvakti.domain.policy.*
import com.example.namazvakti.domain.port.*
import com.example.namazvakti.ui.main.*
import com.example.namazvakti.widget.*
import com.example.namazvakti.widget.alarm.*
import com.example.namazvakti.widget.renderer.*
import com.example.namazvakti.widget.worker.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerCachePolicyTest {
    private val policy = PrayerCachePolicy()
    private val location = PrayerLocation("Ankara", "Turkey", "ANKARA")
    private val settings = PrayerCalculationSettings()

    @Test
    fun freshnessUsesTheCacheTimezoneInsteadOfTheDeviceDate() {
        val cache = cache(date = LocalDate.of(2026, 8, 7), zone = ZoneId.of("Europe/Istanbul"))
        val sameInstantStillPreviousDayInUtc = ZonedDateTime.parse("2026-08-06T21:30:00Z")

        assertTrue(policy.isFresh(cache, location, settings, sameInstantStillPreviousDayInUtc))
    }

    @Test
    fun missingWrongLocationAndWrongSettingsAreNeverFresh() {
        val now = ZonedDateTime.parse("2026-08-07T12:00:00+03:00[Europe/Istanbul]")
        val cache = cache(LocalDate.of(2026, 8, 7), ZoneId.of("Europe/Istanbul"))

        assertFalse(policy.isFresh(null, location, settings, now))
        assertFalse(policy.isFresh(cache, location.copy(city = "Istanbul"), settings, now))
        assertFalse(policy.isFresh(cache, location, settings.copy(method = 3), now))
    }

    private fun cache(date: LocalDate, zone: ZoneId) = CachedPrayerDay(
        date = date,
        location = location,
        timezone = zone,
        settings = settings,
        prayerTimes = PrayerTimes(
            LocalTime.of(4, 12), LocalTime.of(5, 49), LocalTime.of(13, 8),
            LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
        ),
        hijriText = null,
        fetchedAt = Instant.parse("2026-08-06T00:00:00Z")
    )
}
