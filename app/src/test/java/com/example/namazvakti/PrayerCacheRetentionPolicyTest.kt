package com.example.namazvakti

import com.example.namazvakti.domain.policy.PrayerCacheRetentionPolicy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PrayerCacheRetentionPolicyTest {
    private val today = LocalDate.of(2026, 9, 11)

    @Test
    fun defaultWindowKeepsNinetyPastAndSixtyFutureDays() {
        val window = PrayerCacheRetentionPolicy().window(today)

        assertEquals(LocalDate.of(2026, 6, 13), window.minimumDate)
        assertEquals(LocalDate.of(2026, 11, 10), window.maximumDate)
    }

    @Test
    fun negativeRetentionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerCacheRetentionPolicy(pastDays = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrayerCacheRetentionPolicy(futureDays = -1)
        }
    }
}
