package com.example.namazvakti.domain.port

import com.example.namazvakti.domain.model.RefreshResult

interface PrayerRefreshRepository {
    suspend fun refreshAndCache(): RefreshResult
}

interface PrayerRefreshScheduler {
    suspend fun enqueueRefresh(force: Boolean)
    suspend fun scheduleBoundary()
}

interface PrayerWidgetUpdatePort {
    suspend fun updateAll()
}
