package com.example.namazvakti

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.namazvakti.data.local.PrayerTimeOffsetsStore
import com.example.namazvakti.domain.model.PrayerTimeOffsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerTimeOffsetsStoreTest {
    @Test
    fun usesStablePrayerKeysAndRoundTripsEveryOffset() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val store = PrayerTimeOffsetsStore(dataStore)
        val expected = PrayerTimeOffsets(-30, -5, 0, 1, 15, 30)

        store.save(expected)

        assertEquals(expected, store.read())
        val values = dataStore.data.first()
        assertEquals(-30, values[intPreferencesKey("prayer_offset_imsak")])
        assertEquals(-5, values[intPreferencesKey("prayer_offset_sunrise")])
        assertEquals(0, values[intPreferencesKey("prayer_offset_dhuhr")])
        assertEquals(1, values[intPreferencesKey("prayer_offset_asr")])
        assertEquals(15, values[intPreferencesKey("prayer_offset_maghrib")])
        assertEquals(30, values[intPreferencesKey("prayer_offset_isha")])
    }

    @Test
    fun defaultsMissingAndOutOfRangeValuesToZero() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("prayer_offset_imsak")] = 31
            preferences[intPreferencesKey("prayer_offset_dhuhr")] = -31
        }

        assertEquals(PrayerTimeOffsets.ZERO, PrayerTimeOffsetsStore(dataStore).read())
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock {
                transform(state.value).also { state.value = it }
            }
    }
}
