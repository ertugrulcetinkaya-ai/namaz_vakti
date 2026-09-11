package com.example.namazvakti.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerLocationConfig
import com.example.namazvakti.domain.model.PrayerStorageException
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.policy.PrayerCacheRetentionPolicy
import com.example.namazvakti.domain.model.StorageFailureKind
import com.google.gson.Gson
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteTableLockedException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException

private val Context.dataStore by preferencesDataStore(name = "prayer_times_store")

interface PrayerPreferences {
    suspend fun readCache(): CachedPrayerDay?
    suspend fun readCache(
        date: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): CachedPrayerDay? = readCache()?.takeIf { it.matches(date, location, settings) }
    suspend fun saveCache(cache: CachedPrayerDay)
    suspend fun saveCaches(caches: List<CachedPrayerDay>) {
        caches.forEach { saveCache(it) }
    }
    suspend fun hasCoverage(
        startDate: LocalDate,
        endDate: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): Boolean = false
    suspend fun housekeep() = Unit
    suspend fun readLocation(): PrayerLocation
    suspend fun saveLocation(location: PrayerLocation)
    suspend fun clearCache()
}

class PrayerTimesStore(
    private val context: Context,
    private val prayerDayDao: PrayerDayDao,
    private val timeProvider: PrayerTimeProvider = PrayerTimeProvider(),
    private val settings: PrayerCalculationSettings = PrayerCalculationSettings(),
    private val retentionPolicy: PrayerCacheRetentionPolicy = PrayerCacheRetentionPolicy()
) : PrayerPreferences {
    private val cacheKey = stringPreferencesKey("cached_prayer_day")
    private val legacyWidgetTextKey = stringPreferencesKey("cached_widget_text")
    private val legacyWidgetDateKey = stringPreferencesKey("cached_widget_date")
    private val legacyWidgetHijriKey = stringPreferencesKey("cached_widget_hijri_text")
    private val cityKey = stringPreferencesKey("selected_city")
    private val countryKey = stringPreferencesKey("selected_country")
    private val locationTimezoneKey = stringPreferencesKey("selected_timezone")
    private val codec = CachedPrayerDayCodec()
    private val migrationMutex = Mutex()
    @Volatile private var migrationComplete = false
    @Volatile private var lastRetentionDate: LocalDate? = null

    override suspend fun saveCache(cache: CachedPrayerDay) = storageOperation {
        migrateLegacyCache()
        prayerDayDao.upsertAll(listOf(cache.toEntity()))
        pruneIfDue(cache.location)
    }

    override suspend fun saveCaches(caches: List<CachedPrayerDay>) = storageOperation {
        if (caches.isEmpty()) return@storageOperation
        migrateLegacyCache()
        prayerDayDao.upsertAll(caches.map(CachedPrayerDay::toEntity))
        pruneIfDue(caches.first().location)
    }

    override suspend fun saveLocation(location: PrayerLocation) = storageOperation {
        context.dataStore.edit { prefs ->
            prefs[cityKey] = location.city
            prefs[countryKey] = location.country
            prefs[locationTimezoneKey] = location.timezone.id
        }
        Unit
    }

    override suspend fun clearCache() = storageOperation {
        migrateLegacyCache()
        prayerDayDao.clearAll()
        context.dataStore.edit { prefs ->
            prefs.remove(cacheKey)
            prefs.remove(legacyWidgetTextKey)
            prefs.remove(legacyWidgetDateKey)
            prefs.remove(legacyWidgetHijriKey)
        }
        Unit
    }

    override suspend fun readLocation(): PrayerLocation = storageOperation {
        val prefs = context.dataStore.data.first()
        val city = prefs[cityKey] ?: PrayerLocationConfig.defaultCity.city
        val country = prefs[countryKey] ?: PrayerLocationConfig.defaultCity.country
        val displayCity = PrayerLocationConfig.optionForCityAndCountry(city, country).displayCity
        val timezone = prefs[locationTimezoneKey]?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: PrayerTimeProvider.DEFAULT_ZONE
        PrayerLocation(city, country, displayCity, timezone)
    }

    override suspend fun readCache(): CachedPrayerDay? = storageOperation {
        migrateLegacyCache()
        val location = readLocation()
        val today = timeProvider.today(location.timezone)
        prayerDayDao.find(
            today.toString(), location.city, location.country, settings.method, settings.school
        )?.toDomain() ?: prayerDayDao.latestOnOrBefore(
            today.toString(), location.city, location.country, settings.method, settings.school
        )?.toDomain()
    }

    override suspend fun readCache(
        date: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): CachedPrayerDay? = storageOperation {
        migrateLegacyCache()
        prayerDayDao.find(
            date.toString(), location.city, location.country, settings.method, settings.school
        )?.toDomain()
    }

    override suspend fun hasCoverage(
        startDate: LocalDate,
        endDate: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): Boolean {
        require(!endDate.isBefore(startDate)) { "End date must not be before start date" }
        return storageOperation {
            migrateLegacyCache()
            val expectedDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
            prayerDayDao.countBetween(
                startDate.toString(), endDate.toString(), location.city, location.country,
                settings.method, settings.school
            ) == expectedDays
        }
    }

    override suspend fun housekeep() = storageOperation {
        migrateLegacyCache()
        pruneIfDue(readLocation())
    }

    private suspend fun pruneIfDue(location: PrayerLocation) {
        val today = timeProvider.today(location.timezone)
        if (lastRetentionDate == today) return
        val window = retentionPolicy.window(today)
        prayerDayDao.deleteOutsideDateWindow(
            minimumDate = window.minimumDate.toString(),
            maximumDate = window.maximumDate.toString()
        )
        lastRetentionDate = today
    }

    private suspend fun migrateLegacyCache() {
        if (migrationComplete) return
        migrationMutex.withLock {
            if (migrationComplete) return@withLock
            val prefs = context.dataStore.data.first()
            prefs[cacheKey]?.let(codec::decode)?.let { legacy ->
                prayerDayDao.upsertAll(listOf(legacy.toEntity()))
            }
            context.dataStore.edit {
                it.remove(cacheKey)
                it.remove(legacyWidgetTextKey)
                it.remove(legacyWidgetDateKey)
                it.remove(legacyWidgetHijriKey)
            }
            migrationComplete = true
        }
    }

    private suspend fun <T> storageOperation(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: PrayerStorageException) {
        throw e
    } catch (e: Exception) {
        throw PrayerStorageException(
            kind = storageFailureKind(e),
            message = "Prayer storage operation failed",
            cause = e
        )
    }

    private fun storageFailureKind(cause: Throwable): StorageFailureKind {
        val chain = generateSequence(cause) { it.cause }.toList()
        return when {
            chain.any {
                it is SQLiteDatabaseLockedException || it is SQLiteTableLockedException
            } -> StorageFailureKind.TRANSIENT
            chain.any {
                it is SQLiteDatabaseCorruptException ||
                    it is SQLiteDiskIOException ||
                    it is SQLiteFullException
            } -> StorageFailureKind.PERMANENT
            else -> StorageFailureKind.UNKNOWN
        }
    }
}

