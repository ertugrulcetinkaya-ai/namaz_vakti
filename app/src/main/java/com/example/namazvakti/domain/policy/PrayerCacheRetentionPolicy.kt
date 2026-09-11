package com.example.namazvakti.domain.policy

import java.time.LocalDate

data class PrayerCacheRetentionWindow(
    val minimumDate: LocalDate,
    val maximumDate: LocalDate
)

class PrayerCacheRetentionPolicy(
    private val pastDays: Long = DEFAULT_PAST_DAYS,
    private val futureDays: Long = DEFAULT_FUTURE_DAYS
) {
    init {
        require(pastDays >= 0) { "Past retention days must not be negative" }
        require(futureDays >= 0) { "Future retention days must not be negative" }
    }

    fun window(today: LocalDate): PrayerCacheRetentionWindow = PrayerCacheRetentionWindow(
        minimumDate = today.minusDays(pastDays),
        maximumDate = today.plusDays(futureDays)
    )

    private companion object {
        const val DEFAULT_PAST_DAYS = 90L
        const val DEFAULT_FUTURE_DAYS = 60L
    }
}
