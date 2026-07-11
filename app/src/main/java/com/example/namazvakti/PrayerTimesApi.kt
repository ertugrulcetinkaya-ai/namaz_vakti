package com.example.namazvakti

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

class PrayerTimesApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    private val gson = Gson()

    fun fetchToday(city: String, country: String): PrayerTimesApiResult {
        val url = "https://api.aladhan.com/v1/timingsByCity"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("city", city)
            .addQueryParameter("country", country)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val parsed = gson.fromJson(body, PrayerTimesResponse::class.java)
            val timings = parsed.data.timings
            return PrayerTimesApiResult(
                prayerTimes = PrayerTimes(
                fajr = normalize(timings.fajr),
                sunrise = normalize(timings.sunrise),
                dhuhr = normalize(timings.dhuhr),
                asr = normalize(timings.asr),
                maghrib = normalize(timings.maghrib),
                isha = normalize(timings.isha)
                ),
                hijriText = parsed.data.date.hijri.toDisplayText()
            )
        }
    }

    private fun normalize(value: String): String = value.substringBefore(" ")
}

data class PrayerTimesApiResult(
    val prayerTimes: PrayerTimes,
    val hijriText: String?
)

private fun HijriDate.toDisplayText(): String? {
    val dayValue = day.trim()
    if (dayValue.isBlank()) return null

    val monthText = hijriMonthName(number = month.number, en = month.en, ar = month.ar)
    val yearValue = year.trim()
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
