package com.example.namazvakti.data.remote

import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.concurrent.TimeUnit

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
    private val baseUrl: okhttp3.HttpUrl = "https://namazvakti.mtopal.dev/".toHttpUrl()
) : DiyanetPrayerTimesRemoteDataSource {
    private val transport = PrayerTimesApi(client)

    override suspend fun fetch(districtId: String): PrayerTimesCalendarResult {
        val url = baseUrl.newBuilder().addPathSegment("vakitler").addPathSegment(districtId).build()
        return parseResponse(transport.execute(Request.Builder().url(url).get().build()))
    }

    internal fun parseResponse(body: String): PrayerTimesCalendarResult = try {
        val days = JsonParser.parseString(body).asJsonArray.map { element ->
            val row = element.asJsonObject
            fun field(name: String): String = row.get(name)?.asString
                ?: throw IllegalArgumentException("Missing $name")
            fun time(name: String): LocalTime {
                val raw = field(name)
                require(raw.matches(Regex("\\d{2}:\\d{2}")))
                return LocalTime.parse(raw)
            }
            val date = LocalDate.parse(field("MiladiTarihKisa"), DATE_FORMAT)
            val hijri = field("HicriTarihKisa").split(" ")
            require(hijri.size == 3)
            val day = hijri[0].toInt()
            val month = MONTHS.indexOf(hijri[1]) + 1
            val year = hijri[2].toInt()
            require(day in 1..30 && month in 1..12 && year in 1..9999)
            PrayerTimesApiDayResult(date, PrayerTimesApiResult(
                PrayerTimes(time("Imsak"), time("Gunes"), time("Ogle"), time("Ikindi"), time("Aksam"), time("Yatsi")),
                field("HicriTarihKisa"), "Europe/Istanbul", day, month, year
            ))
        }
        require(days.isNotEmpty() && days.map { it.date }.distinct().size == days.size)
        PrayerTimesCalendarResult(days.sortedBy { it.date })
    } catch (e: Exception) {
        throw PrayerTimesApiException("Invalid Diyanet response", e)
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
        val MONTHS = listOf("Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir", "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce")
    }
}
