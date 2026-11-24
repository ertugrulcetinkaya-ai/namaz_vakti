package com.example.namazvakti.domain.model

import java.time.LocalTime

/** Device-only minute corrections applied to displayed and scheduled prayer times. */
data class PrayerTimeOffsets(
    val imsakMinutes: Int = 0,
    val sunriseMinutes: Int = 0,
    val dhuhrMinutes: Int = 0,
    val asrMinutes: Int = 0,
    val maghribMinutes: Int = 0,
    val ishaMinutes: Int = 0
) {
    init {
        require(values.all { it in MIN_OFFSET_MINUTES..MAX_OFFSET_MINUTES }) {
            "Prayer time offsets must be between $MIN_OFFSET_MINUTES and $MAX_OFFSET_MINUTES minutes"
        }
    }

    val isActive: Boolean
        get() = values.any { it != 0 }

    fun minutesFor(prayer: PrayerTimeName): Int = when (prayer) {
        PrayerTimeName.IMSAK -> imsakMinutes
        PrayerTimeName.SUNRISE -> sunriseMinutes
        PrayerTimeName.DHUHR -> dhuhrMinutes
        PrayerTimeName.ASR -> asrMinutes
        PrayerTimeName.MAGHRIB -> maghribMinutes
        PrayerTimeName.ISHA -> ishaMinutes
    }

    fun withOffset(prayer: PrayerTimeName, minutes: Int): PrayerTimeOffsets {
        require(minutes in MIN_OFFSET_MINUTES..MAX_OFFSET_MINUTES)
        return when (prayer) {
            PrayerTimeName.IMSAK -> copy(imsakMinutes = minutes)
            PrayerTimeName.SUNRISE -> copy(sunriseMinutes = minutes)
            PrayerTimeName.DHUHR -> copy(dhuhrMinutes = minutes)
            PrayerTimeName.ASR -> copy(asrMinutes = minutes)
            PrayerTimeName.MAGHRIB -> copy(maghribMinutes = minutes)
            PrayerTimeName.ISHA -> copy(ishaMinutes = minutes)
        }
    }

    private val values: List<Int>
        get() = listOf(
            imsakMinutes,
            sunriseMinutes,
            dhuhrMinutes,
            asrMinutes,
            maghribMinutes,
            ishaMinutes
        )

    companion object {
        const val MIN_OFFSET_MINUTES = -30
        const val MAX_OFFSET_MINUTES = 30
        val ZERO = PrayerTimeOffsets()
    }
}

/** Applies corrections without changing the local date or breaking prayer order. */
fun PrayerTimes.withOffsets(offsets: PrayerTimeOffsets): PrayerTimes {
    val original = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)
    val corrected = buildList {
        original.forEachIndexed { index, time ->
            val shiftedMinute = (time.hour * 60 + time.minute + offsets.minutesFor(PrayerTimeName.entries[index]))
                .coerceIn(0, MINUTES_PER_DAY - 1)
            val candidate = LocalTime.of(shiftedMinute / 60, shiftedMinute % 60)
            val previous = lastOrNull()
            val nextOriginal = original.getOrNull(index + 1)
            add(
                if ((previous == null || candidate > previous) &&
                    (nextOriginal == null || candidate < nextOriginal)
                ) {
                    candidate
                } else {
                    time
                }
            )
        }
    }
    return PrayerTimes(
        fajr = corrected[0],
        sunrise = corrected[1],
        dhuhr = corrected[2],
        asr = corrected[3],
        maghrib = corrected[4],
        isha = corrected[5]
    )
}

fun CachedPrayerDay.withOffsets(offsets: PrayerTimeOffsets): CachedPrayerDay =
    copy(prayerTimes = prayerTimes.withOffsets(offsets))

private const val MINUTES_PER_DAY = 24 * 60
