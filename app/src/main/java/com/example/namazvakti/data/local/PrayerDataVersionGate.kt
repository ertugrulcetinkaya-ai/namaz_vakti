package com.example.namazvakti.data.local

internal class PrayerDataVersionGate(private val currentVersion: Int) {
    suspend fun ensureCurrent(
        storedVersion: Int?,
        invalidateAndStoreVersion: suspend (Int) -> Unit
    ): Boolean {
        if (storedVersion == currentVersion) return false
        invalidateAndStoreVersion(currentVersion)
        return true
    }

    companion object {
        const val CURRENT_VERSION = 2
    }
}
