package com.example.namazvakti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

class PrayerWorkRequestFactoryTest {
    private val factory = PrayerWorkRequestFactory()

    @Test
    fun dailySafetyRefreshIs24HoursAndFetches() {
        val request = factory.dailySafetyRefresh()

        assertEquals(TimeUnit.HOURS.toMillis(24), request.workSpec.intervalDuration)
        assertTrue(request.workSpec.input.getBoolean(PrayerWidgetWorker.INPUT_FETCH, false))
    }

    @Test
    fun immediateRefreshFetchesAndBoundaryRerenderDoesNot() {
        val immediate = factory.immediateRefresh()
        val boundary = factory.boundaryRerender(Duration.ofMinutes(3))

        assertTrue(immediate.workSpec.input.getBoolean(PrayerWidgetWorker.INPUT_FETCH, false))
        assertFalse(boundary.workSpec.input.getBoolean(PrayerWidgetWorker.INPUT_FETCH, true))
        assertEquals(Duration.ofMinutes(3).toMillis(), boundary.workSpec.initialDelay)
    }
}
