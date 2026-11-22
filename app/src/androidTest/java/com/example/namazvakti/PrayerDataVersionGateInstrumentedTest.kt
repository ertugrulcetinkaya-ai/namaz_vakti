package com.example.namazvakti

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.namazvakti.data.local.PrayerTimesDatabase
import com.example.namazvakti.data.local.PrayerTimesStore
import com.example.namazvakti.data.local.toEntity
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerDataVersionGateInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: PrayerTimesDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PrayerTimesDatabase::class.java).build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreFile = File(context.cacheDir, "prayer-version-${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile }
        )
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun upgradeFromVersionThreeClearsRoomOnceAndStoresVersionFour() = runBlocking {
        val date = LocalDate.of(2026, 8, 23)
        val location = PrayerLocation("Ankara", "Turkey", "ANKARA")
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("prayer_data_version")] = 3
            preferences[stringPreferencesKey("selected_city")] = location.city
            preferences[stringPreferencesKey("selected_country")] = location.country
        }
        val dao = database.prayerDayDao()
        dao.upsertAll(listOf(cache(date, location).toEntity()))

        val firstOpen = newStore()
        assertNull(firstOpen.readCache())
        assertNull(dao.find(date.toString(), location.cacheKey, location.country, 13, 0))
        assertEquals(4, dataStore.data.first()[intPreferencesKey("prayer_data_version")])

        val rowAfterUpgrade = cache(date, location).copy(
            prayerTimes = PrayerTimes(
                LocalTime.of(4, 30), LocalTime.of(6, 2), LocalTime.of(12, 57),
                LocalTime.of(16, 42), LocalTime.of(19, 43), LocalTime.of(21, 8)
            )
        )
        dao.upsertAll(listOf(rowAfterUpgrade.toEntity()))

        val secondOpen = newStore()
        assertNotNull(secondOpen.readCache())
        assertNotNull(dao.find(date.toString(), location.cacheKey, location.country, 13, 0))
        assertEquals(4, dataStore.data.first()[intPreferencesKey("prayer_data_version")])
    }

    private fun newStore() = PrayerTimesStore(
        context = context,
        prayerDayDao = database.prayerDayDao(),
        timeProvider = PrayerTimeProvider(
            Clock.fixed(Instant.parse("2026-08-23T08:00:00Z"), PrayerTimeProvider.DEFAULT_ZONE)
        ),
        settings = PrayerCalculationSettings(),
        suppliedDataStore = dataStore
    )

    private fun cache(date: LocalDate, location: PrayerLocation) = CachedPrayerDay(
        date = date,
        location = location,
        timezone = location.timezone,
        settings = PrayerCalculationSettings(),
        prayerTimes = PrayerTimes(
            LocalTime.of(4, 29), LocalTime.of(6, 1), LocalTime.of(12, 56),
            LocalTime.of(16, 41), LocalTime.of(19, 42), LocalTime.of(21, 7)
        ),
        hijriText = null,
        fetchedAt = Instant.parse("2026-08-23T08:00:00Z"),
        source = PrayerDataSource.ALADHAN
    )
}
