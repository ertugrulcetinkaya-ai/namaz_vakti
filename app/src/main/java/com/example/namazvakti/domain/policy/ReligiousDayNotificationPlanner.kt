package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.DiyanetReligiousDay
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private val RELIGIOUS_DAY_NOTIFICATION_TIME: LocalTime = LocalTime.of(10, 0)

data class ReligiousDayNotificationEvent(
    val religiousDays: List<DiyanetReligiousDay>,
    val date: LocalDate,
    val triggerAt: ZonedDateTime
) {
    val title: String
        get() = religiousDays.first().announcementText()

    val text: String
        get() = title

    val secondaryText: String?
        get() = religiousDays.drop(1).takeIf { it.isNotEmpty() }
            ?.joinToString(" · ") { it.announcementText() }
}

private fun DiyanetReligiousDay.announcementText(): String =
    if (isNight) "Bu akşam $displayName" else "Bugün $displayName"

/** Plans one next 10:00 local-device reminder, combining all events on that date. */
object ReligiousDayNotificationPlanner {
    fun nextEvent(
        now: ZonedDateTime,
        events: List<DiyanetReligiousDay> = DiyanetReligiousDays.entries,
        enabled: Boolean = true
    ): ReligiousDayNotificationEvent? {
        if (!enabled) return null
        return events.asSequence()
            .groupBy(DiyanetReligiousDay::date)
            .mapNotNull { (date, sameDayEvents) ->
                val triggerAt = date.atTime(RELIGIOUS_DAY_NOTIFICATION_TIME).atZone(now.zone)
                if (!triggerAt.isAfter(now)) return@mapNotNull null
                ReligiousDayNotificationEvent(
                    religiousDays = sameDayEvents.sortedBy { if (it.isNight) 1 else 0 },
                    date = date,
                    triggerAt = triggerAt
                )
            }
            .minByOrNull { it.triggerAt.toInstant() }
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
