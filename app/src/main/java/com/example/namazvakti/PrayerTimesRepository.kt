package com.example.namazvakti

import android.util.Log
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

class PrayerTimesRepository(
    private val api: PrayerTimesApi = PrayerTimesApi(),
    private val store: PrayerPreferences,
    private val settings: PrayerCalculationSettings = PrayerCalculationSettings(),
    private val timeProvider: PrayerTimeProvider = PrayerTimeProvider()
) {
    suspend fun refreshAndCache(): RefreshResult {
        return try {
            Log.d(TAG, "API request started")
            val location = store.readLocation()
            Log.d(TAG, "selected city=${location.city} country=${location.country}")
            val result = api.fetchToday(location.city, location.country, settings)
            Log.d(TAG, "API response success")
            val hijriText = result.hijriText
            val cache = CachedPrayerDay(
                date = timeProvider.today(),
                location = location,
                timezone = result.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: location.timezone,
                settings = settings,
                prayerTimes = result.prayerTimes,
                hijriText = hijriText,
                fetchedAt = timeProvider.now().toInstant()
            )
            Log.d(TAG, "cache write success start")
            store.saveCache(cache)
            Log.d(TAG, "cache write success")
            RefreshResult.Success(cache)
        } catch (e: CancellationException) {
            throw e
        } catch (t: IOException) {
            return failedRefresh(t)
        } catch (t: PrayerTimesApiException) {
            return failedRefresh(t)
        }
    }

    private suspend fun failedRefresh(t: Exception): RefreshResult {
        Log.e(TAG, "API response failure=${t.javaClass.simpleName}: ${t.message}")
        val cached = runCatching { store.readCache() }.getOrNull()
        return if (cached != null) RefreshResult.StaleCache(cached, t) else RefreshResult.Failure(t)
    }

    suspend fun selectedLocation(): PrayerLocation = store.readLocation()

    suspend fun cachedWidget(): CachedPrayerDay? = runCatching { store.readCache() }.getOrNull()

    private companion object {
        const val TAG = "NamazWidget"
    }
}

sealed interface RefreshResult {
    data class Success(val cache: CachedPrayerDay) : RefreshResult
    data class StaleCache(val cache: CachedPrayerDay, val cause: Throwable) : RefreshResult
    data class Failure(val cause: Throwable) : RefreshResult
}
