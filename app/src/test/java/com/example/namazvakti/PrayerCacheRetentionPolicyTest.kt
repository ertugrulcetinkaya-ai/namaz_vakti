package com.example.namazvakti

import com.example.namazvakti.domain.policy.PrayerCacheRetentionPolicy
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerCacheRetentionPolicyTest {
    private val today = LocalDate.of(2026, 9, 11)

    @Test
    fun defaultWindowKeepsNinetyPastDaysAndThirteenFutureMonths() {
        val window = PrayerCacheRetentionPolicy().window(today)

        assertEquals(LocalDate.of(2026, 6, 13), window.minimumDate)
        assertEquals(LocalDate.of(2027, 10, 11), window.maximumDate)
        assertTrue(ChronoUnit.DAYS.between(window.minimumDate, window.maximumDate) + 1 <= 500)
        assertTrue(LocalDate.of(2027, 2, 8) in window.minimumDate..window.maximumDate)
    }

    @Test
    fun negativeRetentionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerCacheRetentionPolicy(pastDays = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrayerCacheRetentionPolicy(futureMonths = -1)
        }
    }
}
