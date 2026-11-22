package com.example.namazvakti.widget

enum class PrayerWidgetSizeClass {
    SMALL,
    MEDIUM,
    LARGE;

    companion object {
        // A 2x1 widget is ~159dp on the S24 Ultra, while three launcher cells can
        // be about 280dp on Pixel; keep the multi-column layouts reachable there.
        const val WIDE_MIN_WIDTH_DP = 280
        const val LARGE_MIN_HEIGHT_DP = 200

        fun from(widthDp: Int, heightDp: Int): PrayerWidgetSizeClass = when {
            widthDp < WIDE_MIN_WIDTH_DP -> SMALL
            heightDp >= LARGE_MIN_HEIGHT_DP -> LARGE
            else -> MEDIUM
        }
    }
}
