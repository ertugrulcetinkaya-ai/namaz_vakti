package com.example.namazvakti

import com.example.namazvakti.domain.model.DiyanetRamadanCalendar
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiyanetRamadanCalendarTest {
    @Test
    fun official1448DatesDetermineTwentyNineDaysAndEid() {
        val range = requireNotNull(DiyanetRamadanCalendar.find(1448)).toRange()

        assertEquals(LocalDate.of(2027, 2, 8), range.startDate)
        assertEquals(LocalDate.of(2027, 3, 8), range.endDate)
        assertEquals(LocalDate.of(2027, 3, 9), range.eidDate)
        assertEquals(29, range.dayCount)
        assertEquals(1, range.dayNumber(range.startDate))
        assertEquals(29, range.dayNumber(range.endDate))
        assertNull(range.dayNumber(range.eidDate))
        assertFalse(range.estimated)
    }

    @Test
    fun twentySeventhNightBeginsOnEveningOfRamadanDayTwentySix() {
        val range = requireNotNull(DiyanetRamadanCalendar.find(1448)).toRange()

        assertEquals(LocalDate.of(2027, 3, 5), range.qadrNightEveningDate)
        assertEquals(26, range.dayNumber(range.qadrNightEveningDate))
    }

    @Test
    fun officialRecordsOnlyContainYearsVerifiedAgainstPublishedDiyanetCalendars() {
        assertEquals((1447..1457).toSet(), DiyanetRamadanCalendar.entries.keys)
        assertTrue(DiyanetRamadanCalendar.find(1458) == null)
    }
}
