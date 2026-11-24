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
    val longitude: Double? = null,
    val diyanetDistrictId: String? = null,
    val districtName: String? = null
) {
    val cacheKey: String
        get() = if (isCentralDistrict) city else "$city#$diyanetDistrictId"

    val isCentralDistrict: Boolean
        get() = diyanetDistrictId == null ||
            diyanetDistrictId == DiyanetDistricts.forCity(city, country)?.centralDistrictId

    val displayName: String
        get() {
            if (isCentralDistrict) return displayCity
            val name = districtName ?: diyanetDistrictId?.let { id ->
                val provinceId = DiyanetDistricts.forCity(city, country)?.id
                DiyanetDistricts.byId[id]?.takeIf { it.provinceId == provinceId }?.name
            }
            return name?.let { "$it, $displayCity" } ?: displayCity
        }

    companion object {
        fun fromCacheKey(key: String): Pair<String, String?> {
            val separator = key.indexOf('#')
            return if (separator < 0) {
                key to null
            } else {
                key.substring(0, separator) to key.substring(separator + 1)
            }
        }
    }
}

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

enum class PrayerTimeName(val displayName: String, val storageKey: String) {
    IMSAK("İmsak", "imsak"),
    SUNRISE("Güneş", "sunrise"),
    DHUHR("Öğle", "dhuhr"),
    ASR("İkindi", "asr"),
    MAGHRIB("Akşam", "maghrib"),
    ISHA("Yatsı", "isha")
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
    val usesProvinceCenterFallback: Boolean
        get() = source == PrayerDataSource.ALADHAN &&
            location.diyanetDistrictId != null &&
            !location.isCentralDistrict &&
            !location.hasDistrictCoordinates()

    private fun PrayerLocation.hasDistrictCoordinates(): Boolean {
        val province = DiyanetDistricts.forCity(city, country) ?: return false
        val district = DiyanetDistricts.byId[diyanetDistrictId]
            ?.takeIf { it.provinceId == province.id } ?: return false
        return district.latitude != null && district.longitude != null
    }

    fun matches(
        date: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): Boolean = this.date == date &&
        this.location.cacheKey == location.cacheKey &&
        this.location.country == location.country &&
        this.settings == settings
}

enum class PrayerDataSource {
    DIYANET,
    ALADHAN,
    LEGACY_CACHE
}

data class PrayerBoundarySchedule(
    val name: String,
    val time: LocalTime
)
