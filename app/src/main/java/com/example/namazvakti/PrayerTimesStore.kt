package com.example.namazvakti

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "prayer_times_store")

class PrayerTimesStore(private val context: Context) {
    private val widgetTextKey = stringPreferencesKey("cached_widget_text")
    private val widgetDateKey = stringPreferencesKey("cached_widget_date")
    private val widgetHijriTextKey = stringPreferencesKey("cached_widget_hijri_text")
    private val cityKey = stringPreferencesKey("selected_city")
    private val countryKey = stringPreferencesKey("selected_country")

    fun save(cache: PrayerWidgetCache) = runBlocking {
        context.dataStore.edit { prefs ->
            prefs[widgetDateKey] = cache.date
            prefs[widgetTextKey] = cache.widgetText
            if (cache.hijriText.isNullOrBlank()) {
                prefs.remove(widgetHijriTextKey)
            } else {
                prefs[widgetHijriTextKey] = cache.hijriText
            }
        }
    }

    fun saveSelection(city: String, country: String) = runBlocking {
        context.dataStore.edit { prefs ->
            prefs[cityKey] = city
            prefs[countryKey] = country
        }
    }

    fun clearWidgetCache() = runBlocking {
        context.dataStore.edit { prefs ->
            prefs.remove(widgetDateKey)
            prefs.remove(widgetTextKey)
            prefs.remove(widgetHijriTextKey)
        }
    }

    fun getCachedWidgetText(): String? = runBlocking {
        context.dataStore.data.first()[widgetTextKey]
    }

    fun getCachedWidgetDate(): String? = runBlocking {
        context.dataStore.data.first()[widgetDateKey]
    }

    fun getSelectedCity(): String = runBlocking {
        context.dataStore.data.first()[cityKey] ?: PrayerLocationConfig.defaultCity.city
    }

    fun getSelectedCountry(): String = runBlocking {
        context.dataStore.data.first()[countryKey] ?: PrayerLocationConfig.defaultCity.country
    }

    fun read(): PrayerWidgetCache? = runBlocking {
        val prefs = context.dataStore.data.first()
        val date = prefs[widgetDateKey] ?: return@runBlocking null
        val text = prefs[widgetTextKey] ?: return@runBlocking null
        PrayerWidgetCache(
            date = date,
            widgetText = text,
            hijriText = prefs[widgetHijriTextKey]
        )
    }
}
