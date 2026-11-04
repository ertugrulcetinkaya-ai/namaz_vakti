package com.example.namazvakti.domain.model

/**
 * Safe-side extrema of official Diyanet minus untuned AlAdhan method 13 samples from
 * 2026. Imsak/Güneş use minimum deltas; later prayers use maximum deltas.
 * AlAdhan method 13 defaults (Sunrise=-7, Dhuhr=5, Asr=4, Maghrib=7) are translated
 * to tune values because non-zero tune entries replace those defaults.
 */
object PrayerTimeAdjustments {
    const val APP_IMSAK_FAJR_MINUTES = -1
    const val SUNRISE_MINUTES = -1
    const val DHUHR_MINUTES = 1
    const val ASR_MINUTES = 1
    const val MAGHRIB_MINUTES = 2
    const val ISHA_MINUTES = 2

    const val ALADHAN_IMSAK_MINUTES = 0
    const val ALADHAN_SUNSET_MINUTES = 0
    const val ALADHAN_MIDNIGHT_MINUTES = 0
    const val METHOD_13_DEFAULT_SUNRISE_MINUTES = -7
    const val METHOD_13_DEFAULT_DHUHR_MINUTES = 5
    const val METHOD_13_DEFAULT_ASR_MINUTES = 4
    const val METHOD_13_DEFAULT_MAGHRIB_MINUTES = 7

    const val ALADHAN_SUNRISE_TUNE_MINUTES = METHOD_13_DEFAULT_SUNRISE_MINUTES + SUNRISE_MINUTES
    const val ALADHAN_DHUHR_TUNE_MINUTES = METHOD_13_DEFAULT_DHUHR_MINUTES + DHUHR_MINUTES
    const val ALADHAN_ASR_TUNE_MINUTES = METHOD_13_DEFAULT_ASR_MINUTES + ASR_MINUTES
    const val ALADHAN_MAGHRIB_TUNE_MINUTES = METHOD_13_DEFAULT_MAGHRIB_MINUTES + MAGHRIB_MINUTES

    const val ALADHAN_TUNE =
        "$ALADHAN_IMSAK_MINUTES,$APP_IMSAK_FAJR_MINUTES,$ALADHAN_SUNRISE_TUNE_MINUTES," +
            "$ALADHAN_DHUHR_TUNE_MINUTES,$ALADHAN_ASR_TUNE_MINUTES," +
            "$ALADHAN_MAGHRIB_TUNE_MINUTES,$ALADHAN_SUNSET_MINUTES," +
            "$ISHA_MINUTES,$ALADHAN_MIDNIGHT_MINUTES"
}
