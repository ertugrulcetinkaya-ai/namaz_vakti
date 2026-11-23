package com.example.namazvakti.ui.main

import com.example.namazvakti.domain.model.PrayerDataSource
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerSourceBadgeTest {
    @Test
    fun mapsDiyanetToD() {
        assertEquals(
            PrayerSourceBadge.DIYANET,
            prayerSourceBadgeFor(PrayerDataSource.DIYANET, usesProvinceCenterFallback = false)
        )
    }

    @Test
    fun mapsAlAdhanToA() {
        assertEquals(
            PrayerSourceBadge.ALADHAN,
            prayerSourceBadgeFor(PrayerDataSource.ALADHAN, usesProvinceCenterFallback = false)
        )
    }

    @Test
    fun mapsAlAdhanProvinceCenterFallbackToAsterisk() {
        assertEquals(
            PrayerSourceBadge.ALADHAN_PROVINCE_CENTER,
            prayerSourceBadgeFor(PrayerDataSource.ALADHAN, usesProvinceCenterFallback = true)
        )
    }
}
