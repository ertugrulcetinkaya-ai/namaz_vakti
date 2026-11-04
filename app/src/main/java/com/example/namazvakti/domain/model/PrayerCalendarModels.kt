package com.example.namazvakti.domain.model

import java.time.YearMonth

enum class MonthDataSource { CACHE, NETWORK, PARTIAL, ERROR }

data class MonthResult(
    val yearMonth: YearMonth,
    val days: List<CachedPrayerDay>,
    val source: MonthDataSource,
    val error: Throwable? = null
)
