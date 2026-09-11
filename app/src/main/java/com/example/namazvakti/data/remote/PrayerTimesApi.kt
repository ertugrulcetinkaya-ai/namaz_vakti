package com.example.namazvakti.data.remote

import com.google.gson.Gson
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.example.namazvakti.domain.model.PrayerTimes
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import com.google.gson.annotations.SerializedName
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine

data class PrayerTimesCalendarResponse(
    @field:SerializedName("code") val code: Int? = null,
    @field:SerializedName("status") val status: String? = null,
    @field:SerializedName("data") val data: List<PrayerTimesData>? = null,
    @field:SerializedName("message") val message: String? = null
)

data class PrayerTimesData(
    @field:SerializedName("timings") val timings: PrayerTimings? = null,
    @field:SerializedName("date") val date: PrayerDate? = null,
    @field:SerializedName("meta") val meta: PrayerMeta? = null
)

data class PrayerMeta(
    @field:SerializedName("timezone") val timezone: String? = null
)

data class PrayerDate(
    @field:SerializedName("hijri") val hijri: HijriDate? = null,
    @field:SerializedName("gregorian") val gregorian: GregorianDate? = null
)

data class GregorianDate(
    @field:SerializedName("date") val date: String? = null
)

data class HijriDate(
    @field:SerializedName("day") val day: String? = null,
    @field:SerializedName("month") val month: HijriMonth? = null,
    @field:SerializedName("year") val year: String? = null
)

data class HijriMonth(
    @field:SerializedName("number") val number: Int? = null,
    @field:SerializedName("en") val en: String? = null,
    @field:SerializedName("ar") val ar: String? = null
)

data class PrayerTimings(
    @field:SerializedName("Fajr") val fajr: String? = null,
    @field:SerializedName("Sunrise") val sunrise: String? = null,
    @field:SerializedName("Dhuhr") val dhuhr: String? = null,
    @field:SerializedName("Asr") val asr: String? = null,
    @field:SerializedName("Maghrib") val maghrib: String? = null,
    @field:SerializedName("Isha") val isha: String? = null
)

interface PrayerTimesRemoteDataSource {
    suspend fun fetchMonth(
        city: String,
        country: String,
        year: Int,
        month: Int,
        settings: PrayerCalculationSettings
    ): PrayerTimesCalendarResult
}

class PrayerTimesApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val baseUrl: okhttp3.HttpUrl = "https://api.aladhan.com/v1/".toHttpUrl()
) : PrayerTimesRemoteDataSource {
    private val gson = Gson()

    override suspend fun fetchMonth(
        city: String,
        country: String,
        year: Int,
        month: Int,
        settings: PrayerCalculationSettings
    ): PrayerTimesCalendarResult {
        require(month in 1..12) { "Month must be between 1 and 12" }
        val url = baseUrl
            .newBuilder()
            .addPathSegment("calendarByCity")
            .addPathSegment(year.toString())
            .addPathSegment(month.toString())
            .addQueryParameter("city", city)
            .addQueryParameter("country", country)
            .addQueryParameter("method", settings.method.toString())
            .addQueryParameter("school", settings.school.toString())
            .build()
        val request = Request.Builder().url(url).get().build()
        return parseCalendarResponse(execute(request))
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) {
                            throw PrayerTimesApiException("HTTP ${it.code}", statusCode = it.code)
                        }
                        val body = it.body.string()
                        if (body.isBlank()) {
                            throw PrayerTimesApiException("API response has no body")
                        }
                        body
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }

    internal fun parseCalendarResponse(body: String): PrayerTimesCalendarResult {
        val parsed = runCatching { gson.fromJson(body, PrayerTimesCalendarResponse::class.java) }
            .getOrElse { throw PrayerTimesApiException("Invalid API response", it) }
        if (parsed.code !in 200..299 || parsed.status != "OK") {
            throw PrayerTimesApiException(
                parsed.message ?: parsed.status ?: "API request failed",
                statusCode = parsed.code
            )
        }
        val days = parsed.data?.map { data ->
            val rawDate = data.date?.gregorian?.date?.trim().orEmpty()
            val date = runCatching { LocalDate.parse(rawDate, CALENDAR_DATE_FORMATTER) }
                .getOrElse { throw PrayerTimesApiException("Invalid calendar date: $rawDate", it) }
            PrayerTimesApiDayResult(date, data.toApiResult())
        }.orEmpty()
        if (days.isEmpty()) throw PrayerTimesApiException("API response has no calendar days")
        if (days.map { it.date }.distinct().size != days.size) {
            throw PrayerTimesApiException("API response has duplicate calendar dates")
        }
        return PrayerTimesCalendarResult(days.sortedBy(PrayerTimesApiDayResult::date))
    }

    private fun PrayerTimesData.toApiResult(): PrayerTimesApiResult {
        val timings = timings ?: throw PrayerTimesApiException("API response has no timings")
        val values = listOf(
            "Fajr" to timings.fajr,
            "Sunrise" to timings.sunrise,
            "Dhuhr" to timings.dhuhr,
            "Asr" to timings.asr,
            "Maghrib" to timings.maghrib,
            "Isha" to timings.isha
        ).associate { (name, value) -> name to parseTime(value) }
        val prayerTimes = try {
            PrayerTimes(
                fajr = values.getValue("Fajr"),
                sunrise = values.getValue("Sunrise"),
                dhuhr = values.getValue("Dhuhr"),
                asr = values.getValue("Asr"),
                maghrib = values.getValue("Maghrib"),
                isha = values.getValue("Isha")
            )
        } catch (error: IllegalArgumentException) {
            throw PrayerTimesApiException("Invalid prayer time ordering", error)
        }
        return PrayerTimesApiResult(
            prayerTimes = prayerTimes,
            hijriText = date?.hijri?.toDisplayText(),
            timezone = meta?.timezone
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

    private companion object {
        val CALENDAR_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd-MM-uuuu")
            .withResolverStyle(ResolverStyle.STRICT)
    }
}

data class PrayerTimesApiResult(
    val prayerTimes: PrayerTimes,
    val hijriText: String?,
    val timezone: String?
)

data class PrayerTimesApiDayResult(
    val date: LocalDate,
    val result: PrayerTimesApiResult
)

data class PrayerTimesCalendarResult(val days: List<PrayerTimesApiDayResult>)

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
