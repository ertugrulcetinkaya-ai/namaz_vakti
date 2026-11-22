package com.example.namazvakti.ui.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.example.namazvakti.R
import com.example.namazvakti.widget.alarm.PrayerExactAlarmAccess

internal fun openExactAlarmSettings(context: Context) {
    openExactAlarmSettings(
        openRequestAccessSettings = {
            context.startActivity(PrayerExactAlarmAccess.requestAccessIntent(context))
        },
        openApplicationDetailsSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            )
        },
        showUnavailableToast = {
            Toast.makeText(context, R.string.exact_alarm_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    )
}

internal fun openExactAlarmSettings(
    openRequestAccessSettings: () -> Unit,
    openApplicationDetailsSettings: () -> Unit,
    showUnavailableToast: () -> Unit
) {
    try {
        openRequestAccessSettings()
    } catch (_: ActivityNotFoundException) {
        openApplicationDetailsSettingsOrToast(openApplicationDetailsSettings, showUnavailableToast)
    } catch (_: SecurityException) {
        openApplicationDetailsSettingsOrToast(openApplicationDetailsSettings, showUnavailableToast)
    }
}

private fun openApplicationDetailsSettingsOrToast(
    openApplicationDetailsSettings: () -> Unit,
    showUnavailableToast: () -> Unit
) {
    try {
        openApplicationDetailsSettings()
    } catch (_: ActivityNotFoundException) {
        showUnavailableToast()
    } catch (_: SecurityException) {
        showUnavailableToast()
    }
}
