package com.example.namazvakti.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DiyanetHijriCalendar {
    const val ALADHAN_FALLBACK_METHOD = "HJCoSA"

    /**
     * Official Diyanet month starts from the 2025–2035 yearly Dini Günler lists.
     * The daily sequence reconstructed from these starts matches Ankara’s official 2026 annual table 365/365
     * (verified 2026-11-05; method comparison notes: review_bundles/HIJRI_DAILY_COMPARISON_2026-11-05.md).
     * Outside complete months, API values use AlAdhan HJCoSA: the documented default, and the best-tied
     * result in the 2026 method comparison (305/365; no tested method matched all days).
     * A month is exposed only when its following start is present; 1457 Zilhicce is intentionally absent.
     */
    private val monthStarts = listOf(
        // Diyanet 2025: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=152
        HijriMonthStart(1446, 7, LocalDate.parse("2025-01-01")),
        HijriMonthStart(1446, 8, LocalDate.parse("2025-01-31")),
        HijriMonthStart(1446, 9, LocalDate.parse("2025-03-01")),
        HijriMonthStart(1446, 10, LocalDate.parse("2025-03-30")),
        HijriMonthStart(1446, 11, LocalDate.parse("2025-04-29")),
        HijriMonthStart(1446, 12, LocalDate.parse("2025-05-28")),
        HijriMonthStart(1447, 1, LocalDate.parse("2025-06-26")),
        HijriMonthStart(1447, 2, LocalDate.parse("2025-07-26")),
        HijriMonthStart(1447, 3, LocalDate.parse("2025-08-24")),
        HijriMonthStart(1447, 4, LocalDate.parse("2025-09-23")),
        HijriMonthStart(1447, 5, LocalDate.parse("2025-10-23")),
        HijriMonthStart(1447, 6, LocalDate.parse("2025-11-21")),
        HijriMonthStart(1447, 7, LocalDate.parse("2025-12-21")),

        // Diyanet 2026: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=153
        HijriMonthStart(1447, 8, LocalDate.parse("2026-01-20")),
        HijriMonthStart(1447, 9, LocalDate.parse("2026-02-19")),
        HijriMonthStart(1447, 10, LocalDate.parse("2026-03-20")),
        HijriMonthStart(1447, 11, LocalDate.parse("2026-04-18")),
        HijriMonthStart(1447, 12, LocalDate.parse("2026-05-18")),
        HijriMonthStart(1448, 1, LocalDate.parse("2026-06-16")),
        HijriMonthStart(1448, 2, LocalDate.parse("2026-07-15")),
        HijriMonthStart(1448, 3, LocalDate.parse("2026-08-14")),
        HijriMonthStart(1448, 4, LocalDate.parse("2026-09-12")),
        HijriMonthStart(1448, 5, LocalDate.parse("2026-10-12")),
        HijriMonthStart(1448, 6, LocalDate.parse("2026-11-10")),
        HijriMonthStart(1448, 7, LocalDate.parse("2026-12-10")),

        // Diyanet 2027: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=154
        HijriMonthStart(1448, 8, LocalDate.parse("2027-01-09")),
        HijriMonthStart(1448, 9, LocalDate.parse("2027-02-08")),
        HijriMonthStart(1448, 10, LocalDate.parse("2027-03-09")),
        HijriMonthStart(1448, 11, LocalDate.parse("2027-04-08")),
        HijriMonthStart(1448, 12, LocalDate.parse("2027-05-07")),
        HijriMonthStart(1449, 1, LocalDate.parse("2027-06-06")),
        HijriMonthStart(1449, 2, LocalDate.parse("2027-07-05")),
        HijriMonthStart(1449, 3, LocalDate.parse("2027-08-03")),
        HijriMonthStart(1449, 4, LocalDate.parse("2027-09-02")),
        HijriMonthStart(1449, 5, LocalDate.parse("2027-10-01")),
        HijriMonthStart(1449, 6, LocalDate.parse("2027-10-31")),
        HijriMonthStart(1449, 7, LocalDate.parse("2027-11-29")),
        HijriMonthStart(1449, 8, LocalDate.parse("2027-12-29")),

        // Diyanet 2028: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=185
        HijriMonthStart(1449, 9, LocalDate.parse("2028-01-28")),
        HijriMonthStart(1449, 10, LocalDate.parse("2028-02-26")),
        HijriMonthStart(1449, 11, LocalDate.parse("2028-03-27")),
        HijriMonthStart(1449, 12, LocalDate.parse("2028-04-26")),
        HijriMonthStart(1450, 1, LocalDate.parse("2028-05-25")),
        HijriMonthStart(1450, 2, LocalDate.parse("2028-06-24")),
        HijriMonthStart(1450, 3, LocalDate.parse("2028-07-23")),
        HijriMonthStart(1450, 4, LocalDate.parse("2028-08-21")),
        HijriMonthStart(1450, 5, LocalDate.parse("2028-09-20")),
        HijriMonthStart(1450, 6, LocalDate.parse("2028-10-19")),
        HijriMonthStart(1450, 7, LocalDate.parse("2028-11-18")),
        HijriMonthStart(1450, 8, LocalDate.parse("2028-12-17")),

        // Diyanet 2029: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=186
        HijriMonthStart(1450, 9, LocalDate.parse("2029-01-16")),
        HijriMonthStart(1450, 10, LocalDate.parse("2029-02-14")),
        HijriMonthStart(1450, 11, LocalDate.parse("2029-03-16")),
        HijriMonthStart(1450, 12, LocalDate.parse("2029-04-15")),
        HijriMonthStart(1451, 1, LocalDate.parse("2029-05-14")),
        HijriMonthStart(1451, 2, LocalDate.parse("2029-06-13")),
        HijriMonthStart(1451, 3, LocalDate.parse("2029-07-13")),
        HijriMonthStart(1451, 4, LocalDate.parse("2029-08-11")),
        HijriMonthStart(1451, 5, LocalDate.parse("2029-09-09")),
        HijriMonthStart(1451, 6, LocalDate.parse("2029-10-09")),
        HijriMonthStart(1451, 7, LocalDate.parse("2029-11-07")),
        HijriMonthStart(1451, 8, LocalDate.parse("2029-12-07")),

        // Diyanet 2030: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=187
        HijriMonthStart(1451, 9, LocalDate.parse("2030-01-05")),
        HijriMonthStart(1451, 10, LocalDate.parse("2030-02-04")),
        HijriMonthStart(1451, 11, LocalDate.parse("2030-03-05")),
        HijriMonthStart(1451, 12, LocalDate.parse("2030-04-04")),
        HijriMonthStart(1452, 1, LocalDate.parse("2030-05-03")),
        HijriMonthStart(1452, 2, LocalDate.parse("2030-06-02")),
        HijriMonthStart(1452, 3, LocalDate.parse("2030-07-02")),
        HijriMonthStart(1452, 4, LocalDate.parse("2030-08-01")),
        HijriMonthStart(1452, 5, LocalDate.parse("2030-08-30")),
        HijriMonthStart(1452, 6, LocalDate.parse("2030-09-28")),
        HijriMonthStart(1452, 7, LocalDate.parse("2030-10-28")),
        HijriMonthStart(1452, 8, LocalDate.parse("2030-11-26")),
        HijriMonthStart(1452, 9, LocalDate.parse("2030-12-26")),

        // Diyanet 2031: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=188
        HijriMonthStart(1452, 10, LocalDate.parse("2031-01-24")),
        HijriMonthStart(1452, 11, LocalDate.parse("2031-02-22")),
        HijriMonthStart(1452, 12, LocalDate.parse("2031-03-24")),
        HijriMonthStart(1453, 1, LocalDate.parse("2031-04-23")),
        HijriMonthStart(1453, 2, LocalDate.parse("2031-05-22")),
        HijriMonthStart(1453, 3, LocalDate.parse("2031-06-21")),
        HijriMonthStart(1453, 4, LocalDate.parse("2031-07-21")),
        HijriMonthStart(1453, 5, LocalDate.parse("2031-08-19")),
        HijriMonthStart(1453, 6, LocalDate.parse("2031-09-18")),
        HijriMonthStart(1453, 7, LocalDate.parse("2031-10-17")),
        HijriMonthStart(1453, 8, LocalDate.parse("2031-11-16")),
        HijriMonthStart(1453, 9, LocalDate.parse("2031-12-15")),

        // Diyanet 2032: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=189
        HijriMonthStart(1453, 10, LocalDate.parse("2032-01-14")),
        HijriMonthStart(1453, 11, LocalDate.parse("2032-02-12")),
        HijriMonthStart(1453, 12, LocalDate.parse("2032-03-13")),
        HijriMonthStart(1454, 1, LocalDate.parse("2032-04-11")),
        HijriMonthStart(1454, 2, LocalDate.parse("2032-05-11")),
        HijriMonthStart(1454, 3, LocalDate.parse("2032-06-09")),
        HijriMonthStart(1454, 4, LocalDate.parse("2032-07-09")),
        HijriMonthStart(1454, 5, LocalDate.parse("2032-08-07")),
        HijriMonthStart(1454, 6, LocalDate.parse("2032-09-06")),
        HijriMonthStart(1454, 7, LocalDate.parse("2032-10-06")),
        HijriMonthStart(1454, 8, LocalDate.parse("2032-11-04")),
        HijriMonthStart(1454, 9, LocalDate.parse("2032-12-04")),

        // Diyanet 2033: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=190
        HijriMonthStart(1454, 10, LocalDate.parse("2033-01-02")),
        HijriMonthStart(1454, 11, LocalDate.parse("2033-02-01")),
        HijriMonthStart(1454, 12, LocalDate.parse("2033-03-02")),
        HijriMonthStart(1455, 1, LocalDate.parse("2033-04-01")),
        HijriMonthStart(1455, 2, LocalDate.parse("2033-04-30")),
        HijriMonthStart(1455, 3, LocalDate.parse("2033-05-30")),
        HijriMonthStart(1455, 4, LocalDate.parse("2033-06-28")),
        HijriMonthStart(1455, 5, LocalDate.parse("2033-07-27")),
        HijriMonthStart(1455, 6, LocalDate.parse("2033-08-26")),
        HijriMonthStart(1455, 7, LocalDate.parse("2033-09-25")),
        HijriMonthStart(1455, 8, LocalDate.parse("2033-10-24")),
        HijriMonthStart(1455, 9, LocalDate.parse("2033-11-23")),
        HijriMonthStart(1455, 10, LocalDate.parse("2033-12-23")),

        // Diyanet 2034: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=191
        HijriMonthStart(1455, 11, LocalDate.parse("2034-01-21")),
        HijriMonthStart(1455, 12, LocalDate.parse("2034-02-20")),
        HijriMonthStart(1456, 1, LocalDate.parse("2034-03-21")),
        HijriMonthStart(1456, 2, LocalDate.parse("2034-04-20")),
        HijriMonthStart(1456, 3, LocalDate.parse("2034-05-19")),
        HijriMonthStart(1456, 4, LocalDate.parse("2034-06-17")),
        HijriMonthStart(1456, 5, LocalDate.parse("2034-07-17")),
        HijriMonthStart(1456, 6, LocalDate.parse("2034-08-15")),
        HijriMonthStart(1456, 7, LocalDate.parse("2034-09-14")),
        HijriMonthStart(1456, 8, LocalDate.parse("2034-10-13")),
        HijriMonthStart(1456, 9, LocalDate.parse("2034-11-12")),
        HijriMonthStart(1456, 10, LocalDate.parse("2034-12-12")),

        // Diyanet 2035: https://vakithesaplama.diyanet.gov.tr/icerik.php?icerik=192
        HijriMonthStart(1456, 11, LocalDate.parse("2035-01-11")),
        HijriMonthStart(1456, 12, LocalDate.parse("2035-02-09")),
        HijriMonthStart(1457, 1, LocalDate.parse("2035-03-11")),
        HijriMonthStart(1457, 2, LocalDate.parse("2035-04-09")),
        HijriMonthStart(1457, 3, LocalDate.parse("2035-05-09")),
        HijriMonthStart(1457, 4, LocalDate.parse("2035-06-07")),
        HijriMonthStart(1457, 5, LocalDate.parse("2035-07-06")),
        HijriMonthStart(1457, 6, LocalDate.parse("2035-08-05")),
        HijriMonthStart(1457, 7, LocalDate.parse("2035-09-03")),
        HijriMonthStart(1457, 8, LocalDate.parse("2035-10-03")),
        HijriMonthStart(1457, 9, LocalDate.parse("2035-11-01")),
        HijriMonthStart(1457, 10, LocalDate.parse("2035-12-01")),
        HijriMonthStart(1457, 11, LocalDate.parse("2035-12-31")),
    )
        .sortedBy(HijriMonthStart::gregorianStart)

    private val startsByHijriMonth = monthStarts.associateBy { it.hijriYear to it.hijriMonth }

    fun startDateOf(hijriYear: Int, hijriMonth: Int): LocalDate? =
        startsByHijriMonth[hijriYear to hijriMonth]?.gregorianStart

    fun hijriDateOf(date: LocalDate): HijriDate? {
        val index = monthStarts.indexOfLast { !it.gregorianStart.isAfter(date) }
        if (index < 0 || index >= monthStarts.lastIndex) return null

        val current = monthStarts[index]
        val next = monthStarts[index + 1]
        val monthLength = ChronoUnit.DAYS.between(current.gregorianStart, next.gregorianStart).toInt()
        if (monthLength !in 29..30 || date.isBefore(current.gregorianStart) || !date.isBefore(next.gregorianStart)) {
            return null
        }

        return HijriDate(
            day = ChronoUnit.DAYS.between(current.gregorianStart, date).toInt() + 1,
            month = current.hijriMonth,
            year = current.hijriYear
        )
    }

    fun ramadanRangeOf(hijriYear: Int): RamadanRange? {
        val start = startDateOf(hijriYear, 9) ?: return null
        val eid = startDateOf(hijriYear, 10) ?: return null
        return RamadanRange(
            hijriYear = hijriYear,
            startDate = start,
            endDate = eid.minusDays(1),
            eidDate = eid,
            estimated = false
        )
    }

    private data class HijriMonthStart(
        val hijriYear: Int,
        val hijriMonth: Int,
        val gregorianStart: LocalDate
    )
}

