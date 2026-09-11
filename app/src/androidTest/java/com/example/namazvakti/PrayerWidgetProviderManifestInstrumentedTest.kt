package com.example.namazvakti

import com.example.namazvakti.widget.PrayerWidgetProvider
import com.example.namazvakti.widget.alarm.PrayerWidgetSystemReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerWidgetProviderManifestInstrumentedTest {
    @Test
    fun widgetProviderIsNotExported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, PrayerWidgetProvider::class.java),
            0
        )

        assertFalse(receiverInfo.exported)
    }

    @Test
    fun applicationBackupIsDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test
    fun systemReceiverIsRegisteredForRebootClockTimezoneAndAlarmPermissionEvents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )

        actions.forEach { action ->
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                PackageManager.GET_RESOLVED_FILTER
            )
            assertTrue(
                "System receiver is not registered for $action",
                receivers.any { it.activityInfo.name == PrayerWidgetSystemReceiver::class.java.name }
            )
        }
    }
}
