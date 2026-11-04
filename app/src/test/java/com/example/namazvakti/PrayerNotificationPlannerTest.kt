package com.example.namazvakti

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.policy.PrayerNotificationDeliveryPolicy
import com.example.namazvakti.domain.policy.PrayerNotificationPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerNotificationPlannerTest {
    @Test
    fun selectsTheNextEnabledPrayerInChronologicalOrder() {
        val event = planner().nextEvent(today(), tomorrow(), settings(), now())

        assertEquals(PrayerNotificationPrayer.DHUHR, event?.prayer)
        assertEquals(PrayerNotificationType.AT_TIME, event?.type)
        assertEquals(Instant.parse("2026-08-06T10:00:00Z"), event?.triggerAt?.toInstant())
    }

    @Test
    fun plansReminderThenAtTimeAfterReminderHasPassed() {
        val reminderSettings = settings(leadMinutes = 15)
        val beforeReminder = ZonedDateTime.parse("2026-08-06T09:40:00Z")
        val reminder = planner().nextEvent(today(), tomorrow(), reminderSettings, beforeReminder)
        assertEquals(PrayerNotificationType.REMINDER, reminder?.type)
        assertEquals(Instant.parse("2026-08-06T09:45:00Z"), reminder?.triggerAt?.toInstant())

        val afterReminder = ZonedDateTime.parse("2026-08-06T09:46:00Z")
        val atTime = planner().nextEvent(today(), tomorrow(), reminderSettings, afterReminder)
        assertEquals(PrayerNotificationType.AT_TIME, atTime?.type)
        assertEquals(Instant.parse("2026-08-06T10:00:00Z"), atTime?.triggerAt?.toInstant())
    }

    @Test
    fun advancesToTomorrowWhenTodayHasNoRemainingEnabledPrayers() {
        val event = planner().nextEvent(
            today(), tomorrow(),
            settings(enabledPrayers = setOf(PrayerNotificationPrayer.IMSAK)),
            ZonedDateTime.parse("2026-08-06T22:30:00Z")
        )

        assertEquals(LocalDate.of(2026, 8, 7), event?.prayerTime?.toLocalDate())
        assertEquals(PrayerNotificationPrayer.IMSAK, event?.prayer)
    }

    @Test
    fun returnsNullWhenTomorrowCacheIsUnavailable() {
        val event = planner().nextEvent(
            today(), null,
            settings(enabledPrayers = setOf(PrayerNotificationPrayer.IMSAK)),
            ZonedDateTime.parse("2026-08-06T22:30:00Z")
        )
        assertNull(event)
    }

    @Test
    fun returnsNullWhenNotificationsOrAllPrayersAreDisabled() {
        assertNull(planner().nextEvent(today(), tomorrow(), settings(enabled = false), now()))
        assertNull(planner().nextEvent(today(), tomorrow(), settings(enabledPrayers = emptySet()), now()))
    }

    @Test
    fun interpretsNowAndPrayerTimeInCacheTimezone() {
        val zone = ZoneId.of("America/Los_Angeles")
        val localCache = today(zone)
        val localNow = ZonedDateTime.parse("2026-08-06T02:00:00Z")
        val event = planner().nextEvent(
            localCache, tomorrow(zone),
            settings(enabledPrayers = setOf(PrayerNotificationPrayer.IMSAK)),
            localNow
        )
        assertEquals(zone, event?.triggerAt?.zone)
        assertEquals(LocalTime.of(4, 0), event?.prayerTime?.toLocalTime())
        assertEquals(Instant.parse("2026-08-06T11:00:00Z"), event?.triggerAt?.toInstant())
    }

    @Test
    fun lateDeliveryPolicyRejectsChangedTimesAndAlarmsOverTenMinutesLate() {
        val trigger = Instant.parse("2026-08-06T10:00:00Z")
        val prayer = trigger
        fun allowed(now: Instant, current: Instant? = prayer) = PrayerNotificationDeliveryPolicy.shouldShow(
            PrayerNotificationType.AT_TIME,
            now,
            trigger,
            prayer,
            current,
            currentLeadMinutes = 0,
            prayerEnabled = true
        )

        assertTrue(allowed(trigger.plusSeconds(10 * 60L)))
        assertFalse(allowed(trigger.minusSeconds(1)))
        assertFalse(allowed(trigger.plusSeconds(10 * 60L + 1)))
        assertFalse(allowed(trigger, current = prayer.plusSeconds(60)))
    }

    @Test
    fun lateReminderIsSuppressedAfterPrayerTime() {
        val prayer = Instant.parse("2026-08-06T10:00:00Z")
        val trigger = prayer.minusSeconds(15 * 60L)
        val allowed = PrayerNotificationDeliveryPolicy.shouldShow(
            PrayerNotificationType.REMINDER,
            prayer,
            trigger,
            prayer,
            prayer,
            currentLeadMinutes = 15,
            prayerEnabled = true
        )
        assertFalse(allowed)
    }

    @Test
    fun deliveryPolicySuppressesChangedLocationAndReminderSettings() {
        val prayer = Instant.parse("2026-08-06T10:00:00Z")
        val reminder = prayer.minusSeconds(15 * 60L)
        val changedLocation = PrayerNotificationDeliveryPolicy.shouldShow(
            PrayerNotificationType.REMINDER,
            reminder,
            reminder,
            prayer,
            prayer,
            currentLeadMinutes = 15,
            prayerEnabled = true,
            locationMatches = false
        )
        val changedLead = PrayerNotificationDeliveryPolicy.shouldShow(
            PrayerNotificationType.REMINDER,
            reminder,
            reminder,
            prayer,
            prayer,
            currentLeadMinutes = 10,
            prayerEnabled = true
        )

        assertFalse(changedLocation)
        assertFalse(changedLead)
    }

    private fun planner() = PrayerNotificationPlanner

    private fun settings(
        enabled: Boolean = true,
        enabledPrayers: Set<PrayerNotificationPrayer> = setOf(PrayerNotificationPrayer.DHUHR),
        leadMinutes: Int = 0
    ) = PrayerNotificationSettings(enabled, enabledPrayers, leadMinutes)

    private fun now() = ZonedDateTime.parse("2026-08-06T09:00:00Z")

    private fun today(zone: ZoneId = ZoneId.of("UTC")) = cache(
        LocalDate.of(2026, 8, 6), zone,
        PrayerTimes(
            LocalTime.of(4, 0), LocalTime.of(5, 0), LocalTime.of(10, 0),
            LocalTime.of(16, 0), LocalTime.of(19, 0), LocalTime.of(21, 0)
        )
    )

    private fun tomorrow(zone: ZoneId = ZoneId.of("UTC")) = cache(
        LocalDate.of(2026, 8, 7), zone,
        PrayerTimes(
            LocalTime.of(4, 0), LocalTime.of(5, 0), LocalTime.of(10, 0),
            LocalTime.of(16, 0), LocalTime.of(19, 0), LocalTime.of(21, 0)
        )
    )

    private fun cache(date: LocalDate, zone: ZoneId, times: PrayerTimes) = CachedPrayerDay(
        date = date,
        location = PrayerLocation("Ankara", "Turkey", "ANKARA", zone),
        timezone = zone,
        settings = PrayerCalculationSettings(),
        prayerTimes = times,
        hijriText = null,
        fetchedAt = Instant.parse("2026-08-06T00:00:00Z")
    )
}
