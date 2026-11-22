package com.example.namazvakti.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.diyanetSourceDataStore by preferencesDataStore(name = "diyanet_source_store")

data class CachedDiyanetManifest(
    val body: String,
    val fetchedAtEpochMillis: Long
)

enum class DiyanetDataSourceName {
    MIRROR,
    MTOPAL
}

data class DiyanetSourceHealth(
    val source: DiyanetDataSourceName,
    val succeededAtEpochMillis: Long
)

interface DiyanetSourceStateStore {
    suspend fun readCachedManifest(): CachedDiyanetManifest?
    suspend fun cacheManifest(manifest: CachedDiyanetManifest)
    suspend fun recordSuccessfulSource(health: DiyanetSourceHealth)
}

class DataStoreDiyanetSourceStateStore(
    private val dataStore: DataStore<Preferences>
) : DiyanetSourceStateStore {
    constructor(context: Context) : this(context.applicationContext.diyanetSourceDataStore)

    private val manifestBodyKey = stringPreferencesKey("diyanet_manifest_body")
    private val manifestFetchedAtKey = longPreferencesKey("diyanet_manifest_fetched_at")
    private val successfulSourceKey = stringPreferencesKey("diyanet_successful_source")
    private val successfulAtKey = longPreferencesKey("diyanet_successful_at")

    override suspend fun readCachedManifest(): CachedDiyanetManifest? {
        val preferences = dataStore.data.first()
        val body = preferences[manifestBodyKey] ?: return null
        val fetchedAt = preferences[manifestFetchedAtKey] ?: return null
        return CachedDiyanetManifest(body, fetchedAt)
    }

    override suspend fun cacheManifest(manifest: CachedDiyanetManifest) {
        dataStore.edit { preferences ->
            preferences[manifestBodyKey] = manifest.body
            preferences[manifestFetchedAtKey] = manifest.fetchedAtEpochMillis
        }
    }

    override suspend fun recordSuccessfulSource(health: DiyanetSourceHealth) {
        dataStore.edit { preferences ->
            preferences[successfulSourceKey] = health.source.name
            preferences[successfulAtKey] = health.succeededAtEpochMillis
        }
    }

    suspend fun readSourceHealth(): DiyanetSourceHealth? {
        val preferences = dataStore.data.first()
        val source = preferences[successfulSourceKey]
            ?.let { value -> runCatching { DiyanetDataSourceName.valueOf(value) }.getOrNull() }
            ?: return null
        val succeededAt = preferences[successfulAtKey] ?: return null
        return DiyanetSourceHealth(source, succeededAt)
    }
}
