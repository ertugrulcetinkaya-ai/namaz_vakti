package com.example.namazvakti.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerLocationConfig
import com.example.namazvakti.domain.model.PrayerStorageException
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.StorageFailureKind
import com.example.namazvakti.domain.policy.PrayerCacheRetentionPolicy
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteTableLockedException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException

private val Context.dataStore by preferencesDataStore(name = "prayer_times_store")

data class PrayerCacheDateBounds(
    val firstDate: LocalDate,
    val lastDate: LocalDate
)

interface PrayerPreferences {
    suspend fun readCache(): CachedPrayerDay?
    suspend fun readCache(
        date: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): CachedPrayerDay? = readCache()?.takeIf { it.matches(date, location, settings) }
    suspend fun readCaches(
        startDate: LocalDate,
        endDate: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): List<CachedPrayerDay> = buildList {
        var date = startDate
        while (!date.isAfter(endDate)) {
            readCache(date, location, settings)?.let(::add)
            date = date.plusDays(1)
        }
    }
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
    suspend fun readLocation(): PrayerLocation
    suspend fun saveLocation(location: PrayerLocation)
    suspend fun clearCache()
}

class PrayerTimesStore(
    private val context: Context,
    private val prayerDayDao: PrayerDayDao,
    private val timeProvider: PrayerTimeProvider = PrayerTimeProvider(),
    private val settings: PrayerCalculationSettings = PrayerCalculationSettings(),
    private val retentionPolicy: PrayerCacheRetentionPolicy = PrayerCacheRetentionPolicy(),
    suppliedDataStore: DataStore<Preferences>? = null
) : PrayerPreferences {
    private val dataStore = suppliedDataStore ?: context.dataStore
    private val cacheKey = stringPreferencesKey("cached_prayer_day")
    private val legacyWidgetTextKey = stringPreferencesKey("cached_widget_text")
    private val legacyWidgetDateKey = stringPreferencesKey("cached_widget_date")
    private val legacyWidgetHijriKey = stringPreferencesKey("cached_widget_hijri_text")
    private val cityKey = stringPreferencesKey("selected_city")
    private val countryKey = stringPreferencesKey("selected_country")
    private val locationTimezoneKey = stringPreferencesKey("selected_timezone")
    private val latitudeKey = stringPreferencesKey("selected_latitude")
    private val longitudeKey = stringPreferencesKey("selected_longitude")
    private val districtIdKey = stringPreferencesKey("selected_district_id")
    private val districtNameKey = stringPreferencesKey("selected_district_name")
    private val prayerDataVersionKey = intPreferencesKey("prayer_data_version")
    private val districtCacheCleanupKey = booleanPreferencesKey("district_aladhan_cache_cleanup_v1")
    private val districtCacheCleanupMutex = Mutex()
    private val dataVersionMutex = Mutex()
    private val dataVersionGate = PrayerDataVersionGate(PrayerDataVersionGate.CURRENT_VERSION)
    @Volatile private var districtCacheCleanupComplete = false
    @Volatile private var dataVersionCheckComplete = false
    @Volatile private var lastRetentionDate: LocalDate? = null

    override suspend fun saveCache(cache: CachedPrayerDay) = storageOperation {
        prayerDayDao.upsertAll(listOf(cache.toEntity()))
        pruneIfDue(cache.location)
    }

    override suspend fun saveCaches(caches: List<CachedPrayerDay>) = storageOperation {
        if (caches.isEmpty()) return@storageOperation
        val retainedCaches = caches.filter { cache ->
            val window = retentionPolicy.window(timeProvider.today(cache.location.timezone))
            cache.date in window.minimumDate..window.maximumDate
        }
        if (retainedCaches.isNotEmpty()) {
            prayerDayDao.upsertAll(retainedCaches.map(CachedPrayerDay::toEntity))
        }
        pruneIfDue(caches.first().location)
    }

    override suspend fun saveLocation(location: PrayerLocation) = storageOperation {
        val provinceCoordinates = if (!location.isCentralDistrict) {
            PrayerLocationConfig.optionForCityAndCountry(location.city, location.country).let { option ->
                option.latitude?.let { latitude -> option.longitude?.let { longitude -> latitude to longitude } }
            }
        } else {
            null
        }
        val coordinatesToPersist = provinceCoordinates ?: location.latitude?.let { latitude ->
            location.longitude?.let { longitude -> latitude to longitude }
        }
        dataStore.edit { prefs ->
            prefs[cityKey] = location.city
            prefs[countryKey] = location.country
            prefs[locationTimezoneKey] = location.timezone.id
            coordinatesToPersist?.let { (latitude, longitude) ->
                prefs[latitudeKey] = latitude.toString()
                prefs[longitudeKey] = longitude.toString()
            } ?: run {
                prefs.remove(latitudeKey)
                prefs.remove(longitudeKey)
            }
            if (location.isCentralDistrict) {
                prefs.remove(districtIdKey)
                prefs.remove(districtNameKey)
            } else {
                location.diyanetDistrictId?.let { prefs[districtIdKey] = it }
                    ?: prefs.remove(districtIdKey)
                location.districtName?.let { prefs[districtNameKey] = it }
                    ?: prefs.remove(districtNameKey)
            }
        }
        Unit
    }

    override suspend fun clearCache() = storageOperation {
        prayerDayDao.clearAll()
        dataStore.edit { prefs ->
            prefs.remove(cacheKey)
            prefs.remove(legacyWidgetTextKey)
            prefs.remove(legacyWidgetDateKey)
            prefs.remove(legacyWidgetHijriKey)
        }
        Unit
    }

    override suspend fun readLocation(): PrayerLocation = storageOperation {
        val prefs = dataStore.data.first()
        val city = prefs[cityKey] ?: PrayerLocationConfig.defaultCity.city
        val country = prefs[countryKey] ?: PrayerLocationConfig.defaultCity.country
        val option = PrayerLocationConfig.optionForCityAndCountry(city, country)
        val province = DiyanetDistricts.forCity(city, country)
        val storedDistrictId = prefs[districtIdKey]
        val storedDistrictName = prefs[districtNameKey]
        val district = storedDistrictId?.let { id ->
            DiyanetDistricts.byId[id]?.takeIf { it.provinceId == province?.id }
        }?.takeUnless { it.id == province?.centralDistrictId }
        val timezone = prefs[locationTimezoneKey]?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: PrayerTimeProvider.DEFAULT_ZONE
        val storedCoordinates = prefs[latitudeKey]?.toDoubleOrNull()?.let { latitude ->
            prefs[longitudeKey]?.toDoubleOrNull()?.let { longitude -> latitude to longitude }
        }
        val districtCoordinates = district?.let { selectedDistrict ->
            selectedDistrict.latitude?.let { latitude ->
                selectedDistrict.longitude?.let { longitude -> latitude to longitude }
            }
        }
        val coordinates = districtCoordinates ?: storedCoordinates ?: option.latitude?.let { latitude ->
            option.longitude?.let { longitude -> latitude to longitude }
        }
        PrayerLocation(
            city = city,
            country = country,
            displayCity = option.displayCity,
            timezone = timezone,
            latitude = coordinates?.first,
            longitude = coordinates?.second,
            diyanetDistrictId = district?.id,
            districtName = district?.let {
                storedDistrictName?.takeIf { name -> name == it.name } ?: it.name
            }
        )
    }

    override suspend fun readCache(): CachedPrayerDay? = storageOperation {
        val location = readLocation()
        val today = timeProvider.today(location.timezone)
        prayerDayDao.find(
            today.toString(), location.cacheKey, location.country, settings.method, settings.school
        )?.toDomain() ?: prayerDayDao.latestOnOrBefore(
            today.toString(), location.cacheKey, location.country, settings.method, settings.school
        )?.toDomain()
    }

    override suspend fun readCache(
        date: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): CachedPrayerDay? = storageOperation {
        prayerDayDao.find(
            date.toString(), location.cacheKey, location.country, settings.method, settings.school
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
            val expectedDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
            prayerDayDao.countBetween(
                startDate.toString(), endDate.toString(), location.cacheKey, location.country,
                settings.method, settings.school
            ) == expectedDays
        }
    }

    suspend fun readCacheDateBounds(location: PrayerLocation): PrayerCacheDateBounds? = storageOperation {
        val bounds = prayerDayDao.findDateBounds(
            location.cacheKey,
            location.country,
            settings.method,
            settings.school
        )
        val firstDate = bounds.firstDate?.let(LocalDate::parse) ?: return@storageOperation null
        val lastDate = bounds.lastDate?.let(LocalDate::parse) ?: return@storageOperation null
        PrayerCacheDateBounds(firstDate, lastDate)
    }

    override suspend fun readCaches(
        startDate: LocalDate,
        endDate: LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): List<CachedPrayerDay> = storageOperation {
        prayerDayDao.findBetween(
            startDate.toString(), endDate.toString(), location.cacheKey, location.country,
            settings.method, settings.school
        ).map(PrayerDayEntity::toDomain)
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

    private suspend fun ensurePrayerDataVersion() {
        if (dataVersionCheckComplete) return
        dataVersionMutex.withLock {
            if (dataVersionCheckComplete) return@withLock
            val storedVersion = dataStore.data.first()[prayerDataVersionKey]
            dataVersionGate.ensureCurrent(storedVersion) { currentVersion ->
                prayerDayDao.clearAll()
                dataStore.edit { prefs ->
                    prefs.remove(cacheKey)
                    prefs.remove(legacyWidgetTextKey)
                    prefs.remove(legacyWidgetDateKey)
                    prefs.remove(legacyWidgetHijriKey)
                    prefs[prayerDataVersionKey] = currentVersion
                }
            }
            dataVersionCheckComplete = true
        }
    }

    private suspend fun ensureDistrictAlAdhanCacheCleanup() {
        if (districtCacheCleanupComplete) return
        districtCacheCleanupMutex.withLock {
            if (districtCacheCleanupComplete) return@withLock
            val alreadyCleaned = dataStore.data.first()[districtCacheCleanupKey] == true
            if (!alreadyCleaned) {
                prayerDayDao.deleteDistrictAlAdhanCaches()
                dataStore.edit { preferences -> preferences[districtCacheCleanupKey] = true }
            }
            districtCacheCleanupComplete = true
        }
    }

    private suspend fun <T> storageOperation(block: suspend () -> T): T = try {
        ensurePrayerDataVersion()
        ensureDistrictAlAdhanCacheCleanup()
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
