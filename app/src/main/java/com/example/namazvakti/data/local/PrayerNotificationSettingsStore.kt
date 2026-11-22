package com.example.namazvakti.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.domain.policy.PrayerNotificationEventId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
    private val enabledPrayerKeysKey = stringSetPreferencesKey("enabled_prayer_keys")
    private val leadMinutesKey = intPreferencesKey("lead_minutes")
    private val religiousDaysEnabledKey = booleanPreferencesKey("religious_days_enabled")

    override val settings: Flow<PrayerNotificationSettings> = flow {
        migrateLegacyEnabledPrayers()
        emitAll(dataStore.data.map { prefs ->
            decode(
                prefs[enabledKey],
                prefs[enabledPrayerKeysKey],
                prefs[prayerMaskKey],
                prefs[leadMinutesKey],
                prefs[religiousDaysEnabledKey]
            )
        })
    }

    override suspend fun read(): PrayerNotificationSettings = settings.first()

    override suspend fun save(settings: PrayerNotificationSettings) {
        dataStore.edit { prefs ->
            prefs[enabledKey] = settings.notificationsEnabled
            prefs[enabledPrayerKeysKey] = settings.enabledPrayers
                .mapTo(mutableSetOf()) { it.storageKey }
            prefs.remove(prayerMaskKey)
            prefs[leadMinutesKey] = settings.leadMinutes
            prefs[religiousDaysEnabledKey] = settings.religiousDaysEnabled
        }
    }

    suspend fun readShownEvents(): Set<PrayerNotificationEventId> {
        val preferences = dataStore.data.first()
        return buildSet {
            for (prayer in PrayerNotificationPrayer.entries) {
                for (type in PrayerNotificationType.entries) {
                    val date = preferences[shownDateKey(prayer, type)] ?: continue
                    add(PrayerNotificationEventId(prayer, LocalDate.parse(date), type))
                }
            }
        }
    }

    suspend fun recordShownEvent(event: PrayerNotificationEventId) {
        dataStore.edit { prefs ->
            prefs[shownDateKey(event.prayer, event.type)] = event.date.toString()
        }
    }

    private fun shownDateKey(prayer: PrayerNotificationPrayer, type: PrayerNotificationType) =
        stringPreferencesKey("last_shown_${prayer.name}_${type.name}")

    private suspend fun migrateLegacyEnabledPrayers() {
        if (dataStore.data.first()[enabledPrayerKeysKey] != null) return

        dataStore.edit { prefs ->
            if (prefs[enabledPrayerKeysKey] != null) return@edit
            val prayers = decodePrayers(enabledPrayerKeys = null, prayerMask = prefs[prayerMaskKey])
            prefs[enabledPrayerKeysKey] = prayers.mapTo(mutableSetOf()) { it.storageKey }
            prefs.remove(prayerMaskKey)
        }
    }

    private fun decode(
        enabled: Boolean?,
        enabledPrayerKeys: Set<String>?,
        prayerMask: Int?,
        leadMinutes: Int?,
        religiousDaysEnabled: Boolean?
    ): PrayerNotificationSettings {
        return PrayerNotificationSettings(
            notificationsEnabled = enabled ?: false,
            enabledPrayers = decodePrayers(enabledPrayerKeys, prayerMask),
            leadMinutes = leadMinutes?.takeIf {
                it in PrayerNotificationSettings.ALLOWED_LEAD_MINUTES
            } ?: PrayerNotificationSettings.DEFAULT_LEAD_MINUTES,
            religiousDaysEnabled = religiousDaysEnabled ?: false
        )
    }

    private fun decodePrayers(
        enabledPrayerKeys: Set<String>?,
        prayerMask: Int?
    ): Set<PrayerNotificationPrayer> = when {
        enabledPrayerKeys != null -> PrayerNotificationPrayer.entries
            .filterTo(mutableSetOf()) { it.storageKey in enabledPrayerKeys }
        prayerMask != null -> PrayerNotificationPrayer.entries
            .filterTo(mutableSetOf()) { prayerMask and (1 shl it.ordinal) != 0 }
        else -> PrayerNotificationSettings.DEFAULT_ENABLED_PRAYERS
    }
}
