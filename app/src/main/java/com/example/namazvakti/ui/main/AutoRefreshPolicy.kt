package com.example.namazvakti.ui.main

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

internal enum class Reason {
    MISSING_TODAY,
    MISSING_TOMORROW
}

internal data class AutoRefreshKey(
    val date: LocalDate,
    val locationKey: String,
    val reason: Reason
)

internal class AutoRefreshPolicy {
    private data class Attempt(
        val count: Int,
        val lastAttemptAt: Instant,
        val succeeded: Boolean = false
    )

    private val attempts = mutableMapOf<AutoRefreshKey, Attempt>()

    fun shouldRefresh(key: AutoRefreshKey, now: Instant, lastAttemptFailed: Boolean): Boolean {
        val previous = attempts[key]
        if (previous == null) {
            attempts[key] = Attempt(count = 1, lastAttemptAt = now)
            return true
        }
        if (previous.succeeded) return false
        if (!lastAttemptFailed) {
            attempts[key] = previous.copy(succeeded = true)
            return false
        }
        if (previous.count >= MAX_ATTEMPTS) return false

        val backoff = RETRY_BACKOFFS[previous.count - 1]
        if (now < previous.lastAttemptAt.plus(backoff)) return false

        attempts[key] = Attempt(count = previous.count + 1, lastAttemptAt = now)
        return true
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
        val RETRY_BACKOFFS = listOf(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15)
        )
    }
}
