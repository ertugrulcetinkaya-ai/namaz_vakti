package com.example.namazvakti

import com.example.namazvakti.widget.alarm.PrayerAlarmPolicy
import com.example.namazvakti.widget.alarm.PrayerAlarmPrecision
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerAlarmPolicyTest {
    @Test
    fun exactAlarmIsUsedBeforeAndroid12WithoutSpecialAccess() {
        assertEquals(
            PrayerAlarmPrecision.Exact,
            PrayerAlarmPolicy.precision(sdkInt = 30, canScheduleExactAlarms = false)
        )
    }

    @Test
    fun android12AndLaterFallsBackWhenSpecialAccessIsMissing() {
        assertEquals(
            PrayerAlarmPrecision.Inexact,
            PrayerAlarmPolicy.precision(sdkInt = 31, canScheduleExactAlarms = false)
        )
    }

    @Test
    fun android12AndLaterUsesExactAlarmWhenSpecialAccessIsGranted() {
        assertEquals(
            PrayerAlarmPrecision.Exact,
            PrayerAlarmPolicy.precision(sdkInt = 31, canScheduleExactAlarms = true)
        )
    }
}
