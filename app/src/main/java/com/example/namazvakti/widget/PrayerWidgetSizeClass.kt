package com.example.namazvakti.widget

enum class PrayerWidgetSizeClass {
    SMALL,
    MEDIUM,
    LARGE;

    companion object {
        // On the S24 Ultra, a 2x1 widget is ~159dp wide, a wide one-row widget
        // is ~515x126dp, and two rows are at least ~278dp high.
        const val WIDE_MIN_WIDTH_DP = 400
        const val LARGE_MIN_HEIGHT_DP = 200

        fun from(widthDp: Int, heightDp: Int): PrayerWidgetSizeClass = when {
            widthDp < WIDE_MIN_WIDTH_DP -> SMALL
            heightDp >= LARGE_MIN_HEIGHT_DP -> LARGE
            else -> MEDIUM
        }
    }
}
