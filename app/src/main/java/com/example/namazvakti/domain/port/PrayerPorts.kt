package com.example.namazvakti.domain.port

import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.domain.model.MonthResult
import com.example.namazvakti.domain.model.RamadanRange
import java.time.YearMonth

interface PrayerRefreshRepository {
    suspend fun refreshAndCache(): RefreshResult
    suspend fun ensureMonth(yearMonth: YearMonth): MonthResult =
        MonthResult(yearMonth, emptyList(), com.example.namazvakti.domain.model.MonthDataSource.ERROR)
    suspend fun ramadanRange(): RamadanRange? = null
}

interface PrayerRefreshScheduler {
    suspend fun enqueueRefresh(force: Boolean)
    suspend fun scheduleBoundary()
    suspend fun scheduleNotifications()
}

interface PrayerWidgetUpdatePort {
    suspend fun updateAll()
}
