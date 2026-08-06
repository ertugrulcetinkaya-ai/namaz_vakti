package com.example.namazvakti

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import java.time.LocalTime
import com.google.gson.annotations.SerializedName

data class PrayerTimesResponse(
    val code: Int? = null,
    val status: String? = null,
    val data: PrayerTimesData? = null,
    val message: String? = null
)

data class PrayerTimesData(
    val timings: PrayerTimings? = null,
    val date: PrayerDate? = null,
    val meta: PrayerMeta? = null
)

data class PrayerMeta(val timezone: String? = null)

data class PrayerDate(val hijri: HijriDate? = null)

data class HijriDate(
    val day: String? = null,
    val month: HijriMonth? = null,
    val year: String? = null
)

data class HijriMonth(
    val number: Int? = null,
    val en: String? = null,
    val ar: String? = null
)

data class PrayerTimings(
    @SerializedName("Fajr") val fajr: String? = null,
    @SerializedName("Sunrise") val sunrise: String? = null,
    @SerializedName("Dhuhr") val dhuhr: String? = null,
    @SerializedName("Asr") val asr: String? = null,
    @SerializedName("Maghrib") val maghrib: String? = null,
    @SerializedName("Isha") val isha: String? = null
)

class PrayerTimesApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val baseUrl: okhttp3.HttpUrl = "https://api.aladhan.com/v1/".toHttpUrl()
) {
    private val gson = Gson()

    fun fetchToday(
        city: String,
        country: String,
        settings: PrayerCalculationSettings
    ): PrayerTimesApiResult {
        val url = baseUrl
            .newBuilder()
            .addPathSegment("timingsByCity")
            .addQueryParameter("city", city)
            .addQueryParameter("country", country)
            .addQueryParameter("method", settings.method.toString())
            .addQueryParameter("school", settings.school.toString())
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
        if (parsed.code !in 200..299 || parsed.status != "OK") {
            throw PrayerTimesApiException(parsed.message ?: parsed.status ?: "API request failed")
        }
        val data = parsed.data ?: throw PrayerTimesApiException("API response has no data")
        val timings = data.timings ?: throw PrayerTimesApiException("API response has no timings")
        val values = listOf(
            "Fajr" to timings.fajr,
            "Sunrise" to timings.sunrise,
            "Dhuhr" to timings.dhuhr,
            "Asr" to timings.asr,
            "Maghrib" to timings.maghrib,
            "Isha" to timings.isha
        ).associate { (name, value) -> name to parseTime(value) }
        return PrayerTimesApiResult(
            prayerTimes = PrayerTimes(
                fajr = values.getValue("Fajr"),
                sunrise = values.getValue("Sunrise"),
                dhuhr = values.getValue("Dhuhr"),
                asr = values.getValue("Asr"),
                maghrib = values.getValue("Maghrib"),
                isha = values.getValue("Isha")
            ),
            hijriText = data.date?.hijri?.toDisplayText(),
            timezone = data.meta?.timezone
        )
    }

    private fun parseTime(value: String?): LocalTime {
        val normalized = value?.substringBefore(" ")?.trim().orEmpty()
        if (normalized.isBlank()) {
            throw PrayerTimesApiException("API response has incomplete prayer timings")
        }
        return runCatching { LocalTime.parse(normalized) }
            .getOrElse { throw PrayerTimesApiException("Invalid prayer time: $normalized", it) }
    }
}

class PrayerTimesApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class PrayerTimesApiResult(
    val prayerTimes: PrayerTimes,
    val hijriText: String?,
    val timezone: String?
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