data class HijriDate(val day: Int, val month: Int, val year: Int) {
    init {
        require(day in 1..30)
        require(month in 1..12)
        require(year > 0)
    }

    fun toDisplayText(): String = "$day ${MONTH_NAMES[month - 1]} $year"

    fun toCompactDisplayText(): String = "$day ${SHORT_MONTH_NAMES[month - 1]}"

    companion object {
        private val MONTH_NAMES = listOf(
            "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir",
            "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce"
        )
        private val SHORT_MONTH_NAMES = listOf(
            "Muh", "Saf", "R.Ev", "R.Ah", "C.Ev", "C.Ah", "Recep", "Şab", "Ram", "Şev", "Zil", "ZilH"
        )
    }
}

fun CachedPrayerDay.alAdhanHijriDate(): HijriDate? {
    val day = hijriDay ?: return null
    val month = hijriMonth ?: return null
    val year = hijriYear ?: return null
    if (day !in 1..30 || month !in 1..12 || year <= 0) return null
    return HijriDate(day, month, year)
}

fun CachedPrayerDay.displayHijriDate(): HijriDate? =
    DiyanetHijriCalendar.hijriDateOf(date) ?: alAdhanHijriDate()

fun CachedPrayerDay.displayHijriText(): String? = displayHijriDate()?.toDisplayText()
