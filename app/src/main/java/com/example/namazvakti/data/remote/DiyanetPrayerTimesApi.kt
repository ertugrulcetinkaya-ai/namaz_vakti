package com.example.namazvakti.data.remote

import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

interface DiyanetPrayerTimesRemoteDataSource {
    suspend fun fetch(districtId: String): PrayerTimesCalendarResult
}

class DiyanetPrayerTimesApi(
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val baseUrl: okhttp3.HttpUrl = "https://namazvakti.mtopal.dev/".toHttpUrl(),
    private val timeProvider: PrayerTimeProvider = PrayerTimeProvider()
) : DiyanetPrayerTimesRemoteDataSource {
    private val transport = PrayerTimesApi(client)

    override suspend fun fetch(districtId: String): PrayerTimesCalendarResult {
        val today = timeProvider.today()
        val url = baseUrl.newBuilder().addPathSegment("vakitler").addPathSegment(districtId).build()
        return parseResponse(transport.execute(Request.Builder().url(url).get().build()), today)
    }

    internal fun parseResponse(body: String, today: LocalDate): PrayerTimesCalendarResult {
        val rows = try {
            JsonParser.parseString(body).asJsonArray
        } catch (e: RuntimeException) {
            throw PrayerTimesApiException("Invalid Diyanet response", e)
        }
        val days = rows.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            try {
                parseDay(element.asJsonObject)
            } catch (_: DateTimeException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.distinctBy { it.date }.sortedBy { it.date }

        val skippedRows = rows.size() - days.size
        val hasToday = days.any { it.date == today }
        val consecutive = days.firstOrNull()?.date == today &&
            days.zipWithNext().all { (previous, next) -> next.date == previous.date.plusDays(1) }
        if (skippedRows > 0 || !consecutive) {
            logger.warning("diyanet_calendar_partial skipped_rows=$skippedRows consecutive=$consecutive")
        }
        if (!hasToday) throw PrayerTimesApiException("Diyanet response has no data for $today")
        return PrayerTimesCalendarResult(days)
    }

    private fun parseDay(row: JsonObject): PrayerTimesApiDayResult {
        fun field(name: String): String = row.get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw IllegalArgumentException("Missing or invalid $name")
        fun time(name: String): LocalTime {
            val raw = field(name)
            require(TIME_FORMAT.matches(raw))
            return LocalTime.parse(raw)
        }
        val date = LocalDate.parse(field("MiladiTarihKisa"), DATE_FORMAT)
        val hijriText = field("HicriTarihKisa")
        val hijri = hijriText.split(" ")
        require(hijri.size == 3)
        val day = hijri[0].toInt()
        val month = MONTHS.indexOf(hijri[1]) + 1
        val year = hijri[2].toInt()
        require(day in 1..30 && month in 1..12 && year in 1..9999)
        return PrayerTimesApiDayResult(date, PrayerTimesApiResult(
            PrayerTimes(time("Imsak"), time("Gunes"), time("Ogle"), time("Ikindi"), time("Aksam"), time("Yatsi")),
            hijriText, "Europe/Istanbul", day, month, year
        ))
    }

    private companion object {
        val logger: Logger = Logger.getLogger("NamazPrayer")
        val TIME_FORMAT = Regex("[0-9]{2}:[0-9]{2}")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
        val MONTHS = listOf("Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir", "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce")
    }
}
