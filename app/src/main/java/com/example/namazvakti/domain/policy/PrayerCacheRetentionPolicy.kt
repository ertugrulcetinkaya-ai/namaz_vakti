package com.example.namazvakti.domain.policy

import java.time.LocalDate
import java.time.YearMonth

data class PrayerCacheRetentionWindow(
    val minimumDate: LocalDate,
    val maximumDate: LocalDate
)

class PrayerCacheRetentionPolicy(
    private val pastDays: Long = DEFAULT_PAST_DAYS,
    private val futureMonths: Long = DEFAULT_FUTURE_MONTHS
) {
    init {
        require(pastDays >= 0) { "Past retention days must not be negative" }
        require(futureMonths >= 0) { "Future retention months must not be negative" }
    }

    fun window(today: LocalDate): PrayerCacheRetentionWindow = PrayerCacheRetentionWindow(
        minimumDate = today.minusDays(pastDays),
        maximumDate = today.plusMonths(futureMonths)
    )

    fun minimumCalendarMonth(today: LocalDate): YearMonth {
        val minimumDate = window(today).minimumDate
        val minimumMonth = YearMonth.from(minimumDate)
        return if (minimumDate == minimumMonth.atDay(1)) minimumMonth else minimumMonth.plusMonths(1)
    }

    private companion object {
        const val DEFAULT_PAST_DAYS = 90L
        const val DEFAULT_FUTURE_MONTHS = 13L
    }
}
