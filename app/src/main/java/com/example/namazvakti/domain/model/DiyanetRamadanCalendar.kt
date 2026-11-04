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

/** Ramadan ranges derived from the official Hijri month-start table. */
object DiyanetRamadanCalendar {
    val entries: Map<Int, RamadanCalendarEntry> = (1446..1457).mapNotNull { hijriYear ->
        val firstRamadan = DiyanetHijriCalendar.startDateOf(hijriYear, 9) ?: return@mapNotNull null
        val firstShawwal = DiyanetHijriCalendar.startDateOf(hijriYear, 10) ?: return@mapNotNull null
        RamadanCalendarEntry(hijriYear, firstRamadan, firstShawwal)
    }.associateBy(RamadanCalendarEntry::hijriYear)

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
