package com.example.namazvakti

import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.RamadanRange
import com.example.namazvakti.domain.policy.RamadanCountdownCalculator
import com.example.namazvakti.domain.policy.RamadanCountdownKind
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RamadanCountdownCalculatorTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val range = RamadanRange(
        hijriYear = 1448,
        startDate = LocalDate.of(2027, 2, 8),
        endDate = LocalDate.of(2027, 3, 8),
        eidDate = LocalDate.of(2027, 3, 9),
        estimated = false
    )

    @Test
    fun firstRamadanDayBeforeImsakCountsDownToThatDaysImsak() {
        val result = calculate("2027-02-08T04:20:00+03:00", date("2027-02-08"))

        assertEquals(RamadanCountdownKind.SAHUR, result?.kind)
        assertEquals(1, result?.dayNumber)
        assertEquals(at("2027-02-08", "05:30"), result?.targetAt)
        assertEquals(Duration.ofHours(1).plusMinutes(10), result?.remaining)
    }

    @Test
    fun daytimeCountsDownToIftarAndIncludesRamadanDay() {
        val result = calculate("2027-02-08T14:00:00+03:00", date("2027-02-08"))

        assertEquals(RamadanCountdownKind.IFTAR, result?.kind)
        assertEquals(1, result?.dayNumber)
        assertEquals(at("2027-02-08", "18:00"), result?.targetAt)
        assertEquals(Duration.ofHours(4), result?.remaining)
    }

    @Test
    fun afterIftarCountsDownToNextRamadanDaysImsak() {
        val today = date("2027-02-10")
        val result = calculate(
            "2027-02-10T20:30:00+03:00",
            today,
            tomorrow = prayerTimes(fajr = "05:25")
        )

        assertEquals(RamadanCountdownKind.SAHUR, result?.kind)
        assertEquals(3, result?.dayNumber)
        assertEquals(at("2027-02-11", "05:25"), result?.targetAt)
        assertEquals(Duration.ofHours(8).plusMinutes(55), result?.remaining)
    }

    @Test
    fun lastRamadanDayAfterIftarHasNoSahurCountdown() {
        val result = calculate(
            "2027-03-08T20:30:00+03:00",
            date("2027-03-08"),
            tomorrow = prayerTimes(fajr = "05:15")
        )

        assertNull(result)
    }

    @Test
    fun outsideRamadanHasNoCountdown() {
        val result = calculate("2027-02-07T14:00:00+03:00", date("2027-02-07"))

        assertNull(result)
    }

    private fun calculate(
        now: String,
        date: LocalDate,
        tomorrow: PrayerTimes? = null
    ) = RamadanCountdownCalculator.calculate(
        date = date,
        range = range,
        today = prayerTimes(),
        tomorrow = tomorrow,
        timezone = zone,
        now = ZonedDateTime.parse(now)
    )

    private fun prayerTimes(fajr: String = "05:30") = PrayerTimes(
        fajr = LocalTime.parse(fajr),
        sunrise = LocalTime.of(6, 45),
        dhuhr = LocalTime.of(12, 30),
        asr = LocalTime.of(15, 45),
        maghrib = LocalTime.of(18, 0),
        isha = LocalTime.of(19, 30)
    )

    private fun date(value: String) = LocalDate.parse(value)

    private fun at(date: String, time: String) = date(date).atTime(LocalTime.parse(time)).atZone(zone)
}
