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
