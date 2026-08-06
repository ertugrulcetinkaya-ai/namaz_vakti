package com.example.namazvakti

import org.junit.Assert.assertEquals
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class PrayerTimesApiTest {
    private val api = PrayerTimesApi()

    @Test
    fun parsesSuccessfulResponse() {
        val result = api.parseResponse(
            """{"code":200,"status":"OK","data":{"timings":{"Fajr":"04:12 (TRT)","Sunrise":"05:49","Dhuhr":"13:08","Asr":"17:02","Maghrib":"20:21","Isha":"22:01"},"date":{"hijri":{"day":"12","month":{"number":9,"en":"Ramadan"},"year":"1447"}}}}"""
        )

        assertEquals("04:12", result.prayerTimes.fajr)
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
    fun sendsCalculationSettingsAsQueryParameters() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))
        server.start()
        try {
            PrayerTimesApi(baseUrl = server.url("/v1/")).fetchToday("Ankara", "Turkey")
            val request = server.takeRequest()
            assertEquals("Ankara", request.requestUrl?.queryParameter("city"))
            assertEquals("Turkey", request.requestUrl?.queryParameter("country"))
            assertEquals("13", request.requestUrl?.queryParameter("method"))
            assertEquals("1", request.requestUrl?.queryParameter("school"))
        } finally {
            server.shutdown()
        }
    }

    @Test(expected = PrayerTimesApiException::class)
    fun rejectsInvalidPrayerTime() {
        api.parseResponse(successBody().replace("04:12", "99:99"))
    }

    private fun successBody(): String =
        """{"code":200,"status":"OK","data":{"timings":{"Fajr":"04:12 (TRT)","Sunrise":"05:49","Dhuhr":"13:08","Asr":"17:02","Maghrib":"20:21","Isha":"22:01"},"date":{"hijri":{"day":"12","month":{"number":9,"en":"Ramadan"},"year":"1447"}}}}"""
}
