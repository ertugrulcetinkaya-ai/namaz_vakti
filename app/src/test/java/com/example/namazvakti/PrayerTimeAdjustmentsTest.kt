package com.example.namazvakti

import com.example.namazvakti.domain.model.PrayerTimeAdjustments
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerTimeAdjustmentsTest {
    @Test
    fun adjustmentsUseSafeSideExtremaAcrossOfficialSamples() {
        val samples = listOf(
            DeltaRange(-1..0, -1..1, -1..1, -2..1, -1..2, -1..2),
            DeltaRange(-1..1, -1..1, -1..1, -1..1, -1..2, -2..2),
            DeltaRange(-1..1, -1..1, -1..1, -1..1, -1..2, -1..2),
            DeltaRange(0..1, -1..1, -1..1, -1..1, -1..2, -1..2),
            DeltaRange(-1..1, -1..1, -1..1, -1..1, -1..1, -1..2),
            DeltaRange(0..1, 0..1, -1..1, -1..1, -1..2, -1..2),
            DeltaRange(-1..1, -1..1, -1..1, -1..1, -1..2, -1..2)
        )

        assertEquals(samples.minOf { it.imsak.first }, PrayerTimeAdjustments.APP_IMSAK_FAJR_MINUTES)
        assertEquals(samples.minOf { it.gunes.first }, PrayerTimeAdjustments.SUNRISE_MINUTES)
        assertEquals(samples.maxOf { it.ogle.last }, PrayerTimeAdjustments.DHUHR_MINUTES)
        assertEquals(samples.maxOf { it.ikindi.last }, PrayerTimeAdjustments.ASR_MINUTES)
        assertEquals(samples.maxOf { it.aksam.last }, PrayerTimeAdjustments.MAGHRIB_MINUTES)
        assertEquals(samples.maxOf { it.yatsi.last }, PrayerTimeAdjustments.ISHA_MINUTES)
        assertEquals(
            "0,-1,-8,6,5,9,0,2,0",
            PrayerTimeAdjustments.ALADHAN_TUNE
        )
    }

    private data class DeltaRange(
        val imsak: IntRange,
        val gunes: IntRange,
        val ogle: IntRange,
        val ikindi: IntRange,
        val aksam: IntRange,
        val yatsi: IntRange
    )
}
