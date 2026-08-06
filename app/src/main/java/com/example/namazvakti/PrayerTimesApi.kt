package com.example.namazvakti

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

class PrayerTimesApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    private val gson = Gson()

    fun fetchToday(city: String, country: String): PrayerTimesApiResult {
        val url = "https://api.aladhan.com/v1/timingsByCity"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("city", city)
            .addQueryParameter("country", country)
            .addQueryParameter("method", PrayerCalculationSettings.DEFAULT_METHOD.toString())
            .addQueryParameter("school", PrayerCalculationSettings.DEFAULT_SCHOOL.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PrayerTimesApiException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseResponse(body)
        }
    }

    internal fun parseResponse(body: String): PrayerTimesApiResult {
        val parsed = runCatching { gson.fromJson(body, PrayerTimesResponse::class.java) }
            .getOrElse { throw PrayerTimesApiException("Invalid API response", it) }
        if (parsed.code != null && parsed.code !in 200..299) {
            throw PrayerTimesApiException(parsed.message ?: parsed.status ?: "API request failed")
        }
        val data = parsed.data ?: throw PrayerTimesApiException("API response has no data")
        val timings = data.timings ?: throw PrayerTimesApiException("API response has no timings")
        val values = listOf(timings.fajr, timings.sunrise, timings.dhuhr, timings.asr, timings.maghrib, timings.isha)
        if (values.any { it.isNullOrBlank() }) {
            throw PrayerTimesApiException("API response has incomplete prayer timings")
        }
        return PrayerTimesApiResult(
            prayerTimes = PrayerTimes(
                fajr = normalize(timings.fajr),
                sunrise = normalize(timings.sunrise),
                dhuhr = normalize(timings.dhuhr),
                asr = normalize(timings.asr),
                maghrib = normalize(timings.maghrib),
                isha = normalize(timings.isha)
            ),
            hijriText = data.date?.hijri?.toDisplayText()
        )
    }

    private fun normalize(value: String?): String = value.orEmpty().substringBefore(" ")
}

class PrayerTimesApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class PrayerTimesApiResult(
    val prayerTimes: PrayerTimes,
    val hijriText: String?
)

private fun HijriDate.toDisplayText(): String? {
    val dayValue = day?.trim().orEmpty()
    if (dayValue.isBlank()) return null

    val monthText = hijriMonthName(number = month?.number, en = month?.en, ar = month?.ar)
    val yearValue = year?.trim().orEmpty()
    if (monthText.isBlank() || yearValue.isBlank()) return null

    return "$dayValue $monthText $yearValue"
}

private fun hijriMonthName(number: Int?, en: String?, ar: String?): String {
    val byNumber = number?.let {
        when (it) {
            1 -> "Muharrem"
            2 -> "Safer"
            3 -> "Rebiülevvel"
            4 -> "Rebiülahir"
            5 -> "Cemaziyelevvel"
            6 -> "Cemaziyelahir"
            7 -> "Recep"
            8 -> "Şaban"
            9 -> "Ramazan"
            10 -> "Şevval"
            11 -> "Zilkade"
            12 -> "Zilhicce"
            else -> null
        }
    }
    if (byNumber != null) return byNumber

    val raw = (en ?: ar.orEmpty()).lowercase()
    return when {
        "muharram" in raw -> "Muharrem"
        "safar" in raw -> "Safer"
        "rabi" in raw && "awwal" in raw -> "Rebiülevvel"
        "rabi" in raw && ("thani" in raw || "akhir" in raw) -> "Rebiülahir"
        "jumada" in raw && "awwal" in raw -> "Cemaziyelevvel"
        "jumada" in raw && ("akhir" in raw || "thani" in raw) -> "Cemaziyelahir"
        "rajab" in raw -> "Recep"
        "sha" in raw && "ban" in raw -> "Şaban"
        "ramadan" in raw -> "Ramazan"
        "shawwal" in raw -> "Şevval"
        "dhu" in raw && "qadah" in raw -> "Zilkade"
        "dhu" in raw && "hijjah" in raw -> "Zilhicce"
        else -> ""
    }
}
