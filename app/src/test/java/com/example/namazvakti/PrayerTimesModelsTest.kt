package com.example.namazvakti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import java.time.Clock
import java.time.ZonedDateTime

class PrayerTimesModelsTest {
    @Test
    fun defaultsToShafiSchoolForAsrCalculation() {
        assertEquals(
            PrayerCalculationSettings.SCHOOL_SHAFI,
            PrayerCalculationSettings().school
        )
    }

    @Test
    fun parsesWidgetTextOnlyWhenAllSixValuesExist() {
        val times = parsePrayerTimesText("İmsak 04:12 • Güneş 05:49 • Öğle 13:08 • İkindi 17:02 • Akşam 20:21 • Yatsı 22:01")

        assertEquals("17:02", times?.asr?.toString())
        assertNull(parsePrayerTimesText("İmsak 04:12 • Güneş 05:49"))
    }

    @Test
    fun prayerBoundariesHandleMidnightAndExactPrayerTimes() {
        val times = PrayerTimes(
            fajr = LocalTime.of(4, 12),
            sunrise = LocalTime.of(5, 49),
            dhuhr = LocalTime.of(13, 8),
            asr = LocalTime.of(17, 2),
            maghrib = LocalTime.of(20, 21),
            isha = LocalTime.of(22, 1)
        )

        assertNull(times.nextPrayerBoundary(LocalTime.of(23, 30)))
        assertEquals("Güneş", times.nextPrayerBoundary(LocalTime.of(4, 12))?.name)
        assertEquals("Öğle", times.nextPrayerBoundary(LocalTime.of(5, 49))?.name)
    }

    @Test
    fun nextBoundaryBeforeFajrIsFajr() {
        val times = sampleTimes()

        val boundary = times.nextPrayerBoundary(LocalTime.of(3, 0))

        assertEquals("İmsak", boundary?.name)
        assertEquals(LocalTime.of(4, 12), boundary?.time)
    }

    @Test
    fun schedulerBoundaryTargetsTodayOrTomorrowCorrectly() {
        val cache = sampleCache()
        val zone = PrayerTimeProvider.DEFAULT_ZONE

        assertEquals(
            LocalDate.of(2026, 8, 6).atTime(4, 12),
            PrayerBoundaryCalculator.calculateNextBoundary(
                cache, ZonedDateTime.of(2026, 8, 6, 3, 0, 0, 0, zone)
            ).toLocalDateTime()
        )
        assertEquals(
            LocalDate.of(2026, 8, 6).atTime(5, 49),
            PrayerBoundaryCalculator.calculateNextBoundary(
                cache, ZonedDateTime.of(2026, 8, 6, 4, 12, 0, 0, zone)
            ).toLocalDateTime()
        )
        assertEquals(
            LocalDate.of(2026, 8, 7).atTime(4, 12),
            PrayerBoundaryCalculator.calculateNextBoundary(
                cache, ZonedDateTime.of(2026, 8, 6, 22, 30, 0, 0, zone)
            ).toLocalDateTime()
        )
    }

    @Test
    fun clockProviderMakesDateDeterministic() {
        val provider = PrayerTimeProvider(
            Clock.fixed(Instant.parse("2026-08-06T18:00:00Z"), ZoneId.of("Europe/Istanbul"))
        )

        assertEquals(LocalDate.of(2026, 8, 6), provider.today())
        assertEquals(21, provider.now().hour)
    }

    private fun sampleTimes() = PrayerTimes(
        LocalTime.of(4, 12), LocalTime.of(5, 49), LocalTime.of(13, 8),
        LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
    )

    private fun sampleCache() = CachedPrayerDay(
        date = LocalDate.of(2026, 8, 6),
        location = PrayerLocation("Ankara", "Turkey", "ANKARA"),
        timezone = PrayerTimeProvider.DEFAULT_ZONE,
        settings = PrayerCalculationSettings(),
        prayerTimes = sampleTimes(),
        hijriText = null,
        fetchedAt = Instant.parse("2026-08-06T00:00:00Z")
    )
}
