package com.example.namazvakti

import com.example.namazvakti.app.*
import com.example.namazvakti.data.local.*
import com.example.namazvakti.data.remote.*
import com.example.namazvakti.data.repository.*
import com.example.namazvakti.domain.model.*
import com.example.namazvakti.domain.policy.*
import com.example.namazvakti.domain.port.*
import com.example.namazvakti.ui.main.*
import com.example.namazvakti.widget.*
import com.example.namazvakti.widget.alarm.*
import com.example.namazvakti.widget.renderer.*
import com.example.namazvakti.widget.worker.*
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
