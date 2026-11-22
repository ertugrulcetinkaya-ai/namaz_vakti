package com.example.namazvakti.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AutoRefreshPolicyTest {
    private val now = Instant.parse("2026-11-22T06:00:00Z")
    private val date = LocalDate.of(2026, 11, 22)

    @Test
    fun missingTodayAndMissingTomorrowAreIndependentReasons() {
        val policy = AutoRefreshPolicy()
        val missingToday = AutoRefreshKey(date, "Ankara", Reason.MISSING_TODAY)
        val missingTomorrow = AutoRefreshKey(date, "Ankara", Reason.MISSING_TOMORROW)

        assertTrue(policy.shouldRefresh(missingToday, now, lastAttemptFailed = false))
        assertTrue(policy.shouldRefresh(missingTomorrow, now, lastAttemptFailed = false))
    }

    @Test
    fun failedAttemptsRetryAfterOneFiveAndFifteenMinutesAtMostThreeTimes() {
        val policy = AutoRefreshPolicy()
        val key = AutoRefreshKey(date, "Ankara", Reason.MISSING_TODAY)

        assertTrue(policy.shouldRefresh(key, now, lastAttemptFailed = false))
        assertFalse(policy.shouldRefresh(key, now.plusSeconds(59), lastAttemptFailed = true))
        assertTrue(policy.shouldRefresh(key, now.plusSeconds(60), lastAttemptFailed = true))
        assertFalse(policy.shouldRefresh(key, now.plusSeconds(60 + 5 * 60 - 1), lastAttemptFailed = true))
        assertTrue(policy.shouldRefresh(key, now.plusSeconds(60 + 5 * 60), lastAttemptFailed = true))
        assertFalse(policy.shouldRefresh(key, now.plusSeconds(60 + 5 * 60 + 15 * 60 - 1), lastAttemptFailed = true))
        assertTrue(policy.shouldRefresh(key, now.plusSeconds(60 + 5 * 60 + 15 * 60), lastAttemptFailed = true))
        assertFalse(policy.shouldRefresh(key, now.plusSeconds(60 + 5 * 60 + 30 * 60), lastAttemptFailed = true))
    }

    @Test
    fun differentLocationKeyStartsANewAttemptSequence() {
        val policy = AutoRefreshPolicy()
        val ankara = AutoRefreshKey(date, "Ankara", Reason.MISSING_TODAY)
        val istanbul = AutoRefreshKey(date, "Istanbul", Reason.MISSING_TODAY)

        assertTrue(policy.shouldRefresh(ankara, now, lastAttemptFailed = false))
        assertTrue(policy.shouldRefresh(istanbul, now, lastAttemptFailed = false))
    }

    @Test
    fun successfulAttemptIsNotRepeatedWhileDataIsStillMissing() {
        val policy = AutoRefreshPolicy()
        val key = AutoRefreshKey(date, "Ankara", Reason.MISSING_TODAY)

        assertTrue(policy.shouldRefresh(key, now, lastAttemptFailed = false))
        assertFalse(policy.shouldRefresh(key, now.plusSeconds(10), lastAttemptFailed = false))
        assertFalse(policy.shouldRefresh(key, now.plusSeconds(60 * 60), lastAttemptFailed = false))
    }
}
