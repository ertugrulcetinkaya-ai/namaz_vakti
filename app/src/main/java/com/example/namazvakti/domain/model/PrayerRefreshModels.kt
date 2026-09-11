package com.example.namazvakti.domain.model

sealed interface RefreshResult {
    data class Success(
        val cache: CachedPrayerDay,
        val origin: RefreshOrigin = RefreshOrigin.NETWORK
    ) : RefreshResult

    data class StaleCache(
        val cache: CachedPrayerDay,
        val cause: Throwable,
        val error: PrayerError = classifyPrayerError(cause)
    ) : RefreshResult

    data class Failure(
        val cause: Throwable,
        val error: PrayerError = classifyPrayerError(cause)
    ) : RefreshResult
}

enum class RefreshOrigin { NETWORK, LOCAL_CACHE }
