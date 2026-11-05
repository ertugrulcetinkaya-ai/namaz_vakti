package com.example.namazvakti

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.namazvakti.data.local.PrayerNotificationSettingsStore
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerNotificationSettingsStoreTest {
    @Test
    fun defaultsToDisabledWithFivePrayersAndNoReminder() = runBlocking {
        val store = PrayerNotificationSettingsStore(FakePreferencesDataStore())

        assertEquals(PrayerNotificationSettings(), store.read())
        assertEquals(false, store.read().notificationsEnabled)
        assertEquals(false, store.read().religiousDaysEnabled)
        assertEquals(0, store.read().leadMinutes)
        assertEquals(5, store.read().enabledPrayers.size)
        assertEquals(false, PrayerNotificationPrayer.SUNRISE in store.read().enabledPrayers)
    }

    @Test
    fun persistsEnabledPrayersAndLeadTime() = runBlocking {
        val preferences = FakePreferencesDataStore()
        val store = PrayerNotificationSettingsStore(preferences)
        val expected = PrayerNotificationSettings(
            notificationsEnabled = true,
            enabledPrayers = setOf(PrayerNotificationPrayer.SUNRISE, PrayerNotificationPrayer.ISHA),
            leadMinutes = 15,
            religiousDaysEnabled = true
        )

        store.save(expected)

        assertEquals(expected, store.read())
        assertEquals(expected, store.settings.first())
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
