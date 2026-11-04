package com.example.namazvakti.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class RamadanCalendarEntry(
    val hijriYear: Int,
    val firstRamadan: LocalDate,
    val firstShawwal: LocalDate
) {
    init {
        require(firstShawwal.isAfter(firstRamadan))
        require(ChronoUnit.DAYS.between(firstRamadan, firstShawwal) in 29L..30L)
    }

    fun toRange(estimated: Boolean = false) = RamadanRange(
        hijriYear = hijriYear,
        startDate = firstRamadan,
        endDate = firstShawwal.minusDays(1),
        eidDate = firstShawwal,
        estimated = estimated
    )
}

/** Official Diyanet dates, included only for Hijri years checked against published calendars. */
object DiyanetRamadanCalendar {
    val entries: Map<Int, RamadanCalendarEntry> = listOf(
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=153 (2026)
        RamadanCalendarEntry(1447, LocalDate.parse("2026-02-19"), LocalDate.parse("2026-03-20")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=154 (2027)
        RamadanCalendarEntry(1448, LocalDate.parse("2027-02-08"), LocalDate.parse("2027-03-09")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=185 (2028)
        RamadanCalendarEntry(1449, LocalDate.parse("2028-01-28"), LocalDate.parse("2028-02-26")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=186 (2029)
        RamadanCalendarEntry(1450, LocalDate.parse("2029-01-16"), LocalDate.parse("2029-02-14")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=187 (2030)
        RamadanCalendarEntry(1451, LocalDate.parse("2030-01-05"), LocalDate.parse("2030-02-04")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=187 and 188 (2030–2031)
        RamadanCalendarEntry(1452, LocalDate.parse("2030-12-26"), LocalDate.parse("2031-01-24")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=188 and 189 (2031–2032)
        RamadanCalendarEntry(1453, LocalDate.parse("2031-12-15"), LocalDate.parse("2032-01-14")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=189 and 190 (2032–2033)
        RamadanCalendarEntry(1454, LocalDate.parse("2032-12-04"), LocalDate.parse("2033-01-02")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=190 (2033)
        RamadanCalendarEntry(1455, LocalDate.parse("2033-11-23"), LocalDate.parse("2033-12-23")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=191 (2034)
        RamadanCalendarEntry(1456, LocalDate.parse("2034-11-12"), LocalDate.parse("2034-12-12")),
        // https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=192 (2035)
        RamadanCalendarEntry(1457, LocalDate.parse("2035-11-01"), LocalDate.parse("2035-12-01"))
    ).associateBy(RamadanCalendarEntry::hijriYear)

    fun find(hijriYear: Int): RamadanCalendarEntry? = entries[hijriYear]
}

data class RamadanRange(
    val hijriYear: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val eidDate: LocalDate,
    val estimated: Boolean
) {
    init {
        require(!endDate.isBefore(startDate))
        require(eidDate == endDate.plusDays(1))
        require(ChronoUnit.DAYS.between(startDate, eidDate) in 29L..30L)
    }

    val dayCount: Int get() = ChronoUnit.DAYS.between(startDate, eidDate).toInt()

    fun dayNumber(date: LocalDate): Int? =
        if (date in startDate..endDate) ChronoUnit.DAYS.between(startDate, date).toInt() + 1 else null

    /** The 27th night begins after the 26th fasting day. */
    val qadrNightEveningDate: LocalDate get() = startDate.plusDays(25)
}
