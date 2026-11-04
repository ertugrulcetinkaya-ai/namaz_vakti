package com.example.namazvakti

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DiyanetHijriCalendar
import com.example.namazvakti.domain.model.HijriDate
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.displayHijriDate
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiyanetHijriCalendarTest {
    @Test
    fun allDaysOf2026MatchTheOfficialAnkaraAnnualTable() {
        val input = requireNotNull(javaClass.getResourceAsStream("/diyanet_ankara_2026_hijri.csv"))
        val rows = InputStreamReader(input).use { reader ->
            reader.readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("date,") }
        }

        var comparedDays = 0
        rows.forEach { row ->
            val (dateText, dayText, monthText, yearText) = row.split(',')
            val date = LocalDate.parse(dateText)
            assertEquals(
                "Official 2026 Hijri date on $date",
                HijriDate(dayText.toInt(), monthText.toInt(), yearText.toInt()),
                DiyanetHijriCalendar.hijriDateOf(date)
            )
            comparedDays++
        }

        assertEquals(365, comparedDays)
        assertEquals(HijriDate(25, 5, 1448), DiyanetHijriCalendar.hijriDateOf(LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun published2027MonthTransitionsAreExact() {
        val transitions = listOf(
            LocalDate.of(2027, 1, 8) to HijriDate(30, 7, 1448),
            LocalDate.of(2027, 1, 9) to HijriDate(1, 8, 1448),
            LocalDate.of(2027, 2, 7) to HijriDate(30, 8, 1448),
            LocalDate.of(2027, 2, 8) to HijriDate(1, 9, 1448),
            LocalDate.of(2027, 3, 8) to HijriDate(29, 9, 1448),
            LocalDate.of(2027, 3, 9) to HijriDate(1, 10, 1448),
            LocalDate.of(2027, 4, 7) to HijriDate(30, 10, 1448),
            LocalDate.of(2027, 4, 8) to HijriDate(1, 11, 1448),
            LocalDate.of(2027, 5, 7) to HijriDate(1, 12, 1448),
            LocalDate.of(2027, 6, 6) to HijriDate(1, 1, 1449)
        )

        transitions.forEach { (date, expected) ->
            assertEquals("Hijri date on $date", expected, DiyanetHijriCalendar.hijriDateOf(date))
        }
    }

    @Test
    fun lastCompleteMonthEndsFromItsNextOfficialStartAndNextDateUsesFallback() {
        val lastOfficialDay = LocalDate.of(2035, 12, 30)
        val firstUncoveredDay = lastOfficialDay.plusDays(1)

        assertEquals(HijriDate(30, 10, 1457), DiyanetHijriCalendar.hijriDateOf(lastOfficialDay))
        assertNull(DiyanetHijriCalendar.hijriDateOf(firstUncoveredDay))
        assertEquals(
            HijriDate(2, 11, 1457),
            cachedDay(firstUncoveredDay, day = 2, month = 11, year = 1457).displayHijriDate()
        )
    }

    @Test
    fun missingMonthStartDoesNotInventAThirtyDayMonth() {
        assertNotNull(DiyanetHijriCalendar.startDateOf(1457, 11))
        assertNull(DiyanetHijriCalendar.startDateOf(1457, 12))
        assertNull(DiyanetHijriCalendar.hijriDateOf(LocalDate.of(2036, 1, 1)))
    }

    private fun cachedDay(date: LocalDate, day: Int, month: Int, year: Int) = CachedPrayerDay(
        date = date,
        location = PrayerLocation("Ankara", "Turkey"),
        timezone = ZoneId.of("Europe/Istanbul"),
        settings = PrayerCalculationSettings(),
        prayerTimes = PrayerTimes(
            fajr = LocalTime.of(5, 0),
            sunrise = LocalTime.of(6, 30),
            dhuhr = LocalTime.of(12, 0),
            asr = LocalTime.of(15, 0),
            maghrib = LocalTime.of(18, 0),
            isha = LocalTime.of(19, 30)
        ),
        hijriText = "$day/$month/$year",
        fetchedAt = Instant.EPOCH,
        hijriDay = day,
        hijriMonth = month,
        hijriYear = year
    )
}
