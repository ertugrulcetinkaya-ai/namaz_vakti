package com.example.namazvakti

import com.example.namazvakti.data.remote.DiyanetPrayerTimesApi
import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.DiyanetHijriCalendar
import com.example.namazvakti.domain.model.PrayerLocationConfig
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class DiyanetPrayerTimesApiTest {
    private val api = DiyanetPrayerTimesApi()
    private val today = LocalDate.of(2026, 11, 16)

    private fun fixture(id: String) = javaClass.getResource("/diyanet/$id.json")!!.readText()

    @Test fun snapshotDatesAndHijriCalendarAgree() {
        for (id in listOf("9206", "9541", "9560")) {
            val days = api.parseResponse(fixture(id), today).days
            assertEquals(46, days.size)
            assertEquals(today, days.first().date)
            assertEquals(LocalDate.parse("2026-12-31"), days.last().date)
            days.forEach { day ->
                val hijri = DiyanetHijriCalendar.hijriDateOf(day.date)!!
                assertEquals(hijri.day, day.result.hijriDay)
                assertEquals(hijri.month, day.result.hijriMonth)
                assertEquals(hijri.year, day.result.hijriYear)
            }
        }
    }

    @Test fun malformedOrEmptyResponsesAreRejected() {
        assertThrows(PrayerTimesApiException::class.java) { api.parseResponse("bad", today) }
        assertThrows(PrayerTimesApiException::class.java) { api.parseResponse("[]", today) }
    }

    @Test fun skipsInvalidTimeAndDateRowsAndKeepsValidDays() {
        val rows = fixtureRows()
        rows[1].asJsonObject.addProperty("Imsak", "99:99")
        rows[2].asJsonObject.addProperty("MiladiTarihKisa", "31.11.2026")

        val days = api.parseResponse(rows.toString(), today).days

        assertEquals(44, days.size)
        assertEquals(today, days[0].date)
        assertEquals(today.plusDays(3), days[1].date)
    }

    @Test fun rejectsResponseWhenTodaysRowIsInvalid() {
        val rows = fixtureRows()
        rows[0].asJsonObject.addProperty("Imsak", "99:99")

        assertThrows(PrayerTimesApiException::class.java) { api.parseResponse(rows.toString(), today) }
    }

    @Test fun keepsTheFirstValidRowForADuplicateDate() {
        val rows = fixtureRows()
        val duplicate = rows[0].asJsonObject.deepCopy()
        duplicate.addProperty("Imsak", "00:00")
        rows.add(duplicate)

        val days = api.parseResponse(rows.toString(), today).days

        assertEquals(api.parseResponse(fixture("9206"), today).days, days)
    }

    private fun fixtureRows(): JsonArray = JsonParser.parseString(fixture("9206")).asJsonArray

    @Test fun allCitiesResolveToCentralDistricts() {
        assertEquals(869, DiyanetDistricts.districts.size)
        PrayerLocationConfig.cityOptions.forEach { assertNotNull(DiyanetDistricts.forCity(it.city, it.country)) }
        mapOf("Istanbul" to "9541", "Ankara" to "9206", "Izmir" to "9560", "Usak" to "9919", "Kahramanmaras" to "9577").forEach { (city, id) ->
            assertEquals(id, DiyanetDistricts.forCity(city, "Turkey")?.centralDistrictId)
        }
    }
}
