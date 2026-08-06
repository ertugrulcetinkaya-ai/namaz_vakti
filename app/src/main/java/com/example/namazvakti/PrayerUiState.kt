package com.example.namazvakti

sealed interface PrayerUiState {
    data object Loading : PrayerUiState
    data class Ready(
        val location: PrayerLocation,
        val stale: Boolean = false,
        val message: String? = null
    ) : PrayerUiState
    data class Error(val message: String, val location: PrayerLocation?) : PrayerUiState
}
