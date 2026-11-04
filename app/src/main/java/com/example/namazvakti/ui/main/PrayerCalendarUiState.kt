package com.example.namazvakti.ui.main

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.MonthDataSource
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.RamadanRange
import java.time.YearMonth

data class MonthlyUiState(
    val month: YearMonth,
    val loading: Boolean = false,
    val days: List<CachedPrayerDay> = emptyList(),
    val source: MonthDataSource? = null,
    val error: Throwable? = null
)

data class ImsakiyeUiState(
    val loading: Boolean = false,
    val location: PrayerLocation? = null,
    val range: RamadanRange? = null,
    val days: List<CachedPrayerDay> = emptyList(),
    val error: Throwable? = null
)
