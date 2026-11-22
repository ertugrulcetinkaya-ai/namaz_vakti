package com.example.namazvakti.ui.main

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmSettingsTest {
    @Test
    fun opensExactAlarmSettingsFirst() {
        var fallbackOpened = false
        var toastShown = false

        openExactAlarmSettings(
            openRequestAccessSettings = {},
            openApplicationDetailsSettings = { fallbackOpened = true },
            showUnavailableToast = { toastShown = true }
        )

        assertFalse(fallbackOpened)
        assertFalse(toastShown)
    }

    @Test
    fun fallsBackToApplicationDetailsWhenExactAlarmActivityIsMissing() {
        val actions = mutableListOf<String>()

        openExactAlarmSettings(
            openRequestAccessSettings = {
                actions += "exact-alarm"
                throw ActivityNotFoundException("Exact alarm settings unavailable")
            },
            openApplicationDetailsSettings = { actions += "application-details" },
            showUnavailableToast = { actions += "toast" }
        )

        assertEquals(listOf("exact-alarm", "application-details"), actions)
    }

    @Test
    fun fallsBackToApplicationDetailsWhenExactAlarmLaunchIsDenied() {
        val actions = mutableListOf<String>()

        openExactAlarmSettings(
            openRequestAccessSettings = {
                actions += "exact-alarm"
                throw SecurityException("Exact alarm settings launch denied")
            },
            openApplicationDetailsSettings = { actions += "application-details" },
            showUnavailableToast = { actions += "toast" }
        )

        assertEquals(listOf("exact-alarm", "application-details"), actions)
    }

    @Test
    fun showsToastWhenApplicationDetailsActivityIsUnavailable() {
        val actions = mutableListOf<String>()

        openExactAlarmSettings(
            openRequestAccessSettings = {
                actions += "exact-alarm"
                throw ActivityNotFoundException("Exact alarm settings unavailable")
            },
            openApplicationDetailsSettings = {
                actions += "application-details"
                throw ActivityNotFoundException("Application settings unavailable")
            },
            showUnavailableToast = { actions += "toast" }
        )

        assertEquals(listOf("exact-alarm", "application-details", "toast"), actions)
    }

    @Test
    fun showsToastWhenApplicationDetailsLaunchIsDenied() {
        var toastShown = false

        openExactAlarmSettings(
            openRequestAccessSettings = { throw SecurityException("Exact alarm settings launch denied") },
            openApplicationDetailsSettings = { throw SecurityException("Application settings launch denied") },
            showUnavailableToast = { toastShown = true }
        )

        assertTrue(toastShown)
    }
}
