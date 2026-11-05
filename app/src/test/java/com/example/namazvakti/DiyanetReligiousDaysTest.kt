package com.example.namazvakti

import com.example.namazvakti.domain.model.DiyanetHijriCalendar
import com.example.namazvakti.domain.model.DiyanetReligiousDay
import com.example.namazvakti.domain.model.DiyanetReligiousDayType
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import com.example.namazvakti.domain.model.DiyanetRamadanCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiyanetReligiousDaysTest {
    @Test
    fun allPublishedYearPagesAndNightKindsAreRepresented() {
        assertEquals((2025..2035).toSet(), DiyanetReligiousDays.entries.flatMap { it.dates }.map { it.year }.toSet())
        assertEquals(208, DiyanetReligiousDays.entries.size)
        DiyanetReligiousDays.entries.forEach { event ->
            assertEquals(event.type == DiyanetReligiousDayType.KANDIL, event.isNight)
        }
        assertTrue(DiyanetReligiousDays.forYear(2028).any { it.dates.size == 2 })
        assertTrue(DiyanetReligiousDays.forYear(2036).isEmpty())
    }

    @Test
    fun ramadanStartQadrAndFirstEidDaysMatchBothOfficialCalendars() {
        val ranges = DiyanetRamadanCalendar.entries.values
            .map { it.toRange() }
            .filter { it.startDate.year in DiyanetReligiousDays.FIRST_YEAR..DiyanetReligiousDays.LAST_YEAR }
        assertEquals(12, ranges.size)

        ranges.forEach { range ->
            assertEventAt(range.startDate) { it.name.contains("RAMAZAN BAŞLANGICI") }
            assertEventAt(range.qadrNightEveningDate) { it.name.contains("KADİR GECESİ") }
            assertEventAt(range.eidDate) {
                it.name.contains("RAMAZAN BAYRAMI") && it.name.contains("1. Gün")
            }
            assertEquals(1, DiyanetHijriCalendar.hijriDateOf(range.startDate)?.day)
            assertEquals(9, DiyanetHijriCalendar.hijriDateOf(range.startDate)?.month)
            assertEquals(26, DiyanetHijriCalendar.hijriDateOf(range.qadrNightEveningDate)?.day)
            assertEquals(9, DiyanetHijriCalendar.hijriDateOf(range.qadrNightEveningDate)?.month)
            assertEquals(1, DiyanetHijriCalendar.hijriDateOf(range.eidDate)?.day)
            assertEquals(10, DiyanetHijriCalendar.hijriDateOf(range.eidDate)?.month)
        }
    }

    @Test
    fun kandilRulesMatchEveryOfficialListedDate() {
        val mevlid = DiyanetReligiousDays.entries.filter { it.name.contains("MEVLİD") || it.name.contains("MEVLİT") }
        assertEquals(11, mevlid.size)
        mevlid.forEach { assertHijri(it, day = 11, month = 3) }

        val miraj = DiyanetReligiousDays.entries.filter { it.name.contains("MİRAC") }
        assertEquals(12, miraj.size)
        miraj.forEach { assertHijri(it, day = 26, month = 7) }

        val berat = DiyanetReligiousDays.entries.filter { it.name.contains("BERAT") }
        assertEquals(12, berat.size)
        berat.forEach { assertHijri(it, day = 14, month = 8) }

        val regaip = DiyanetReligiousDays.entries.filter { it.name.contains("REGAİB") }
        assertEquals(12, regaip.size)
        regaip.forEach { event ->
            assertEquals("Regaip is listed for the first Friday night (Thursday evening): ${event.name}", DayOfWeek.THURSDAY, event.date.dayOfWeek)
            val listedDay = requireNotNull(DiyanetHijriCalendar.hijriDateOf(event.date))
            val nextDay = requireNotNull(DiyanetHijriCalendar.hijriDateOf(event.date.plusDays(1)))
            val firstFridayNightInRajab =
                (listedDay.month == 7 && listedDay.day in 1..6) ||
                    (listedDay.month == 6 && listedDay.day in 29..30 && nextDay.month == 7 && nextDay.day == 1)
            assertTrue("Regaip Hijri date is part of the first Friday night in Rajab: $event", firstFridayNightInRajab)
        }
    }

    @Test
    fun hijriNewYearAshuraAndFirstQurbanDayMatchOfficialHijriDates() {
        val newYear = DiyanetReligiousDays.entries.filter { it.name.contains("HİCRİ YILBAŞI") }
        val ashura = DiyanetReligiousDays.entries.filter { it.name.contains("AŞURE GÜNÜ") }
        val qurbanFirstDays = DiyanetReligiousDays.entries.filter {
            it.name.contains("KURBAN BAYRAMI") && it.name.contains("1. Gün")
        }
        assertEquals(11, newYear.size)
        assertEquals(11, ashura.size)
        assertEquals(11, qurbanFirstDays.size)
        newYear.forEach { assertHijri(it, day = 1, month = 1) }
        ashura.forEach { assertHijri(it, day = 10, month = 1) }
        qurbanFirstDays.forEach { assertHijri(it, day = 10, month = 12) }
    }

    @Test
    fun slashSeparatedOfficialDatesArePreservedWithoutGuessing() {
        val regaip2031 = DiyanetReligiousDays.forYear(2031).single { it.name.contains("REGAİB") }
        assertEquals(LocalDate.of(2031, 10, 16), regaip2031.date)
        assertEquals(listOf(LocalDate.of(2031, 10, 17)), regaip2031.alternateDates)
        assertEquals("29/01 C.AHİR/RECEB 1453", regaip2031.sourceHijriDateLabel)
        assertTrue(regaip2031 in DiyanetReligiousDays.on(LocalDate.of(2031, 10, 17)))
    }

    private fun assertHijri(event: DiyanetReligiousDay, day: Int, month: Int) {
        val hijri = requireNotNull(DiyanetHijriCalendar.hijriDateOf(event.date)) {
            "No official Hijri date for ${event.name} on ${event.date}"
        }
        assertEquals("${event.name} on ${event.date}", day, hijri.day)
        assertEquals("${event.name} on ${event.date}", month, hijri.month)
    }

    private fun assertEventAt(date: LocalDate, predicate: (DiyanetReligiousDay) -> Boolean) {
        assertNotNull("Missing Diyanet event for $date", DiyanetReligiousDays.on(date).firstOrNull(predicate))
    }
}
