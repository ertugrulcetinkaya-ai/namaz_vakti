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
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerErrorsTest {
    @Test
    fun networkFailuresAreRetryable() {
        val error = classifyPrayerError(IOException("timeout"))

        assertEquals(PrayerError.Network, error)
        assertTrue(error.isRetryable)
        assertEquals("network", error.code)
    }

    @Test
    fun serverAndRateLimitFailuresAreRetryable() {
        assertTrue(PrayerError.Service(429).isRetryable)
        assertTrue(PrayerError.Service(503).isRetryable)
    }

    @Test
    fun clientFailuresArePermanent() {
        val error = classifyPrayerError(PrayerTimesApiException("HTTP 400", statusCode = 400))

        assertEquals(PrayerError.Service(400), error)
        assertFalse(error.isRetryable)
    }

    @Test
    fun malformedApiDataIsPermanentInvalidData() {
        val error = classifyPrayerError(PrayerTimesApiException("Invalid API response"))

        assertEquals(PrayerError.InvalidData, error)
        assertFalse(error.isRetryable)
    }

    @Test
    fun storageAndUnknownFailuresArePermanent() {
        assertFalse(PrayerError.Storage.isRetryable)
        assertFalse(PrayerError.Unknown.isRetryable)
    }
}
