package com.example.namazvakti

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
