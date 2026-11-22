package com.example.namazvakti

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.namazvakti.data.local.PrayerNotificationSettingsStore
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.domain.policy.PrayerNotificationEventId
import java.time.LocalDate
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

    @Test
    fun keepsTheLastShownDateForEachPrayerAndTypeAcrossStoreInstances() = runBlocking {
        val preferences = FakePreferencesDataStore()
        val store = PrayerNotificationSettingsStore(preferences)
        val atTime = PrayerNotificationEventId(
            PrayerNotificationPrayer.DHUHR, LocalDate.of(2026, 8, 6), PrayerNotificationType.AT_TIME
        )
        val reminder = atTime.copy(type = PrayerNotificationType.REMINDER)
        val anotherPrayer = atTime.copy(prayer = PrayerNotificationPrayer.ASR)
        val nextDay = atTime.copy(date = atTime.date.plusDays(1))

        assertEquals(emptySet<PrayerNotificationEventId>(), store.readShownEvents())
        store.recordShownEvent(atTime)
        store.recordShownEvent(reminder)
        store.recordShownEvent(anotherPrayer)
        store.recordShownEvent(nextDay)
        store.save(PrayerNotificationSettings(notificationsEnabled = true))

        assertEquals(
            setOf(reminder, anotherPrayer, nextDay),
            PrayerNotificationSettingsStore(preferences).readShownEvents()
        )
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
