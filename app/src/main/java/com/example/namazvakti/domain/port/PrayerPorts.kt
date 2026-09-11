package com.example.namazvakti.domain.port

import android.content.Context
import com.example.namazvakti.domain.model.RefreshResult

interface PrayerRefreshRepository {
    suspend fun refreshAndCache(): RefreshResult
}

interface PrayerRefreshScheduler {
    suspend fun enqueueRefresh(context: Context, force: Boolean)
    suspend fun scheduleBoundary(context: Context)
}

interface PrayerWidgetUpdatePort {
    suspend fun updateAll(context: Context)
}
