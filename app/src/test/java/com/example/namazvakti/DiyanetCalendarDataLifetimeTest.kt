package com.example.namazvakti

import com.example.namazvakti.domain.model.DiyanetHijriCalendar
import com.example.namazvakti.domain.model.DiyanetRamadanCalendar
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class DiyanetCalendarDataLifetimeTest {
    @Test
    fun everyCalendarTableCoversAtLeastTwelveMonthsFromToday() {
        val requiredThroughDate = LocalDate.now().plusMonths(12)

        assertCoverage(
            tableName = "Dini günler",
            lastCoveredDate = DiyanetReligiousDays.lastCoveredDate(),
            requiredThroughDate = requiredThroughDate
        )
        assertCoverage(
            tableName = "Hicri takvim",
            lastCoveredDate = DiyanetHijriCalendar.lastCoveredDate(),
            requiredThroughDate = requiredThroughDate
        )
        assertCoverage(
            tableName = "Ramazan",
            lastCoveredDate = DiyanetRamadanCalendar.lastCoveredDate(),
            requiredThroughDate = requiredThroughDate
        )
    }

    private fun assertCoverage(tableName: String, lastCoveredDate: LocalDate, requiredThroughDate: LocalDate) {
        assertTrue(
            "$tableName tablosu $lastCoveredDate tarihinde bitiyor; en az $requiredThroughDate tarihine kadar kapsamalı",
            !lastCoveredDate.isBefore(requiredThroughDate)
        )
    }
}
