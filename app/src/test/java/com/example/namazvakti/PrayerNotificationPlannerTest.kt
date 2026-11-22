package com.example.namazvakti

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerLocationConfig
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.policy.PrayerNotificationDeliveryPolicy
import com.example.namazvakti.domain.policy.PrayerNotificationEventId
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
    fun usesLocationCacheKeyForNonCentralDistrictEvents() {
        val district = DiyanetDistricts.districts.single {
            it.provinceName == "İSTANBUL" && it.name == "MALTEPE"
        }
        val province = DiyanetDistricts.provinces.single { it.id == district.provinceId }
        val provinceOption = PrayerLocationConfig.cityOptions.single { it.displayCity == province.name }
        val districtLocation = PrayerLocation(
            city = provinceOption.city,
            country = provinceOption.country,
            displayCity = provinceOption.displayCity,
            diyanetDistrictId = district.id,
            districtName = district.name
        )
        val provinceLocation = PrayerLocation(
            city = provinceOption.city,
            country = provinceOption.country,
            displayCity = provinceOption.displayCity,
            diyanetDistrictId = province.centralDistrictId,
            districtName = province.name
        )
        val event = planner().nextEvent(
            today().copy(location = districtLocation),
            tomorrow().copy(location = districtLocation),
            settings(),
            now()
        )

        assertEquals(districtLocation.cacheKey, event?.locationCity)
        assertFalse(districtLocation.cacheKey == provinceLocation.cacheKey)
    }

    @Test
    fun replanRecoversTheLatestUnshownPrayerImmediately() {
        val day = today().let { it.copy(prayerTimes = it.prayerTimes.copy(dhuhr = LocalTime.of(12, 30))) }
        val now = ZonedDateTime.parse("2026-08-06T12:32:00Z")
        val event = requireNotNull(planner().nextEvent(day, tomorrow(), settings(), now, exact = false))

        assertEquals(PrayerNotificationPrayer.DHUHR, event.prayer)
        assertEquals(PrayerNotificationType.AT_TIME, event.type)
        assertEquals(now.toInstant(), event.triggerAt.toInstant())
        assertEquals(LocalTime.of(12, 30), event.prayerTime.toLocalTime())
        assertTrue(
            PrayerNotificationDeliveryPolicy.shouldShow(
                event.type, now.toInstant(), event.triggerAt.toInstant(),
                event.prayerTime.toInstant(), event.prayerTime.toInstant(),
                currentLeadMinutes = 0, prayerEnabled = true, exact = false
            )
        )

        val next = planner().nextEvent(
            day, tomorrow(), settings(), now, exact = false, shownEvents = setOf(event.id)
        )
        assertEquals(LocalDate.of(2026, 8, 7), next?.id?.date)
    }

    @Test
    fun replanUsesTheAlarmModeAndExcludesTheStartOfTheWindow() {
        val atPrayerTime = ZonedDateTime.parse("2026-08-06T10:00:00Z")
        fun next(exact: Boolean, now: ZonedDateTime) =
            planner().nextEvent(today(), tomorrow(), settings(), now, exact = exact)?.triggerAt?.toInstant()

        val fifteenMinutesLater = atPrayerTime.plusMinutes(15)
        assertEquals(atPrayerTime.toInstant(), next(false, atPrayerTime))
        assertEquals(fifteenMinutesLater.toInstant(), next(false, fifteenMinutesLater))
        assertEquals(atPrayerTime.plusDays(1).toInstant(), next(true, fifteenMinutesLater))
        assertEquals(atPrayerTime.plusDays(1).toInstant(), next(false, atPrayerTime.plusMinutes(30)))
    }

    @Test
    fun replanRecoversYesterdayWhenTheWindowCrossesMidnight() {
        val yesterday = today().let { it.copy(prayerTimes = it.prayerTimes.copy(isha = LocalTime.of(23, 55))) }
        val now = ZonedDateTime.parse("2026-08-07T00:10:00Z")
        val event = planner().nextEvent(
            tomorrow(), null, settings(enabledPrayers = setOf(PrayerNotificationPrayer.ISHA)),
            now, exact = false, yesterday = yesterday
        )

        assertEquals(yesterday.date, event?.id?.date)
        assertEquals(now.toInstant(), event?.triggerAt?.toInstant())
    }

    @Test
    fun replanStillSchedulesAChangedPrayerIfItsNewTimeIsUpcoming() {
        val attempted = PrayerNotificationEventId(
            PrayerNotificationPrayer.DHUHR, today().date, PrayerNotificationType.AT_TIME
        )
        val day = today().let { it.copy(prayerTimes = it.prayerTimes.copy(dhuhr = LocalTime.of(10, 5))) }
        val originalTime = ZonedDateTime.parse("2026-08-06T10:00:00Z")

        val upcoming = planner().nextEvent(day, tomorrow(), settings(), originalTime, attemptedEvent = attempted)
        val past = planner().nextEvent(day, tomorrow(), settings(), originalTime.plusMinutes(6), attemptedEvent = attempted)

        assertEquals(originalTime.plusMinutes(5).toInstant(), upcoming?.triggerAt?.toInstant())
        assertEquals(tomorrow().date, past?.id?.date)
    }

    @Test
    fun deliveryPolicyUsesTheModeOfTheScheduledAlarm() {
        assertTrue(allowedAtTime(15, exact = false))
        assertTrue(allowedAtTime(30, exact = false))
        assertFalse(allowedAtTime(35, exact = false))
        assertFalse(allowedAtTime(15, exact = true))
        assertTrue(allowedAtTime(10, exact = true))
        assertFalse(allowedAtTime(-1, exact = true))
        assertFalse(allowedAtTime(35, exact = false, triggerDelayMinutes = 29))
    }

    @Test
    fun deliveryPolicyAllowsSmallTimeShiftsAndRejectsDuplicates() {
        assertTrue(allowedAtTime(1, shiftMinutes = 1))
        assertTrue(allowedAtTime(1, shiftMinutes = -2))
        assertFalse(allowedAtTime(1, shiftMinutes = 5))
        assertFalse(allowedAtTime(1, alreadyShown = true))
    }

    private fun allowedAtTime(
        delayMinutes: Long,
        exact: Boolean = true,
        shiftMinutes: Long = 0,
        triggerDelayMinutes: Long = 0,
        alreadyShown: Boolean = false
    ): Boolean {
        val prayer = Instant.parse("2026-08-06T10:00:00Z")
        return PrayerNotificationDeliveryPolicy.shouldShow(
            type = PrayerNotificationType.AT_TIME,
            now = prayer.plusSeconds(delayMinutes * 60),
            scheduledTriggerAt = prayer.plusSeconds(triggerDelayMinutes * 60),
            scheduledPrayerTime = prayer,
            currentPrayerTime = prayer.plusSeconds(shiftMinutes * 60),
            currentLeadMinutes = 0,
            prayerEnabled = true,
            exact = exact,
            alreadyShown = alreadyShown
        )
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
