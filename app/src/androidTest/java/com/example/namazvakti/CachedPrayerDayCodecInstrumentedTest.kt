package com.example.namazvakti

import com.example.namazvakti.data.local.CachedPrayerDayCodec
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CachedPrayerDayCodecInstrumentedTest {
    @Test
    fun legacyCacheFieldNamesDecodeOnTheRuntimeClasspath() {
        val decoded = CachedPrayerDayCodec().decode(
            """
                {
                  "schemaVersion": 1,
                  "date": "2026-08-06",
                  "city": "Ankara",
                  "country": "Turkey",
                  "displayCity": "ANKARA",
                  "locationTimezone": "Europe/Istanbul",
                  "timezone": "Europe/Istanbul",
                  "method": 13,
                  "school": 0,
                  "fajr": "04:12",
                  "sunrise": "05:49",
                  "dhuhr": "13:08",
                  "asr": "17:02",
                  "maghrib": "20:21",
                  "isha": "22:01",
                  "hijriText": null,
                  "fetchedAtEpochMillis": 1785974400000
                }
            """.trimIndent()
        )

        assertNotNull(decoded)
        assertEquals(LocalDate.of(2026, 8, 6), decoded?.date)
        assertEquals("Ankara", decoded?.location?.city)
        assertEquals("04:12", decoded?.prayerTimes?.fajr.toString())
    }
}
