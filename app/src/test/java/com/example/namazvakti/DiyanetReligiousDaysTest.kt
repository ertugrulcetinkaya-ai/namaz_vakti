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
        assertEquals((2025..2035).toSet(), DiyanetReligiousDays.entries.map { it.date.year }.toSet())
        assertEquals(208, DiyanetReligiousDays.entries.size)
        DiyanetReligiousDays.entries.forEach { event ->
            assertEquals(event.type == DiyanetReligiousDayType.KANDIL, event.isNight)
            assertTrue("Missing display name for ${event.name}", event.displayName.isNotBlank())
            assertTrue("Unexpected display name for ${event.name}: ${event.displayName}", event.displayName in DISPLAY_NAMES)
        }
        val slashRows = DiyanetReligiousDays.entries.filter { "/" in it.sourceGregorianDateLabel }
        assertEquals(42, slashRows.size)
        slashRows.forEach { event ->
            assertTrue("Slash observance must be a night: ${event.name}", event.isNight)
            assertEquals("Slash observance is dated on its first listed day", event.date, DiyanetReligiousDays.on(event.date).single { it.name == event.name }.date)
        }
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
    fun slashSeparatedDateIsOneNightOnTheFirstListedDate() {
        val berat2028 = DiyanetReligiousDays.forYear(2028).single { it.name == "BERAT KANDİLİ" && it.date.monthValue == 1 }
        assertEquals(LocalDate.of(2028, 1, 11), berat2028.date)
        assertEquals("Berat Kandili", berat2028.displayName)
        assertEquals("11/12 OCAK -2028", berat2028.sourceGregorianDateLabel)
        assertEquals("14/15 ŞABAN 1449", berat2028.sourceHijriDateLabel)
        assertTrue(DiyanetReligiousDays.on(LocalDate.of(2028, 1, 12)).none { it.displayName == "Berat Kandili" })

        val regaip2031 = DiyanetReligiousDays.forYear(2031).single { it.name.contains("REGAİB") }
        assertEquals(LocalDate.of(2031, 10, 16), regaip2031.date)
        assertEquals("29/01 C.AHİR/RECEB 1453", regaip2031.sourceHijriDateLabel)
        assertTrue(DiyanetReligiousDays.on(LocalDate.of(2031, 10, 17)).none { it.displayName == "Regaip Kandili" })
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

    private companion object {
        val DISPLAY_NAMES = setOf(
            "Üç Ayların Başlangıcı", "Regaip Kandili", "Miraç Kandili", "Berat Kandili",
            "Ramazan Başlangıcı", "Kadir Gecesi", "Ramazan Bayramı Arefesi",
            "Ramazan Bayramı (1. gün)", "Ramazan Bayramı (2. gün)", "Ramazan Bayramı (3. gün)",
            "Kurban Bayramı Arefesi", "Kurban Bayramı (1. gün)", "Kurban Bayramı (2. gün)",
            "Kurban Bayramı (3. gün)", "Kurban Bayramı (4. gün)", "Hicri Yılbaşı", "Aşure Günü",
            "Mevlid Kandili"
        )
    }
}
