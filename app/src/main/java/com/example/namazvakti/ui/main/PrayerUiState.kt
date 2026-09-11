package com.example.namazvakti.ui.main

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.RefreshOrigin

sealed interface PrayerUiState {
    data object Loading : PrayerUiState
    data class Ready(
        val location: PrayerLocation,
        val freshness: Freshness,
        val operation: OperationState = OperationState.Idle,
        val cache: CachedPrayerDay? = null,
        val refreshOrigin: RefreshOrigin? = null,
        val lastError: PrayerError? = null
    ) : PrayerUiState
    data class Error(
        val location: PrayerLocation?,
        val error: PrayerError = PrayerError.Unknown
    ) : PrayerUiState
}

enum class Freshness { Fresh, Stale }
enum class OperationState { Idle, Refreshing, Refreshed, RefreshFailed }
