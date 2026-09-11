package com.example.namazvakti

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.namazvakti.data.remote.PrayerTimesApi
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerTimesApiInstrumentedTest {
    @Test
    fun representativeAlAdhanJsonParsesOnMinifiedRuntime() {
        val result = PrayerTimesApi().parseCalendarResponse(
            """
                {
                  "code": 200,
                  "status": "OK",
                  "data": [
                    {
                      "timings": {
                        "Fajr": "04:12",
                        "Sunrise": "05:49",
                        "Dhuhr": "13:08",
                        "Asr": "17:02",
                        "Maghrib": "20:21",
                        "Isha": "22:01"
                      },
                      "date": {
                        "gregorian": { "date": "01-08-2026" },
                        "hijri": {
                          "day": "1",
                          "month": { "number": 9, "en": "Ramadan" },
                          "year": "1447"
                        }
                      },
                      "meta": { "timezone": "Europe/Istanbul" }
                    }
                  ]
                }
            """.trimIndent()
        )

        val day = result.days.single()
        assertEquals(LocalDate.of(2026, 8, 1), day.date)
        assertEquals(LocalTime.of(4, 12), day.result.prayerTimes.fajr)
        assertEquals("1 Ramazan 1447", day.result.hijriText)
        assertEquals("Europe/Istanbul", day.result.timezone)
    }
}
