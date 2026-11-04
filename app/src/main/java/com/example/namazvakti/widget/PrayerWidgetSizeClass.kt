package com.example.namazvakti.widget

enum class PrayerWidgetSizeClass {
    SMALL,
    MEDIUM,
    LARGE;

    companion object {
        const val SMALL_MAX_WIDTH_DP = 180
        const val LARGE_MIN_WIDTH_DP = 220
        const val LARGE_MIN_HEIGHT_DP = 100

        fun from(widthDp: Int, heightDp: Int): PrayerWidgetSizeClass = when {
            widthDp < SMALL_MAX_WIDTH_DP -> SMALL
            widthDp >= LARGE_MIN_WIDTH_DP && heightDp >= LARGE_MIN_HEIGHT_DP -> LARGE
            else -> MEDIUM
        }
    }
}
