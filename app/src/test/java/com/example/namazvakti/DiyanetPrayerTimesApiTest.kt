package com.example.namazvakti

import com.example.namazvakti.data.remote.DiyanetPrayerTimesApi
import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.DiyanetHijriCalendar
import com.example.namazvakti.domain.model.PrayerLocationConfig
import com.example.namazvakti.domain.model.PrayerTimesApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class DiyanetPrayerTimesApiTest {
    private fun fixture(id: String) = javaClass.getResource("/diyanet/$id.json")!!.readText()

    @Test fun snapshotDatesAndHijriCalendarAgree() {
        for (id in listOf("9206", "9541", "9560")) {
            val days = DiyanetPrayerTimesApi().parseResponse(fixture(id)).days
            assertEquals(46, days.size)
            assertEquals(LocalDate.parse("2026-11-16"), days.first().date)
            assertEquals(LocalDate.parse("2026-12-31"), days.last().date)
            days.forEach { day ->
                val hijri = DiyanetHijriCalendar.hijriDateOf(day.date)!!
                assertEquals(hijri.day, day.result.hijriDay)
                assertEquals(hijri.month, day.result.hijriMonth)
                assertEquals(hijri.year, day.result.hijriYear)
            }
        }
    }

    @Test fun malformedAndDuplicateRowsAreRejected() {
        val body = fixture("9206")
        assertThrows(PrayerTimesApiException::class.java) { DiyanetPrayerTimesApi().parseResponse("bad") }
        val row = body.substring(1, body.indexOf("},{") + 1)
        assertThrows(PrayerTimesApiException::class.java) { DiyanetPrayerTimesApi().parseResponse("[$row,$row]") }
        assertThrows(PrayerTimesApiException::class.java) { DiyanetPrayerTimesApi().parseResponse(body.replace("06:01", "99:99")) }
    }

    @Test fun allCitiesResolveToCentralDistricts() {
        assertEquals(869, DiyanetDistricts.districts.size)
        PrayerLocationConfig.cityOptions.forEach { assertNotNull(DiyanetDistricts.forCity(it.city, it.country)) }
        mapOf("Istanbul" to "9541", "Ankara" to "9206", "Izmir" to "9560", "Usak" to "9919", "Kahramanmaras" to "9577").forEach { (city, id) ->
            assertEquals(id, DiyanetDistricts.forCity(city, "Turkey")?.centralDistrictId)
        }
    }
}
