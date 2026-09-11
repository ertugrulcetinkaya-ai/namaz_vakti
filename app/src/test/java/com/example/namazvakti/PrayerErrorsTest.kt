package com.example.namazvakti

import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.PrayerStorageException
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.example.namazvakti.domain.model.StorageFailureKind
import com.example.namazvakti.domain.model.classifyPrayerError
import com.example.namazvakti.domain.model.code
import com.example.namazvakti.domain.model.isRetryable
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
        assertFalse(PrayerError.PermanentStorage.isRetryable)
        assertFalse(PrayerError.Unknown.isRetryable)
    }

    @Test
    fun storageFailureClassificationSeparatesTransientAndPermanentFailures() {
        val transient = classifyPrayerError(
            PrayerStorageException(StorageFailureKind.TRANSIENT, "database locked")
        )
        val permanent = classifyPrayerError(
            PrayerStorageException(StorageFailureKind.PERMANENT, "database corrupt")
        )
        val unknown = classifyPrayerError(
            PrayerStorageException(StorageFailureKind.UNKNOWN, "unexpected storage error")
        )

        assertEquals(PrayerError.TransientStorage, transient)
        assertTrue(transient.isRetryable)
        assertEquals(PrayerError.PermanentStorage, permanent)
        assertFalse(permanent.isRetryable)
        assertEquals(PrayerError.Storage, unknown)
        assertFalse(unknown.isRetryable)
    }
}
