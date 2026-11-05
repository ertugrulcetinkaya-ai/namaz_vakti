package com.example.namazvakti

import com.example.namazvakti.domain.policy.ReligiousDayNotificationDeliveryPolicy
import com.example.namazvakti.domain.policy.ReligiousDayNotificationPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReligiousDayNotificationPlannerTest {
    private val istanbul = ZoneId.of("Europe/Istanbul")

    @Test
    fun schedulesNextDayAtTenBeforeTenAndSkipsTodayAfterTen() {
        val before = ReligiousDayNotificationPlanner.nextEvent(
            ZonedDateTime.of(2026, 12, 10, 9, 59, 0, 0, istanbul)
        )
        assertEquals(LocalDate.of(2026, 12, 10), before?.date)
        assertEquals(ZonedDateTime.of(2026, 12, 10, 10, 0, 0, 0, istanbul), before?.triggerAt)
        assertEquals("Bu akşam Regaip Kandili", before?.text)

        val after = ReligiousDayNotificationPlanner.nextEvent(
            ZonedDateTime.of(2026, 12, 10, 10, 1, 0, 0, istanbul)
        )
        assertTrue(requireNotNull(after).date.isAfter(LocalDate.of(2026, 12, 10)))
    }

    @Test
    fun rollsAcrossGregorianYearAndUsesDeviceTimezone() {
        val now = ZonedDateTime.of(2025, 12, 31, 9, 0, 0, 0, istanbul)
        val event = ReligiousDayNotificationPlanner.nextEvent(now)

        assertEquals(LocalDate.of(2026, 1, 15), event?.date)
        assertEquals(istanbul, event?.triggerAt?.zone)
    }

    @Test
    fun usesFirstOfficialDateForReminderWhenSourceListsAlternatives() {
        val event = ReligiousDayNotificationPlanner.nextEvent(
            ZonedDateTime.of(2031, 10, 15, 10, 0, 0, 0, istanbul)
        )
        assertEquals(LocalDate.of(2031, 10, 16), event?.date)
    }

    @Test
    fun returnsNullWhenDisabledOrBeyondThePublishedTable() {
        val afterTable = ZonedDateTime.of(2035, 12, 31, 9, 0, 0, 0, istanbul)
        assertNull(ReligiousDayNotificationPlanner.nextEvent(afterTable))
        assertNull(ReligiousDayNotificationPlanner.nextEvent(afterTable, enabled = false))
    }

    @Test
    fun deliveryAllowsAtMostTwoHoursLateAndRejectsWrongDateOrDisabledSetting() {
        val trigger = Instant.parse("2026-12-10T07:00:00Z")
        fun allowed(now: Instant, date: LocalDate = LocalDate.of(2026, 12, 10), enabled: Boolean = true) =
            ReligiousDayNotificationDeliveryPolicy.shouldShow(now, trigger, date, istanbul, enabled)

        assertTrue(allowed(trigger))
        assertTrue(allowed(trigger.plusSeconds(2 * 60 * 60L)))
        assertFalse(allowed(trigger.minusSeconds(1)))
        assertFalse(allowed(trigger.plusSeconds(2 * 60 * 60L + 1)))
        assertFalse(allowed(trigger, date = LocalDate.of(2026, 12, 11)))
        assertFalse(allowed(trigger, enabled = false))
    }
}
