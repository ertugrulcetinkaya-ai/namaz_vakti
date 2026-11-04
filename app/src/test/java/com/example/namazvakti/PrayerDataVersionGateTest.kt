package com.example.namazvakti

import com.example.namazvakti.data.local.PrayerDataVersionGate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerDataVersionGateTest {
    @Test
    fun olderVersionClearsPrayerCachesOnceAndPreservesSelectedLocation() = runTest {
        var version: Int? = 3
        val roomDays = mutableListOf("old room day")
        val legacyCaches = mutableListOf("old datastore cache")
        val prayerPreferences = mutableMapOf(
            "selected_city" to "Ankara",
            "selected_country" to "Turkey"
        )
        val notificationPreferences = mutableMapOf(
            "notifications_enabled" to true,
            "lead_minutes" to 10
        )
        val gate = PrayerDataVersionGate(PrayerDataVersionGate.CURRENT_VERSION)
        var cleanupCount = 0

        suspend fun ensureCurrent() = gate.ensureCurrent(version) { currentVersion ->
            cleanupCount++
            roomDays.clear()
            legacyCaches.clear()
            version = currentVersion
        }

        assertTrue(ensureCurrent())
        assertTrue(roomDays.isEmpty())
        assertTrue(legacyCaches.isEmpty())
        assertEquals(PrayerDataVersionGate.CURRENT_VERSION, version)
        assertEquals("Ankara", prayerPreferences["selected_city"])
        assertEquals("Turkey", prayerPreferences["selected_country"])
        assertEquals(true, notificationPreferences["notifications_enabled"])
        assertEquals(10, notificationPreferences["lead_minutes"])

        assertFalse(ensureCurrent())
        assertEquals(1, cleanupCount)
        assertEquals("Ankara", prayerPreferences["selected_city"])
        assertEquals("Turkey", prayerPreferences["selected_country"])
        assertEquals(true, notificationPreferences["notifications_enabled"])
        assertEquals(10, notificationPreferences["lead_minutes"])
    }
}
