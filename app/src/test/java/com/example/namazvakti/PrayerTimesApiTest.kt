package com.example.namazvakti

import org.junit.Assert.assertEquals
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.runTest
import java.time.LocalDate

class PrayerTimesApiTest {
    private val api = PrayerTimesApi()

    @Test
    fun parsesSuccessfulResponse() {
        val result = api.parseResponse(
            """{"code":200,"status":"OK","data":{"timings":{"Fajr":"04:12 (TRT)","Sunrise":"05:49","Dhuhr":"13:08","Asr":"17:02","Maghrib":"20:21","Isha":"22:01"},"date":{"hijri":{"day":"12","month":{"number":9,"en":"Ramadan"},"year":"1447"}}}}"""
        )

        assertEquals("04:12", result.prayerTimes.fajr.toString())
        assertEquals("12 Ramazan 1447", result.hijriText)
    }

    @Test(expected = PrayerTimesApiException::class)
    fun rejectsMissingTimings() {
        api.parseResponse("""{"code":200,"status":"OK","data":{"date":{}}}""")
    }

    @Test(expected = PrayerTimesApiException::class)
    fun rejectsApiErrorBody() {
        api.parseResponse("""{"code":400,"status":"BAD_REQUEST","message":"Invalid city"}""")
    }

    @Test
    fun sendsCalculationSettingsAsQueryParameters() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))
        server.start()
        try {
            PrayerTimesApi(baseUrl = server.url("/v1/")).fetchToday(
                "Ankara", "Turkey", PrayerCalculationSettings()
            )
            val request = server.takeRequest()
            assertEquals("Ankara", request.requestUrl?.queryParameter("city"))
            assertEquals("Turkey", request.requestUrl?.queryParameter("country"))
            assertEquals("13", request.requestUrl?.queryParameter("method"))
            assertEquals("0", request.requestUrl?.queryParameter("school"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun sendsProvidedCalculationSettings() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))
        server.start()
        try {
            PrayerTimesApi(baseUrl = server.url("/v1/")).fetchToday(
                "Ankara", "Turkey", PrayerCalculationSettings(
                    method = 3,
                    school = PrayerCalculationSettings.SCHOOL_HANAFI
                )
            )
            val request = server.takeRequest()
            assertEquals("3", request.requestUrl?.queryParameter("method"))
            assertEquals("1", request.requestUrl?.queryParameter("school"))
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
        api.parseResponse(successBody().replace("04:12", "99:99"))
    }

    private fun successBody(): String =
        """{"code":200,"status":"OK","data":{"timings":{"Fajr":"04:12 (TRT)","Sunrise":"05:49","Dhuhr":"13:08","Asr":"17:02","Maghrib":"20:21","Isha":"22:01"},"date":{"hijri":{"day":"12","month":{"number":9,"en":"Ramadan"},"year":"1447"}}}}"""

    private fun calendarBody(): String =
        """{"code":200,"status":"OK","data":[{"timings":{"Fajr":"04:13","Sunrise":"05:50","Dhuhr":"13:08","Asr":"17:01","Maghrib":"20:20","Isha":"22:00"},"date":{"gregorian":{"date":"02-08-2026"},"hijri":{"day":"2","month":{"number":9,"en":"Ramadan"},"year":"1447"}},"meta":{"timezone":"Europe/Istanbul"}},{"timings":{"Fajr":"04:12","Sunrise":"05:49","Dhuhr":"13:08","Asr":"17:02","Maghrib":"20:21","Isha":"22:01"},"date":{"gregorian":{"date":"01-08-2026"},"hijri":{"day":"1","month":{"number":9,"en":"Ramadan"},"year":"1447"}},"meta":{"timezone":"Europe/Istanbul"}}]}"""
}
