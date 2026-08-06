package com.example.namazvakti

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PrayerLocation(
    val city: String,
    val country: String,
    val displayCity: String = city.uppercase(),
    val timezone: ZoneId = PrayerTimeProvider.DEFAULT_ZONE
)

data class PrayerCalculationSettings(
    val method: Int = DEFAULT_METHOD,
    val school: Int = DEFAULT_SCHOOL
) {
    companion object {
        const val DEFAULT_METHOD = 13
        const val DEFAULT_SCHOOL = 1
    }
}

data class PrayerTimes(
    val fajr: LocalTime,
    val sunrise: LocalTime,
    val dhuhr: LocalTime,
    val asr: LocalTime,
    val maghrib: LocalTime,
    val isha: LocalTime
) {
    fun nextPrayerBoundary(now: LocalTime): PrayerBoundarySchedule? = when {
        now < fajr -> PrayerBoundarySchedule("İmsak", fajr)
        now < sunrise -> PrayerBoundarySchedule("Güneş", sunrise)
        now < dhuhr -> PrayerBoundarySchedule("Öğle", dhuhr)
        now < asr -> PrayerBoundarySchedule("İkindi", asr)
        now < maghrib -> PrayerBoundarySchedule("Akşam", maghrib)
        now < isha -> PrayerBoundarySchedule("Yatsı", isha)
        else -> null
    }

    fun toWidgetText(): String = displayItems().joinToString(" • ") { "${it.label} ${it.time.format(TIME_FORMATTER)}" }

    fun toHighlightedDisplayItems(now: LocalTime): List<PrayerDisplayItem> {
        val items = displayItems()
        val activeIndex = currentPrayerIndex(now)
        return items.mapIndexed { index, item -> item.copy(highlighted = index == activeIndex) }
    }

    private fun displayItems(): List<PrayerDisplayItem> = listOf(
        PrayerDisplayItem("İmsak", fajr),
        PrayerDisplayItem("Güneş", sunrise),
        PrayerDisplayItem("Öğle", dhuhr),
        PrayerDisplayItem("İkindi", asr),
        PrayerDisplayItem("Akşam", maghrib),
        PrayerDisplayItem("Yatsı", isha)
    )

    private fun currentPrayerIndex(now: LocalTime): Int = when {
        now >= fajr && now < sunrise -> 0
        now >= sunrise && now < dhuhr -> 1
        now >= dhuhr && now < asr -> 2
        now >= asr && now < maghrib -> 3
        now >= maghrib && now < isha -> 4
        else -> 5
    }

    companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class PrayerDisplayItem(
    val label: String,
    val time: LocalTime,
    val highlighted: Boolean = false
) {
    fun render(): String = "${if (highlighted) "▶ " else ""}$label ${time.format(PrayerTimes.TIME_FORMATTER)}"
}

data class CachedPrayerDay(
    val date: LocalDate,
    val location: PrayerLocation,
    val timezone: ZoneId,
    val settings: PrayerCalculationSettings,
    val prayerTimes: PrayerTimes,
    val hijriText: String?,
    val fetchedAt: Instant
) {
    fun matches(
        date: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): Boolean = this.date == date && this.location == location && this.settings == settings
}

data class PrayerBoundarySchedule(
    val name: String,
    val time: LocalTime
)

fun parsePrayerTimesText(text: String): PrayerTimes? {
    val parts = text.trim().split(" • ").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size != 6) return null

    val values = parts.map { part ->
        part.substringAfter(' ', "").substringBefore(" ").let { raw ->
            runCatching { LocalTime.parse(raw) }.getOrNull()
        }
    }
    if (values.any { it == null }) return null
    return PrayerTimes(
        fajr = values[0]!!,
        sunrise = values[1]!!,
        dhuhr = values[2]!!,
        asr = values[3]!!,
        maghrib = values[4]!!,
        isha = values[5]!!
    )
}
