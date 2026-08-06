package com.example.namazvakti

import android.util.Log
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

class PrayerTimesRepository(
    private val api: PrayerTimesApi = PrayerTimesApi(),
    private val store: PrayerTimesStore
) {
    fun refreshAndCache(): RefreshResult {
        return try {
            Log.d(TAG, "API request started")
            val city = store.getSelectedCity()
            val country = store.getSelectedCountry()
            Log.d(TAG, "selected city=$city country=$country")
            val result = api.fetchToday(city, country)
            Log.d(TAG, "API response success")
            val widgetText = result.prayerTimes.toWidgetText()
            val hijriText = result.hijriText
            val today = LocalDate.now(ZoneId.of("Europe/Istanbul")).toString()
            val cache = PrayerWidgetCache(
                date = today,
                widgetText = widgetText,
                hijriText = hijriText
            )
            Log.d(TAG, "cache write success start")
            store.save(cache)
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

    private fun failedRefresh(t: Exception): RefreshResult {
        Log.e(TAG, "API response failure=${t.javaClass.simpleName}: ${t.message}")
        val cached = runCatching { store.read() }.getOrNull()
        return if (cached != null) RefreshResult.StaleCache(cached, t) else RefreshResult.Failure(t)
    }

    fun currentCachedText(): String? = store.getCachedWidgetText()

    fun currentCachedDate(): String? = store.getCachedWidgetDate()

    fun selectedCity(): PrayerLocationConfig.CityOption {
        val city = store.getSelectedCity()
        val country = store.getSelectedCountry()
        return PrayerLocationConfig.optionForCityAndCountry(city, country)
    }

    fun cachedWidget(): PrayerWidgetCache? = runCatching { store.read() }.getOrNull()

    private companion object {
        const val TAG = "NamazWidget"
    }
}

sealed interface RefreshResult {
    data class Success(val cache: PrayerWidgetCache) : RefreshResult
    data class StaleCache(val cache: PrayerWidgetCache, val cause: Throwable) : RefreshResult
    data class Failure(val cause: Throwable) : RefreshResult
}
