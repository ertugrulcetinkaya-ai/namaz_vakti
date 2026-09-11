package com.example.namazvakti

import com.example.namazvakti.data.remote.PrayerTimesApi
import com.example.namazvakti.data.remote.PrayerTimesApiDayResult
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.example.namazvakti.domain.model.classifyPrayerError
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.runTest
import java.time.LocalDate

class PrayerTimesApiTest {
    private val api = PrayerTimesApi()

    @Test
    fun preservesHttpStatusForRetryClassification() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))
        server.start()
        try {
            try {
                PrayerTimesApi(baseUrl = server.url("/v1/")).fetchMonth(
                    "Ankara", "Turkey", 2026, 8, PrayerCalculationSettings()
                )
                fail("HTTP 503 should fail the API request")
            } catch (error: PrayerTimesApiException) {
                assertEquals(503, error.statusCode)
                assertEquals(PrayerError.Service(503), classifyPrayerError(error))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun parsesMonthlyCalendarAndSortsDays() {
        val result = api.parseCalendarResponse(calendarBody())

        assertEquals(
            listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)),
            result.days.map(PrayerTimesApiDayResult::date)
        )
        assertEquals("1 Ramazan 1447", result.days.first().result.hijriText)
        assertEquals("Europe/Istanbul", result.days.first().result.timezone)
    }

    @Test
    fun monthlyRequestUsesCalendarEndpointAndCalculationSettings() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(calendarBody()))
        server.start()
        try {
            PrayerTimesApi(baseUrl = server.url("/v1/")).fetchMonth(
                "Ankara", "Turkey", 2026, 8,
                PrayerCalculationSettings(method = 3, school = PrayerCalculationSettings.SCHOOL_HANAFI)
            )
            val request = server.takeRequest()
            assertEquals("/v1/calendarByCity/2026/8", request.requestUrl?.encodedPath)
            assertEquals("Ankara", request.requestUrl?.queryParameter("city"))
            assertEquals("Turkey", request.requestUrl?.queryParameter("country"))
            assertEquals("3", request.requestUrl?.queryParameter("method"))
            assertEquals("1", request.requestUrl?.queryParameter("school"))
        } finally {
            server.shutdown()
        }
    }

    @Test(expected = PrayerTimesApiException::class)
    fun monthlyCalendarRejectsDuplicateDates() {
        api.parseCalendarResponse(calendarBody().replace("02-08-2026", "01-08-2026"))
    }

    @Test(expected = PrayerTimesApiException::class)
    fun rejectsInvalidPrayerTime() {
        api.parseCalendarResponse(calendarBody().replace("04:12", "99:99"))
    }

    @Test(expected = PrayerTimesApiException::class)
    fun rejectsNonChronologicalPrayerTimes() {
        api.parseCalendarResponse(calendarBody().replace("17:02", "12:00"))
    }

    private fun calendarBody(): String =
        """{"code":200,"status":"OK","data":[{"timings":{"Fajr":"04:13","Sunrise":"05:50","Dhuhr":"13:08","Asr":"17:01","Maghrib":"20:20","Isha":"22:00"},"date":{"gregorian":{"date":"02-08-2026"},"hijri":{"day":"2","month":{"number":9,"en":"Ramadan"},"year":"1447"}},"meta":{"timezone":"Europe/Istanbul"}},{"timings":{"Fajr":"04:12","Sunrise":"05:49","Dhuhr":"13:08","Asr":"17:02","Maghrib":"20:21","Isha":"22:01"},"date":{"gregorian":{"date":"01-08-2026"},"hijri":{"day":"1","month":{"number":9,"en":"Ramadan"},"year":"1447"}},"meta":{"timezone":"Europe/Istanbul"}}]}"""
}
