package com.example.namazvakti

import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

class PrayerTimesRepository(
    private val api: PrayerTimesApi = PrayerTimesApi(),
    private val store: PrayerTimesStore
) {
    fun refreshAndCache(): PrayerWidgetCache {
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
            cache
        } catch (t: Throwable) {
            Log.d(TAG, "API response failure=${t.javaClass.simpleName}: ${t.message}")
            val cached = runCatching { store.read() }.getOrNull()
            cached ?: PrayerWidgetCache(
                date = LocalDate.now(ZoneId.of("Europe/Istanbul")).toString(),
                widgetText = "Vakitler alınamadı",
                hijriText = null
            )
        }
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
