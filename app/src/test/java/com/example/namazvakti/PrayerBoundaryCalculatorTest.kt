package com.example.namazvakti

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.policy.PrayerBoundaryCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerBoundaryCalculatorTest {
    private val zone = ZoneId.of("Europe/Istanbul")

    @Test
    fun calculatesTheNextBoundaryInTheCacheTimezone() {
        val cache = cache(LocalDate.of(2026, 8, 6))
        val now = ZonedDateTime.parse("2026-08-06T08:00:00Z")

        val target = PrayerBoundaryCalculator.calculateNextBoundary(cache, now)

        assertEquals(
            ZonedDateTime.of(2026, 8, 6, 13, 8, 0, 0, zone),
            target
        )
    }

    @Test
    fun afterIshaSchedulesTomorrowMidnightPlusThirtySeconds() {
        val today = LocalDate.of(2026, 8, 6)
        val cache = cache(today)
        val now = ZonedDateTime.of(today, LocalTime.of(23, 30), zone)

        val target = PrayerBoundaryCalculator.calculateNextBoundary(cache, now)

        assertEquals(
            ZonedDateTime.of(today.plusDays(1), LocalTime.of(0, 0, 30), zone),
            target
        )
    }

    @Test
    fun afterMidnightBeforeFajrSchedulesTodaysFajr() {
        val today = LocalDate.of(2026, 8, 7)
        val now = ZonedDateTime.of(today, LocalTime.of(0, 10), zone)

        val target = PrayerBoundaryCalculator.calculateNextBoundary(
            cache(today).copy(prayerTimes = cache(today).prayerTimes.copy(fajr = LocalTime.of(4, 10))),
            now
        )

        assertEquals(
            ZonedDateTime.of(today, LocalTime.of(4, 10), zone),
            target
        )
    }

    @Test
    fun afterIshaUsesCacheTimezoneWhenNowHasDifferentTimezone() {
        val today = LocalDate.of(2026, 8, 6)
        val cache = cache(today)
        val now = ZonedDateTime.parse("2026-08-06T20:30:00Z")

        val target = PrayerBoundaryCalculator.calculateNextBoundary(cache, now)

        assertEquals(
            ZonedDateTime.of(today.plusDays(1), LocalTime.of(0, 0, 30), zone),
            target
        )
        assertEquals(zone, target.zone)
    }

    private fun cache(date: LocalDate) = CachedPrayerDay(
        date = date,
        location = PrayerLocation("Ankara", "Turkey", "ANKARA", zone),
        timezone = zone,
        settings = PrayerCalculationSettings(),
        prayerTimes = PrayerTimes(
            LocalTime.of(4, 12), LocalTime.of(5, 49), LocalTime.of(13, 8),
            LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
        ),
        hijriText = null,
        fetchedAt = Instant.parse("2026-08-06T00:00:00Z")
    )
}
