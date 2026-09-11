package com.example.namazvakti

import com.example.namazvakti.app.*
import com.example.namazvakti.data.local.*
import com.example.namazvakti.data.remote.*
import com.example.namazvakti.data.repository.*
import com.example.namazvakti.domain.model.*
import com.example.namazvakti.domain.policy.*
import com.example.namazvakti.domain.port.*
import com.example.namazvakti.ui.main.*
import com.example.namazvakti.widget.*
import com.example.namazvakti.widget.alarm.*
import com.example.namazvakti.widget.renderer.*
import com.example.namazvakti.widget.worker.*
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