internal class CachedPrayerDayCodec(private val gson: Gson = Gson()) {
    fun encode(cache: CachedPrayerDay): String = gson.toJson(cache.toDto())

    fun decode(json: String): CachedPrayerDay? = runCatching {
        gson.fromJson(json, CachedPrayerDayDto::class.java).toDomain()
    }.getOrNull()
}

internal data class CachedPrayerDayDto(
    val schemaVersion: Int? = null,
    val date: String? = null,
    val city: String? = null,
    val country: String? = null,
    val displayCity: String? = null,
    val locationTimezone: String? = null,
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
        require(schemaVersion == null || schemaVersion == CACHE_SCHEMA_VERSION) {
            "Unsupported cache schema"
        }
        val cityValue = city?.trim().orEmpty()
        val countryValue = country?.trim().orEmpty()
        require(cityValue.isNotEmpty() && countryValue.isNotEmpty()) { "Missing cache location" }
        val cacheTimezone = ZoneId.of(requireNotNull(timezone))
        val location = PrayerLocation(
            city = cityValue,
            country = countryValue,
            displayCity = displayCity?.trim().takeUnless { it.isNullOrEmpty() } ?: cityValue.uppercase(),
            timezone = locationTimezone?.let(ZoneId::of) ?: cacheTimezone
        )
        return CachedPrayerDay(
            date = LocalDate.parse(requireNotNull(date)),
            location = location,
            timezone = cacheTimezone,
            settings = PrayerCalculationSettings(method ?: PrayerCalculationSettings.DEFAULT_METHOD, school ?: PrayerCalculationSettings.DEFAULT_SCHOOL),
            prayerTimes = PrayerTimes(
                LocalTime.parse(fajr.orEmpty()), LocalTime.parse(sunrise.orEmpty()), LocalTime.parse(dhuhr.orEmpty()),
                LocalTime.parse(asr.orEmpty()), LocalTime.parse(maghrib.orEmpty()), LocalTime.parse(isha.orEmpty())
            ),
            hijriText = hijriText,
            fetchedAt = Instant.ofEpochMilli(requireNotNull(fetchedAtEpochMillis))
        )
    }
}

private fun CachedPrayerDay.toDto() = CachedPrayerDayDto(
    schemaVersion = CACHE_SCHEMA_VERSION,
    date = date.toString(),
    city = location.city,
    country = location.country,
    displayCity = location.displayCity,
    locationTimezone = location.timezone.id,
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

private const val CACHE_SCHEMA_VERSION = 1
