package com.example.namazvakti.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationSettingsDataStore by preferencesDataStore(
    name = "prayer_notification_settings"
)

interface PrayerNotificationSettingsSource {
    val settings: Flow<PrayerNotificationSettings>
    suspend fun read(): PrayerNotificationSettings
    suspend fun save(settings: PrayerNotificationSettings)
}

class PrayerNotificationSettingsStore(
    private val dataStore: DataStore<Preferences>
) : PrayerNotificationSettingsSource {
    constructor(context: Context) : this(context.applicationContext.notificationSettingsDataStore)

    private val enabledKey = booleanPreferencesKey("notifications_enabled")
    private val prayerMaskKey = intPreferencesKey("enabled_prayers_mask")
    private val leadMinutesKey = intPreferencesKey("lead_minutes")

    override val settings: Flow<PrayerNotificationSettings> = dataStore.data.map { prefs ->
        decode(prefs[enabledKey], prefs[prayerMaskKey], prefs[leadMinutesKey])
    }

    override suspend fun read(): PrayerNotificationSettings = settings.first()

    override suspend fun save(settings: PrayerNotificationSettings) {
        dataStore.edit { prefs ->
            prefs[enabledKey] = settings.notificationsEnabled
            prefs[prayerMaskKey] = settings.enabledPrayers.fold(0) { mask, prayer ->
                mask or (1 shl prayer.ordinal)
            }
            prefs[leadMinutesKey] = settings.leadMinutes
        }
    }

    private fun decode(enabled: Boolean?, prayerMask: Int?, leadMinutes: Int?): PrayerNotificationSettings {
        val mask = prayerMask ?: defaultPrayerMask()
        val prayers = PrayerNotificationPrayer.entries
            .filterTo(mutableSetOf()) { mask and (1 shl it.ordinal) != 0 }
        return PrayerNotificationSettings(
            notificationsEnabled = enabled ?: false,
            enabledPrayers = prayers,
            leadMinutes = leadMinutes?.takeIf {
                it in PrayerNotificationSettings.ALLOWED_LEAD_MINUTES
            } ?: PrayerNotificationSettings.DEFAULT_LEAD_MINUTES
        )
    }

    private fun defaultPrayerMask(): Int = PrayerNotificationSettings.DEFAULT_ENABLED_PRAYERS
        .fold(0) { mask, prayer -> mask or (1 shl prayer.ordinal) }
}
