package com.example.namazvakti.ui.main

import androidx.annotation.StringRes
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.PrayerDataSource

internal enum class PrayerSourceBadge(
    @StringRes val label: Int,
    @StringRes val description: Int
) {
    DIYANET(R.string.prayer_source_badge_diyanet, R.string.prayer_source_diyanet),
    ALADHAN(R.string.prayer_source_badge_aladhan, R.string.prayer_source_aladhan),
    ALADHAN_PROVINCE_CENTER(
        R.string.prayer_source_badge_aladhan_province_center,
        R.string.prayer_source_aladhan_province_center
    ),
    LEGACY_CACHE(R.string.prayer_source_badge_legacy, R.string.prayer_source_legacy_cache)
}

internal fun prayerSourceBadgeFor(
    source: PrayerDataSource,
    usesProvinceCenterFallback: Boolean
): PrayerSourceBadge = when (source) {
    PrayerDataSource.DIYANET -> PrayerSourceBadge.DIYANET
    PrayerDataSource.ALADHAN -> if (usesProvinceCenterFallback) {
        PrayerSourceBadge.ALADHAN_PROVINCE_CENTER
    } else {
        PrayerSourceBadge.ALADHAN
    }
    PrayerDataSource.LEGACY_CACHE -> PrayerSourceBadge.LEGACY_CACHE
}
