package com.example.namazvakti

import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.policy.PrayerRetryPolicy
import com.example.namazvakti.domain.policy.PrayerWorkDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerRetryPolicyTest {
    private val policy = PrayerRetryPolicy()

    @Test
    fun retryableErrorsRetryUntilTheThirdAttempt() {
        assertEquals(PrayerWorkDecision.Retry, policy.decide(PrayerError.Network, 0))
        assertEquals(PrayerWorkDecision.Retry, policy.decide(PrayerError.Network, 1))
        assertEquals(PrayerWorkDecision.Failure, policy.decide(PrayerError.Network, 2))
    }

    @Test
    fun permanentErrorsNeverRetry() {
        assertEquals(
            PrayerWorkDecision.Failure,
            policy.decide(PrayerError.Service(400), 0)
        )
        assertEquals(
            PrayerWorkDecision.Failure,
            policy.decide(PrayerError.InvalidData, 0)
        )
        assertEquals(
            PrayerWorkDecision.Failure,
            policy.decide(PrayerError.Storage, 0)
        )
    }

    @Test
    fun transientStorageErrorsRetryAtMostOnce() {
        assertEquals(
            PrayerWorkDecision.Retry,
            policy.decide(PrayerError.TransientStorage, runAttemptCount = 0)
        )
        assertEquals(
            PrayerWorkDecision.Failure,
            policy.decide(PrayerError.TransientStorage, runAttemptCount = 1)
        )
    }
}
