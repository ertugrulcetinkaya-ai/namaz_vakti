package com.example.namazvakti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrayerTimesModelsTest {
    @Test
    fun parsesWidgetTextOnlyWhenAllSixValuesExist() {
        val times = parsePrayerTimesText("İmsak 04:12 • Güneş 05:49 • Öğle 13:08 • İkindi 17:02 • Akşam 20:21 • Yatsı 22:01")

        assertEquals("17:02", times?.asr)
        assertNull(parsePrayerTimesText("İmsak 04:12 • Güneş 05:49"))
    }
}
