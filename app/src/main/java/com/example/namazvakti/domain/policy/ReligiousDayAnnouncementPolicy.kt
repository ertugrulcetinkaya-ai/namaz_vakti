package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.DiyanetReligiousDay
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReligiousDayAnnouncement(
    val events: List<DiyanetReligiousDay>,
    val date: LocalDate,
    val daysUntil: Int
) {
    val text: String
        get() = events.joinToString(" · ") { event ->
            when {
                daysUntil == 0 && event.isNight -> "Bu akşam ${event.displayName}"
                daysUntil == 0 -> "Bugün ${event.displayName}"
                daysUntil == 1 && event.isNight -> "Yarın akşam ${event.displayName}"
                daysUntil == 1 -> "Yarın: ${event.displayName}"
                event.isNight -> "$daysUntil gün sonra akşam: ${event.displayName}"
                else -> "$daysUntil gün sonra: ${event.displayName}"
            }
        }
}

/** Chooses today's observances or the nearest listed date during the next seven days. */
object ReligiousDayAnnouncementPolicy {
    fun select(
        today: LocalDate,
        events: List<DiyanetReligiousDay> = DiyanetReligiousDays.entries
    ): ReligiousDayAnnouncement? {
        val groupedByDate = events.groupBy(DiyanetReligiousDay::date)
        val todayEvents = groupedByDate[today]
        if (!todayEvents.isNullOrEmpty()) {
            return ReligiousDayAnnouncement(todayEvents.sortedBy(::nightOrder), today, 0)
        }

        val nearest = groupedByDate.keys.asSequence()
            .map { date -> date to ChronoUnit.DAYS.between(today, date).toInt() }
            .filter { (_, days) -> days in 1..7 }
            .minByOrNull { it.second }
            ?: return null
        val dateEvents = groupedByDate.getValue(nearest.first).sortedBy(::nightOrder)
        return ReligiousDayAnnouncement(dateEvents, nearest.first, nearest.second)
    }

    private fun nightOrder(event: DiyanetReligiousDay): Int = if (event.isNight) 1 else 0
}
