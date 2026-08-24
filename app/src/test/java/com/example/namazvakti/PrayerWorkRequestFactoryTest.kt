package com.example.namazvakti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun immediateRefreshFetches() {
        val immediate = factory.immediateRefresh()

        assertTrue(immediate.workSpec.input.getBoolean(PrayerWidgetWorker.INPUT_FETCH, false))
    }
}
