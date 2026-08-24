package com.example.namazvakti

sealed interface PrayerUiState {
    data object Loading : PrayerUiState
    data class Ready(
        val location: PrayerLocation,
        val freshness: Freshness,
        val operation: OperationState = OperationState.Idle,
        val cache: CachedPrayerDay? = null,
        val refreshOrigin: RefreshOrigin? = null
    ) : PrayerUiState
    data class Error(val location: PrayerLocation?) : PrayerUiState
}

enum class Freshness { Fresh, Stale }
enum class OperationState { Idle, Refreshing, Refreshed, RefreshFailed }
