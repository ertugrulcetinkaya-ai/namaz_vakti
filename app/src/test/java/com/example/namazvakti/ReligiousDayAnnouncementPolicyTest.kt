package com.example.namazvakti

import com.example.namazvakti.domain.policy.ReligiousDayAnnouncementPolicy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReligiousDayAnnouncementPolicyTest {
    @Test
    fun todayNightAndDayUseCorrectWording() {
        assertEquals(
            "Bu akşam Berat Kandili",
            ReligiousDayAnnouncementPolicy.select(LocalDate.of(2026, 2, 2))?.text
        )
        assertEquals(
            "Bugün Ramazan Bayramı (1. gün)",
            ReligiousDayAnnouncementPolicy.select(LocalDate.of(2025, 3, 30))?.text
        )
    }

    @Test
    fun tomorrowAndSevenDayWindowUseCorrectWording() {
        assertEquals(
            "Yarın akşam Regaip Kandili",
            ReligiousDayAnnouncementPolicy.select(LocalDate.of(2026, 12, 9))?.text
        )
        assertEquals(
            "7 gün sonra akşam: Regaip Kandili",
            ReligiousDayAnnouncementPolicy.select(LocalDate.of(2026, 12, 3))?.text
        )
        assertEquals(
            "3 gün sonra: Arefe",
            ReligiousDayAnnouncementPolicy.select(LocalDate.of(2025, 6, 2))?.text
        )
    }

    @Test
    fun nearestNightTakesPriorityWhenMultipleDaysShareDate() {
        val announcement = ReligiousDayAnnouncementPolicy.select(LocalDate.of(2026, 12, 10))

        assertEquals("Bu akşam Regaip Kandili", announcement?.text)
    }

    @Test
    fun noEventTodayOrWithinSevenDaysReturnsNull() {
        assertNull(ReligiousDayAnnouncementPolicy.select(LocalDate.of(2026, 11, 5)))
    }
}
