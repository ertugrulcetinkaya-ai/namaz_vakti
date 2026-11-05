package com.example.namazvakti

import com.example.namazvakti.widget.PrayerWidgetSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerWidgetSizeClassTest {
    @Test
    fun measuredS24UltraTwoByOneDimensionsUseSmallLayout() {
        assertEquals(PrayerWidgetSizeClass.SMALL, PrayerWidgetSizeClass.from(159, 81))
        assertEquals(PrayerWidgetSizeClass.SMALL, PrayerWidgetSizeClass.from(399, 278))
    }

    @Test
    fun measuredS24UltraWideOneRowDimensionsUseMediumLayout() {
        assertEquals(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.from(515, 126))
        assertEquals(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.from(406, 199))
    }

    @Test
    fun measuredS24UltraFourByTwoDimensionsUseLargeLayout() {
        assertEquals(PrayerWidgetSizeClass.LARGE, PrayerWidgetSizeClass.from(407, 278))
        assertEquals(PrayerWidgetSizeClass.LARGE, PrayerWidgetSizeClass.from(515, 278))
    }
}
