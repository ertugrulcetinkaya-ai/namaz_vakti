package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.isRetryable

enum class PrayerWorkDecision {
    Retry,
    Failure
}

class PrayerRetryPolicy(private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS) {
    init {
        require(maxAttempts > 0) { "Maximum attempts must be positive" }
    }

    fun decide(error: PrayerError, runAttemptCount: Int): PrayerWorkDecision {
        require(runAttemptCount >= 0) { "Run attempt count must not be negative" }
        return if (error.isRetryable && runAttemptCount < maxAttempts - 1) {
            PrayerWorkDecision.Retry
        } else {
            PrayerWorkDecision.Failure
        }
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
    }
}
