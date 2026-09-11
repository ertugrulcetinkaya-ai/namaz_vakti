package com.example.namazvakti

import com.example.namazvakti.widget.worker.PrayerWidgetWorker
import com.example.namazvakti.widget.worker.PrayerWorkRequestFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.work.NetworkType
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
        assertEquals(NetworkType.CONNECTED, immediate.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.SECONDS.toMillis(30), immediate.workSpec.backoffDelayDuration)
    }

    @Test
    fun dailySafetyRefreshUsesTheSameBoundedRetryBackoff() {
        val periodic = factory.dailySafetyRefresh()

        assertEquals(NetworkType.CONNECTED, periodic.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.SECONDS.toMillis(30), periodic.workSpec.backoffDelayDuration)
    }
}
