package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.DiyanetReligiousDay
import com.example.namazvakti.domain.model.DiyanetReligiousDayType
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReligiousDayAnnouncement(
    val event: DiyanetReligiousDay,
    val date: LocalDate,
    val daysUntil: Int
) {
    val text: String
        get() = when {
            daysUntil == 0 && event.isNight -> "Bu akşam ${event.displayName}"
            daysUntil == 0 -> "Bugün ${event.displayName}"
            daysUntil == 1 && event.isNight -> "Yarın akşam ${event.displayName}"
            daysUntil == 1 -> "Yarın: ${event.displayName}"
            event.isNight -> "$daysUntil gün sonra akşam: ${event.displayName}"
            else -> "$daysUntil gün sonra: ${event.displayName}"
        }
}

/** Chooses today's observance or the nearest listed observance during the next seven days. */
object ReligiousDayAnnouncementPolicy {
    fun select(
        today: LocalDate,
        events: List<DiyanetReligiousDay> = DiyanetReligiousDays.entries
    ): ReligiousDayAnnouncement? {
        val todayEvent = events.asSequence()
            .filter { today in it.dates }
            .sortedBy(::priority)
            .firstOrNull()
        if (todayEvent != null) return ReligiousDayAnnouncement(todayEvent, today, 0)

        return events.asSequence()
            .flatMap { event -> event.dates.asSequence().map { date -> event to date } }
            .map { (event, date) -> event to (date to ChronoUnit.DAYS.between(today, date).toInt()) }
            .filter { (_, pair) -> pair.second in 1..7 }
            .sortedWith(compareBy({ it.second.second }, { priority(it.first) }, { it.second.first }))
            .firstOrNull()
            ?.let { (event, pair) -> ReligiousDayAnnouncement(event, pair.first, pair.second) }
    }

    private fun priority(event: DiyanetReligiousDay): Int = when (event.type) {
        DiyanetReligiousDayType.KANDIL -> 0
        DiyanetReligiousDayType.BAYRAM -> 1
        DiyanetReligiousDayType.AREFE -> 2
        DiyanetReligiousDayType.SPECIAL_DAY -> 3
    }
}
