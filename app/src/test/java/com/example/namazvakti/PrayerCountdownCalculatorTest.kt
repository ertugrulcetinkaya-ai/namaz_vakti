package com.example.namazvakti

import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.policy.PrayerCountdownCalculator
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrayerCountdownCalculatorTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val date = LocalDate.of(2026, 4, 3)

    @Test
    fun daytimeUsesSharedActivePrayerAndNextBoundary() {
        val result = calculate("16:00", today = prayerTimes())

        assertEquals(PrayerTimeName.DHUHR, result.activePrayer)
        assertEquals(PrayerTimeName.ASR, result.nextPrayer)
        assertEquals(date.atTime(17, 2).atZone(zone), result.nextPrayerAt)
        assertEquals(Duration.ofMinutes(62), result.remaining)
    }

    @Test
    fun exactPrayerBoundaryBecomesActiveAndAdvancesNextPrayer() {
        val result = calculate("04:12", today = prayerTimes())

        assertEquals(PrayerTimeName.IMSAK, result.activePrayer)
        assertEquals(PrayerTimeName.SUNRISE, result.nextPrayer)
        assertEquals(date.atTime(5, 49).atZone(zone), result.nextPrayerAt)
    }

    @Test
    fun afterIshaUsesTomorrowFajrWhenTomorrowCacheExists() {
        val result = calculate("22:02", today = prayerTimes(), tomorrow = prayerTimes(fajr = "04:15"))

        assertEquals(PrayerTimeName.ISHA, result.activePrayer)
        assertEquals(PrayerTimeName.IMSAK, result.nextPrayer)
        assertEquals(date.plusDays(1).atTime(4, 15).atZone(zone), result.nextPrayerAt)
        assertEquals(Duration.ofHours(6).plusMinutes(13), result.remaining)
    }

    @Test
    fun afterIshaWithoutTomorrowCacheHasNoKnownNextBoundary() {
        val result = calculate("22:02", today = prayerTimes())

        assertEquals(PrayerTimeName.ISHA, result.activePrayer)
        assertNull(result.nextPrayer)
        assertNull(result.nextPrayerAt)
        assertNull(result.remaining)
    }

    @Test
    fun afterMidnightBeforeFajrUsesCurrentDateImsak() {
        val result = calculate("01:00", today = prayerTimes())

        assertEquals(PrayerTimeName.ISHA, result.activePrayer)
        assertEquals(PrayerTimeName.IMSAK, result.nextPrayer)
        assertEquals(date.atTime(4, 12).atZone(zone), result.nextPrayerAt)
        assertEquals(Duration.ofHours(3).plusMinutes(12), result.remaining)
    }

    @Test
    fun convertsNowToCacheTimezoneBeforeSelectingBoundary() {
        val now = ZonedDateTime.parse("2026-04-03T20:00:00Z")
        val result = PrayerCountdownCalculator.calculate(
            today = prayerTimes(),
            date = date,
            timezone = zone,
            tomorrow = prayerTimes(fajr = "04:15"),
            now = now
        )

        assertEquals(PrayerTimeName.ISHA, result.activePrayer)
        assertEquals(date.plusDays(1).atTime(4, 15).atZone(zone), result.nextPrayerAt)
        assertEquals(Duration.ofHours(5).plusMinutes(15), result.remaining)
    }

    @Test
    fun remainingDurationPreservesSecondAccuracy() {
        val result = calculate("16:01:37", today = prayerTimes())

        assertEquals(Duration.ofHours(1).plusSeconds(23), result.remaining)
    }

    private fun calculate(
        now: String,
        today: PrayerTimes,
        tomorrow: PrayerTimes? = null
    ) = PrayerCountdownCalculator.calculate(
        today = today,
        date = date,
        timezone = zone,
        tomorrow = tomorrow,
        now = date.atTime(LocalTime.parse(now)).atZone(zone)
    )

    private fun prayerTimes(fajr: String = "04:12") = PrayerTimes(
        fajr = LocalTime.parse(fajr),
        sunrise = LocalTime.of(5, 49),
        dhuhr = LocalTime.of(13, 8),
        asr = LocalTime.of(17, 2),
        maghrib = LocalTime.of(20, 21),
        isha = LocalTime.of(22, 1)
    )
}
