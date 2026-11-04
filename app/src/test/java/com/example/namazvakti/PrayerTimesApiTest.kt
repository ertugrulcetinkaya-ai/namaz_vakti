package com.example.namazvakti

import com.example.namazvakti.data.remote.PrayerTimesApi
import com.example.namazvakti.data.remote.PrayerTimesApiDayResult
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.PrayerTimeAdjustments
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
        assertEquals(1, result.days.first().result.hijriDay)
        assertEquals(9, result.days.first().result.hijriMonth)
        assertEquals(1447, result.days.first().result.hijriYear)
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
                PrayerCalculationSettings(method = 3, school = PrayerCalculationSettings.SCHOOL_HANAFI),
                latitude = 39.9199,
                longitude = 32.8543,
                timezone = "Europe/Istanbul"
            )
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/calendar/2026/8", request.requestUrl?.encodedPath)
            assertEquals("39.9199", request.requestUrl?.queryParameter("latitude"))
            assertEquals("32.8543", request.requestUrl?.queryParameter("longitude"))
            assertEquals("3", request.requestUrl?.queryParameter("method"))
            assertEquals("1", request.requestUrl?.queryParameter("school"))
            assertEquals("HJCoSA", request.requestUrl?.queryParameter("calendarMethod"))
            assertEquals("Europe/Istanbul", request.requestUrl?.queryParameter("timezonestring"))
            assertEquals(PrayerTimeAdjustments.ALADHAN_TUNE, request.requestUrl?.queryParameter("tune"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun monthlyRequestFallsBackToCityWhenCoordinatesAreUnavailable() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(calendarBody()))
        server.start()
        try {
            PrayerTimesApi(baseUrl = server.url("/v1/")).fetchMonth(
                "Baku", "Azerbaijan", 2026, 8, PrayerCalculationSettings()
            )
            val request = server.takeRequest()
            assertEquals("/v1/calendarByCity/2026/8", request.requestUrl?.encodedPath)
            assertEquals("Baku", request.requestUrl?.queryParameter("city"))
            assertEquals("Azerbaijan", request.requestUrl?.queryParameter("country"))
            assertEquals("HJCoSA", request.requestUrl?.queryParameter("calendarMethod"))
            assertEquals(PrayerTimeAdjustments.ALADHAN_TUNE, request.requestUrl?.queryParameter("tune"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun hijriMonthRequestUsesHjCoSaCalendarMethodAndParsesDates() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"code":200,"status":"OK","data":[{"gregorian":{"date":"09-02-2027"}},{"gregorian":{"date":"08-02-2027"}}]}"""
            )
        )
        server.start()
        try {
            val dates = PrayerTimesApi(baseUrl = server.url("/v1/")).fetchHijriMonth(9, 1448)
            val request = server.takeRequest()
            assertEquals(listOf(LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 9)), dates)
            assertEquals("/v1/hToGCalendar/9/1448", request.requestUrl?.encodedPath)
            assertEquals("HJCoSA", request.requestUrl?.queryParameter("calendarMethod"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun invalidHijriNumbersAreDiscarded() {
        val result = api.parseCalendarResponse(
            calendarBody().replace("\"day\":\"1\"", "\"day\":\"31\"")
        )

        assertEquals(null, result.days.first().result.hijriText)
        assertEquals(null, result.days.first().result.hijriDay)
        assertEquals(null, result.days.first().result.hijriMonth)
        assertEquals(null, result.days.first().result.hijriYear)
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
