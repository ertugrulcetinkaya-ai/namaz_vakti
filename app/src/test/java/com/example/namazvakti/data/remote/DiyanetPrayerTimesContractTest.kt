package com.example.namazvakti.data.remote

import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class DiyanetPrayerTimesContractTest {
    @Test
    fun liveDistrictCalendarsAreCompleteAndConsecutive() {
        assumeTrue(
            "Run the live contract test with -PdiyanetContract=true",
            System.getProperty("diyanet.contract.enabled") == "true"
        )

        val today = PrayerTimeProvider().today()
        val api = DiyanetPrayerTimesApi()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        val districts = listOf(
            "İSTANBUL" to "9541",
            "ANKARA" to "9206",
            "İZMİR" to "9560"
        )

        districts.forEach { (city, id) ->
            val url = "https://namazvakti.mtopal.dev/vakitler/$id".toHttpUrl()
            val request = Request.Builder().url(url).get().build()
            val body = client.newCall(request).execute().use { response ->
                assertTrue("$city ($id): HTTP ${response.code}", response.isSuccessful)
                requireNotNull(response.body).string()
            }
            val responseRowCount = JsonParser.parseString(body).asJsonArray.size()
            val days = api.parseResponse(body, today).days

            assertTrue("$city ($id): response must contain today's row", days.any { it.date == today })
            assertEquals("$city ($id): calendar must start today", today, days.firstOrNull()?.date)
            assertEquals("$city ($id): parser must not skip or deduplicate rows", responseRowCount, days.size)
            days.zipWithNext().forEach { (previous, next) ->
                assertEquals(
                    "$city ($id): dates must be consecutive after ${previous.date}",
                    previous.date.plusDays(1),
                    next.date
                )
            }
        }
    }
}
