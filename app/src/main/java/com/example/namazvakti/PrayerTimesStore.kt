package com.example.namazvakti

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val Context.dataStore by preferencesDataStore(name = "prayer_times_store")

interface PrayerPreferences {
    suspend fun readCache(): CachedPrayerDay?
    suspend fun saveCache(cache: CachedPrayerDay)
    suspend fun readLocation(): PrayerLocation
    suspend fun saveLocation(location: PrayerLocation)
    suspend fun clearCache()
}

class PrayerTimesStore(private val context: Context) : PrayerPreferences {
    private val cacheKey = stringPreferencesKey("cached_prayer_day")
    private val legacyWidgetTextKey = stringPreferencesKey("cached_widget_text")
    private val legacyWidgetDateKey = stringPreferencesKey("cached_widget_date")
    private val legacyWidgetHijriKey = stringPreferencesKey("cached_widget_hijri_text")
    private val cityKey = stringPreferencesKey("selected_city")
    private val countryKey = stringPreferencesKey("selected_country")
    private val gson = Gson()

    override suspend fun saveCache(cache: CachedPrayerDay) {
        context.dataStore.edit { prefs ->
            prefs[cacheKey] = gson.toJson(cache.toDto())
            prefs.remove(legacyWidgetTextKey)
            prefs.remove(legacyWidgetDateKey)
            prefs.remove(legacyWidgetHijriKey)
        }
    }

    override suspend fun saveLocation(location: PrayerLocation) {
        context.dataStore.edit { prefs ->
            prefs[cityKey] = location.city
            prefs[countryKey] = location.country
        }
    }

    override suspend fun clearCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(cacheKey)
            prefs.remove(legacyWidgetTextKey)
            prefs.remove(legacyWidgetDateKey)
            prefs.remove(legacyWidgetHijriKey)
        }
    }

    override suspend fun readLocation(): PrayerLocation {
        val prefs = context.dataStore.data.first()
        val city = prefs[cityKey] ?: PrayerLocationConfig.defaultCity.city
        val country = prefs[countryKey] ?: PrayerLocationConfig.defaultCity.country
        val displayCity = PrayerLocationConfig.optionForCityAndCountry(city, country).displayCity
        return PrayerLocation(city, country, displayCity)
    }

    override suspend fun readCache(): CachedPrayerDay? = context.dataStore.data.first()[cacheKey]
        ?.let { json -> runCatching { gson.fromJson(json, CachedPrayerDayDto::class.java).toDomain() }.getOrNull() }
}

private data class CachedPrayerDayDto(
    val date: String? = null,
    val city: String? = null,
    val country: String? = null,
    val displayCity: String? = null,
    val timezone: String? = null,
    val method: Int? = null,
    val school: Int? = null,
    val fajr: String? = null,
    val sunrise: String? = null,
    val dhuhr: String? = null,
    val asr: String? = null,
    val maghrib: String? = null,
    val isha: String? = null,
    val hijriText: String? = null,
    val fetchedAtEpochMillis: Long? = null
) {
    fun toDomain(): CachedPrayerDay {
        val location = PrayerLocation(city.orEmpty(), country.orEmpty(), displayCity.orEmpty())
        return CachedPrayerDay(
            date = LocalDate.parse(date),
            location = location,
            timezone = ZoneId.of(timezone),
            settings = PrayerCalculationSettings(method ?: PrayerCalculationSettings.DEFAULT_METHOD, school ?: PrayerCalculationSettings.DEFAULT_SCHOOL),
            prayerTimes = PrayerTimes(
                LocalTime.parse(fajr.orEmpty()), LocalTime.parse(sunrise.orEmpty()), LocalTime.parse(dhuhr.orEmpty()),
                LocalTime.parse(asr.orEmpty()), LocalTime.parse(maghrib.orEmpty()), LocalTime.parse(isha.orEmpty())
            ),
            hijriText = hijriText,
            fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis ?: 0L)
        )
    }
}

private fun CachedPrayerDay.toDto() = CachedPrayerDayDto(
    date = date.toString(),
    city = location.city,
    country = location.country,
    displayCity = location.displayCity,
    timezone = timezone.id,
    method = settings.method,
    school = settings.school,
    fajr = prayerTimes.fajr.toString(),
    sunrise = prayerTimes.sunrise.toString(),
    dhuhr = prayerTimes.dhuhr.toString(),
    asr = prayerTimes.asr.toString(),
    maghrib = prayerTimes.maghrib.toString(),
    isha = prayerTimes.isha.toString(),
    hijriText = hijriText,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli()
)
