package com.example.namazvakti

import com.example.namazvakti.widget.PrayerWidgetSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerWidgetSizeClassTest {
    @Test
    fun twoCellAndSubThresholdWidthsUseSmallLayout() {
        assertEquals(PrayerWidgetSizeClass.SMALL, PrayerWidgetSizeClass.from(159, 81))
        assertEquals(PrayerWidgetSizeClass.SMALL, PrayerWidgetSizeClass.from(279, 278))
    }

    @Test
    fun threeCellWidthsUseMediumLayoutAtOneRowHeight() {
        assertEquals(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.from(280, 126))
        assertEquals(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.from(380, 126))
        assertEquals(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.from(515, 126))
        assertEquals(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.from(380, 199))
    }

    @Test
    fun twoRowHeightUsesLargeLayoutForThreeCellAndWiderWidgets() {
        assertEquals(PrayerWidgetSizeClass.LARGE, PrayerWidgetSizeClass.from(280, 278))
        assertEquals(PrayerWidgetSizeClass.LARGE, PrayerWidgetSizeClass.from(380, 278))
        assertEquals(PrayerWidgetSizeClass.LARGE, PrayerWidgetSizeClass.from(515, 278))
    }
}
