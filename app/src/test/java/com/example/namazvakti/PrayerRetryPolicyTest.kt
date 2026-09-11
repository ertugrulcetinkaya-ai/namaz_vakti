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
}
