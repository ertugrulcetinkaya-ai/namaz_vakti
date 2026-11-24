package com.example.namazvakti

import com.example.namazvakti.domain.model.PrayerTimeOffsets
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.withOffsets
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerTimeOffsetsTest {
    @Test
    fun appliesPositiveAndNegativeMinuteOffsets() {
        val adjusted = prayerTimes().withOffsets(
            PrayerTimeOffsets(imsakMinutes = 7, dhuhrMinutes = -4)
        )

        assertEquals(LocalTime.of(4, 19), adjusted.fajr)
        assertEquals(LocalTime.of(13, 4), adjusted.dhuhr)
        assertEquals(LocalTime.of(5, 49), adjusted.sunrise)
    }

    @Test
    fun clampsAtMidnightAndEndOfDayWithoutChangingTheDate() {
        val adjusted = prayerTimes(
            fajr = LocalTime.of(0, 5),
            isha = LocalTime.of(23, 50)
        ).withOffsets(PrayerTimeOffsets(imsakMinutes = -30, ishaMinutes = 30))

        assertEquals(LocalTime.MIDNIGHT, adjusted.fajr)
        assertEquals(LocalTime.of(23, 59), adjusted.isha)
    }

    @Test
    fun keepsStrictPrayerOrderBySkippingOnlyAnInvalidShift() {
        val adjusted = prayerTimes(
            fajr = LocalTime.of(4, 10),
            sunrise = LocalTime.of(4, 30),
            dhuhr = LocalTime.of(4, 50)
        ).withOffsets(
            PrayerTimeOffsets(imsakMinutes = 30, sunriseMinutes = -30)
        )

        assertEquals(LocalTime.of(4, 10), adjusted.fajr)
        assertEquals(LocalTime.of(4, 30), adjusted.sunrise)
        assertTrue(
            listOf(adjusted.fajr, adjusted.sunrise, adjusted.dhuhr)
                .zipWithNext().all { (earlier, later) -> earlier < later }
        )
    }

    @Test
    fun acceptsInclusiveOffsetLimitsAndRejectsValuesOutsideThem() {
        val adjusted = prayerTimes().withOffsets(
            PrayerTimeOffsets(imsakMinutes = PrayerTimeOffsets.MIN_OFFSET_MINUTES)
        )

        assertEquals(LocalTime.of(3, 42), adjusted.fajr)
        assertThrows(IllegalArgumentException::class.java) {
            PrayerTimeOffsets(ishaMinutes = PrayerTimeOffsets.MAX_OFFSET_MINUTES + 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrayerTimeOffsets.ZERO.withOffset(PrayerTimeName.IMSAK, PrayerTimeOffsets.MIN_OFFSET_MINUTES - 1)
        }
    }

    private fun prayerTimes(
        fajr: LocalTime = LocalTime.of(4, 12),
        sunrise: LocalTime = LocalTime.of(5, 49),
        dhuhr: LocalTime = LocalTime.of(13, 8),
        asr: LocalTime = LocalTime.of(17, 2),
        maghrib: LocalTime = LocalTime.of(20, 21),
        isha: LocalTime = LocalTime.of(22, 1)
    ) = PrayerTimes(fajr, sunrise, dhuhr, asr, maghrib, isha)
}
