package com.example.namazvakti.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.PrayerTimeOffsets
import kotlinx.coroutines.flow.first

private val Context.prayerTimeOffsetsDataStore by preferencesDataStore(name = "prayer_time_offsets")

interface PrayerTimeOffsetsSource {
    suspend fun read(): PrayerTimeOffsets
    suspend fun save(offsets: PrayerTimeOffsets)
}

class PrayerTimeOffsetsStore(
    private val dataStore: DataStore<Preferences>
) : PrayerTimeOffsetsSource {
    constructor(context: Context) : this(context.applicationContext.prayerTimeOffsetsDataStore)

    private val keys = PrayerTimeName.entries.associateWith { prayer ->
        intPreferencesKey("prayer_offset_${prayer.storageKey}")
    }

    override suspend fun read(): PrayerTimeOffsets {
        val preferences = dataStore.data.first()
        return PrayerTimeOffsets().let { defaults ->
            PrayerTimeName.entries.fold(defaults) { offsets, prayer ->
                val value = preferences[keys.getValue(prayer)]
                    ?.takeIf { it in PrayerTimeOffsets.MIN_OFFSET_MINUTES..PrayerTimeOffsets.MAX_OFFSET_MINUTES }
                    ?: 0
                offsets.withOffset(prayer, value)
            }
        }
    }

    override suspend fun save(offsets: PrayerTimeOffsets) {
        dataStore.edit { preferences ->
            PrayerTimeName.entries.forEach { prayer ->
                preferences[keys.getValue(prayer)] = offsets.minutesFor(prayer)
            }
        }
    }
}
