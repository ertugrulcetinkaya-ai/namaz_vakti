package com.example.namazvakti

import com.example.namazvakti.widget.PrayerWidgetSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerWidgetSizeClassTest {
    @Test
    fun twoByOneDimensionsUseSmallLayout() {
        assertEquals(PrayerWidgetSizeClass.SMALL, PrayerWidgetSizeClass.from(110, 60))
        assertEquals(PrayerWidgetSizeClass.SMALL, PrayerWidgetSizeClass.from(179, 200))
    }

    @Test
    fun fourByOneDimensionsUseMediumLayout() {
        assertEquals(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.from(250, 80))
        assertEquals(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.from(220, 99))
        assertEquals(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.from(180, 120))
    }

    @Test
    fun fourByTwoDimensionsUseLargeLayout() {
        assertEquals(PrayerWidgetSizeClass.LARGE, PrayerWidgetSizeClass.from(220, 100))
        assertEquals(PrayerWidgetSizeClass.LARGE, PrayerWidgetSizeClass.from(250, 140))
    }
}
