package com.example.namazvakti

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.widget.PrayerWidgetChronometerBase
import com.example.namazvakti.widget.PrayerWidgetCountdownCalculator
import com.example.namazvakti.widget.renderer.PrayerWidgetDataState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrayerWidgetCountdownCalculatorTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val date = LocalDate.of(2026, 4, 3)
    private val location = PrayerLocation("Istanbul", "Turkey", "İSTANBUL", zone)
    private val settings = PrayerCalculationSettings()

    @Test
    fun chronometerBaseAddsRemainingTimeToElapsedRealtime() {
        val now = at("16:00:00")
        val target = at("17:02:00")

        assertEquals(3_820_000L, PrayerWidgetChronometerBase.calculate(now, target, 100_000L))
    }

    @Test
    fun chronometerDoesNotStartAtOrAfterBoundary() {
        val now = at("17:02:00")

        assertNull(PrayerWidgetChronometerBase.calculate(now, now, 100_000L))
        assertNull(PrayerWidgetChronometerBase.calculate(at("17:03:00"), now, 100_000L))
    }

    @Test
    fun afterIshaUsesTomorrowImsakWhenTomorrowIsCached() {
        val tomorrow = cache(date.plusDays(1), fajr = "04:15")
        val result = PrayerWidgetCountdownCalculator.calculate(
            cache = cache(date),
            tomorrowCache = tomorrow,
            now = at("22:02:00"),
            elapsedRealtime = 900_000L,
            dataState = PrayerWidgetDataState.Fresh
        )

        assertEquals(PrayerTimeName.IMSAK, result?.prayer)
        assertEquals(date.plusDays(1).atTime(4, 15).atZone(zone), result?.targetAt)
        assertEquals(23_280_000L, result?.chronometerBase)
    }

    @Test
    fun afterIshaWithoutTomorrowCacheHidesCountdown() {
        val result = PrayerWidgetCountdownCalculator.calculate(
            cache = cache(date),
            tomorrowCache = null,
            now = at("22:02:00"),
            elapsedRealtime = 900_000L,
            dataState = PrayerWidgetDataState.Fresh
        )

        assertNull(result)
    }

    @Test
    fun staleDataNeverStartsCountdown() {
        val result = PrayerWidgetCountdownCalculator.calculate(
            cache = cache(date),
            tomorrowCache = cache(date.plusDays(1)),
            now = at("16:00:00"),
            elapsedRealtime = 900_000L,
            dataState = PrayerWidgetDataState.Stale
        )

        assertNull(result)
    }

    private fun at(time: String): ZonedDateTime = date.atTime(LocalTime.parse(time)).atZone(zone)

    private fun cache(date: LocalDate, fajr: String = "04:12") = CachedPrayerDay(
        date = date,
        location = location,
        timezone = zone,
        settings = settings,
        prayerTimes = PrayerTimes(
            fajr = LocalTime.parse(fajr),
            sunrise = LocalTime.of(5, 49),
            dhuhr = LocalTime.of(13, 8),
            asr = LocalTime.of(17, 2),
            maghrib = LocalTime.of(20, 21),
            isha = LocalTime.of(22, 1)
        ),
        hijriText = "24 Ramazan 1447",
        fetchedAt = Instant.parse("2026-04-03T10:00:00Z")
    )
}
