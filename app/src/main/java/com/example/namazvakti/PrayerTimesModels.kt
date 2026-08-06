package com.example.namazvakti

import com.google.gson.annotations.SerializedName
import android.util.Log
import java.time.LocalTime
import java.time.ZoneId

data class PrayerTimesResponse(
    val code: Int? = null,
    val status: String? = null,
    val data: PrayerTimesData? = null,
    val message: String? = null
)

data class PrayerTimesData(
    val timings: PrayerTimings? = null,
    val date: PrayerDate? = null
)

data class PrayerDate(
    val hijri: HijriDate? = null
)

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

data class PrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
) {
    fun nextPrayerBoundary(now: LocalTime = LocalTime.now(ZoneId.of("Europe/Istanbul"))): PrayerBoundarySchedule? {
        val times = prayerBoundaries()
        if (times.size != 6) return null

        val sunrise = times[1]
        val dhuhr = times[2]
        val asr = times[3]
        val maghrib = times[4]
        val isha = times[5]
        return when {
            now < sunrise -> PrayerBoundarySchedule("Güneş", sunrise)
            now < dhuhr -> PrayerBoundarySchedule("Öğle", dhuhr)
            now < asr -> PrayerBoundarySchedule("İkindi", asr)
            now < maghrib -> PrayerBoundarySchedule("Akşam", maghrib)
            now < isha -> PrayerBoundarySchedule("Yatsı", isha)
            else -> null
        }
    }

    fun toWidgetText(): String =
        "İmsak $fajr • Güneş $sunrise • Öğle $dhuhr • İkindi $asr • Akşam $maghrib • Yatsı $isha"

    fun toHighlightedDisplayItems(): List<PrayerDisplayItem> {
        val items = prayerDisplayItems()
        val activeIndex = currentPrayerIndex()
        return items.mapIndexed { index, item ->
            if (index == activeIndex) {
                item.copy(value = "▶ ${item.value}", highlighted = true)
            } else {
                item
            }
        }
    }

    fun prayerDisplayItems(): List<PrayerDisplayItem> = listOf(
        PrayerDisplayItem(R.id.prayer_widget_item_1, "İmsak $fajr"),
        PrayerDisplayItem(R.id.prayer_widget_item_2, "Güneş $sunrise"),
        PrayerDisplayItem(R.id.prayer_widget_item_3, "Öğle $dhuhr"),
        PrayerDisplayItem(R.id.prayer_widget_item_4, "İkindi $asr"),
        PrayerDisplayItem(R.id.prayer_widget_item_5, "Akşam $maghrib"),
        PrayerDisplayItem(R.id.prayer_widget_item_6, "Yatsı $isha")
    )

    private fun currentPrayerIndex(): Int {
        val times = prayerBoundaries()
        if (times.size != 6) return -1

        val now = LocalTime.now(ZoneId.of("Europe/Istanbul"))
        Log.d(
            "NamazWidget",
            "now=$now imsak=${times[0]} gunes=${times[1]} ogle=${times[2]} ikindi=${times[3]} aksam=${times[4]} yatsi=${times[5]}"
        )
        return when {
            now >= times[0] && now < times[1] -> 0
            now >= times[1] && now < times[2] -> 1
            now >= times[2] && now < times[3] -> 2
            now >= times[3] && now < times[4] -> 3
            now >= times[4] && now < times[5] -> 4
            now >= times[5] || now < times[0] -> 5
            else -> -1
        }
    }

    private fun prayerBoundaries(): List<LocalTime> = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)
        .mapNotNull { it.toLocalTimeOrNull() }
}

data class PrayerCalculationSettings(
    val method: Int = DEFAULT_METHOD,
    val school: Int = DEFAULT_SCHOOL
) {
    companion object {
        // AlAdhan method 13 is the Turkey calculation method; school 1 is Hanafi.
        const val DEFAULT_METHOD = 13
        const val DEFAULT_SCHOOL = 1
    }
}

data class PrayerDisplayItem(
    val viewId: Int,
    val value: CharSequence,
    val highlighted: Boolean = false
)

data class PrayerWidgetCache(
    val date: String,
    val widgetText: String,
    val hijriText: String? = null
)

data class PrayerBoundarySchedule(
    val name: String,
    val time: LocalTime
)

private fun String.toLocalTimeOrNull(): LocalTime? = runCatching {
    val normalized = trim().substringBefore(" ")
    LocalTime.parse(normalized)
}.getOrNull()

fun parsePrayerTimesText(text: String): PrayerTimes? {
    val parts = text
        .trim()
        .split(" • ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (parts.size != 6) {
        return null
    }

    return PrayerTimes(
        fajr = parts[0].substringAfter(' ', parts[0]),
        sunrise = parts[1].substringAfter(' ', parts[1]),
        dhuhr = parts[2].substringAfter(' ', parts[2]),
        asr = parts[3].substringAfter(' ', parts[3]),
        maghrib = parts[4].substringAfter(' ', parts[4]),
        isha = parts[5].substringAfter(' ', parts[5])
    )
}
