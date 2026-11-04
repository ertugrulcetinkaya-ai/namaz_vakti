package com.example.namazvakti.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PrayerLocation(
    val city: String,
    val country: String,
    val displayCity: String = city.uppercase(),
    val timezone: ZoneId = PrayerTimeProvider.DEFAULT_ZONE,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class PrayerCalculationSettings(
    val method: Int = DEFAULT_METHOD,
    val school: Int = DEFAULT_SCHOOL
) {
    init {
        require(method > 0) { "Calculation method must be positive" }
        require(school == SCHOOL_SHAFI || school == SCHOOL_HANAFI) {
            "Unsupported calculation school: $school"
        }
    }

    companion object {
        const val DEFAULT_METHOD = 13
        const val SCHOOL_SHAFI = 0
        const val SCHOOL_HANAFI = 1
        const val DEFAULT_SCHOOL = SCHOOL_SHAFI
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
    init {
        val times = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)
        require(times.zipWithNext().all { (earlier, later) -> earlier < later }) {
            "Prayer times must be strictly chronological"
        }
    }

    fun nextPrayerBoundary(now: LocalTime): PrayerBoundarySchedule? =
        nextPrayerName(now)?.let { PrayerBoundarySchedule(it.displayName, timeAt(it)) }

    fun currentPrayerName(now: LocalTime): PrayerTimeName =
        prayerSchedule().lastOrNull { (_, time) -> now >= time }?.first ?: PrayerTimeName.ISHA

    fun nextPrayerName(now: LocalTime): PrayerTimeName? =
        prayerSchedule().firstOrNull { (_, time) -> now < time }?.first

    fun timeAt(prayer: PrayerTimeName): LocalTime = when (prayer) {
        PrayerTimeName.IMSAK -> fajr
        PrayerTimeName.SUNRISE -> sunrise
        PrayerTimeName.DHUHR -> dhuhr
        PrayerTimeName.ASR -> asr
        PrayerTimeName.MAGHRIB -> maghrib
        PrayerTimeName.ISHA -> isha
    }

    fun toWidgetText(): String = displayItems().joinToString(" • ") { "${it.label} ${it.time.format(TIME_FORMATTER)}" }

    fun toHighlightedDisplayItems(now: LocalTime): List<PrayerDisplayItem> {
        val items = displayItems()
        val activePrayer = currentPrayerName(now)
        return items.mapIndexed { index, item -> item.copy(highlighted = index == activePrayer.ordinal) }
    }

    private fun displayItems(): List<PrayerDisplayItem> = listOf(
        PrayerDisplayItem("İmsak", fajr),
        PrayerDisplayItem("Güneş", sunrise),
        PrayerDisplayItem("Öğle", dhuhr),
        PrayerDisplayItem("İkindi", asr),
        PrayerDisplayItem("Akşam", maghrib),
        PrayerDisplayItem("Yatsı", isha)
    )

    private fun prayerSchedule(): List<Pair<PrayerTimeName, LocalTime>> = listOf(
        PrayerTimeName.IMSAK to fajr,
        PrayerTimeName.SUNRISE to sunrise,
        PrayerTimeName.DHUHR to dhuhr,
        PrayerTimeName.ASR to asr,
        PrayerTimeName.MAGHRIB to maghrib,
        PrayerTimeName.ISHA to isha
    )

    companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

enum class PrayerTimeName(val displayName: String) {
    IMSAK("İmsak"),
    SUNRISE("Güneş"),
    DHUHR("Öğle"),
    ASR("İkindi"),
    MAGHRIB("Akşam"),
    ISHA("Yatsı")
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
    val fetchedAt: Instant,
    val source: PrayerDataSource = PrayerDataSource.ALADHAN,
    val hijriDay: Int? = null,
    val hijriMonth: Int? = null,
    val hijriYear: Int? = null
) {
    fun matches(
        date: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): Boolean = this.date == date &&
        this.location.city == location.city &&
        this.location.country == location.country &&
        this.settings == settings
}

enum class PrayerDataSource {
    ALADHAN,
    LEGACY_CACHE
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
    return try {
        PrayerTimes(
            fajr = values[0]!!,
            sunrise = values[1]!!,
            dhuhr = values[2]!!,
            asr = values[3]!!,
            maghrib = values[4]!!,
            isha = values[5]!!
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
