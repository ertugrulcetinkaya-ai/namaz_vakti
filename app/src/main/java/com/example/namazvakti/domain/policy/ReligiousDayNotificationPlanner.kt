package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.DiyanetReligiousDay
import com.example.namazvakti.domain.model.DiyanetReligiousDayType
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private val RELIGIOUS_DAY_NOTIFICATION_TIME: LocalTime = LocalTime.of(10, 0)

data class ReligiousDayNotificationEvent(
    val religiousDay: DiyanetReligiousDay,
    val date: LocalDate,
    val triggerAt: ZonedDateTime
) {
    val text: String
        get() = if (religiousDay.isNight) {
            "Bu akşam ${religiousDay.displayName}"
        } else {
            "Bugün ${religiousDay.displayName}"
        }
}

/** Plans one next 10:00 local-device reminder. Ambiguous source dates remain separate candidates. */
object ReligiousDayNotificationPlanner {
    fun nextEvent(
        now: ZonedDateTime,
        events: List<DiyanetReligiousDay> = DiyanetReligiousDays.entries,
        enabled: Boolean = true
    ): ReligiousDayNotificationEvent? {
        if (!enabled) return null
        val candidates = events.asSequence()
            .groupBy(DiyanetReligiousDay::date)
            .mapNotNull { (date, sameDayEvents) ->
                val triggerAt = date.atTime(RELIGIOUS_DAY_NOTIFICATION_TIME).atZone(now.zone)
                if (!triggerAt.isAfter(now)) return@mapNotNull null
                val event = sameDayEvents.minByOrNull(::priority) ?: return@mapNotNull null
                ReligiousDayNotificationEvent(event, date, triggerAt)
            }
        return candidates.minByOrNull { it.triggerAt.toInstant() }
    }

    private fun priority(event: DiyanetReligiousDay): Int = when {
        event.isNight -> 0
        event.type == DiyanetReligiousDayType.BAYRAM -> 1
        event.type == DiyanetReligiousDayType.AREFE -> 2
        else -> 3
    }
}

object ReligiousDayNotificationDeliveryPolicy {
    private val MAX_LATE: java.time.Duration = ChronoUnit.HOURS.duration.multipliedBy(2)

    fun shouldShow(
        now: Instant,
        scheduledTriggerAt: Instant,
        eventDate: LocalDate,
        deviceZone: java.time.ZoneId,
        enabled: Boolean
    ): Boolean {
        if (!enabled || now.isBefore(scheduledTriggerAt) || now.isAfter(scheduledTriggerAt.plus(MAX_LATE))) {
            return false
        }
        val expectedTrigger = eventDate.atTime(RELIGIOUS_DAY_NOTIFICATION_TIME).atZone(deviceZone).toInstant()
        return expectedTrigger == scheduledTriggerAt
    }
}
