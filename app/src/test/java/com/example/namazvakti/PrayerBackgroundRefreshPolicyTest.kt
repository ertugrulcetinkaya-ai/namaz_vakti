package com.example.namazvakti

import com.example.namazvakti.domain.policy.needsBackgroundRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerBackgroundRefreshPolicyTest {
    @Test
    fun backgroundRefreshRunsForWidgetsOrNotifications() {
        assertFalse(needsBackgroundRefresh(hasWidgets = false, notificationsEnabled = false))
        assertTrue(needsBackgroundRefresh(hasWidgets = true, notificationsEnabled = false))
        assertTrue(needsBackgroundRefresh(hasWidgets = false, notificationsEnabled = true))
        assertTrue(needsBackgroundRefresh(hasWidgets = true, notificationsEnabled = true))
    }
}
