package com.example.namazvakti.widget.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import java.time.ZonedDateTime

internal enum class PrayerAlarmPrecision { Exact, Inexact }

internal object PrayerAlarmPolicy {
    fun precision(sdkInt: Int, canScheduleExactAlarms: Boolean): PrayerAlarmPrecision =
        if (sdkInt < Build.VERSION_CODES.S || canScheduleExactAlarms) {
            PrayerAlarmPrecision.Exact
        } else {
            PrayerAlarmPrecision.Inexact
        }
}

object PrayerExactAlarmAccess {
    fun requiresUserAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (!requiresUserAccess()) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun requestAccessIntent(context: Context): Intent =
        Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:${context.packageName}".toUri()
        }

    private const val ACTION_REQUEST_SCHEDULE_EXACT_ALARM =
        "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"
}

internal object PrayerBoundaryAlarm {
    private const val REQUEST_CODE = 6102
    private const val TAG = "NamazWidget"

    fun schedule(context: Context, target: ZonedDateTime) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(appContext)
        val exactAllowed = PrayerExactAlarmAccess.canScheduleExactAlarms(appContext)
        val precision = PrayerAlarmPolicy.precision(Build.VERSION.SDK_INT, exactAllowed)

        alarmManager.cancel(operation)
        if (precision == PrayerAlarmPrecision.Exact) {
            scheduleExactOrFallback(alarmManager, target, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.toInstant().toEpochMilli(),
                operation
            )
        }
        Log.d(TAG, "next boundary alarm scheduled at=$target precision=$precision")
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntent(appContext))
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleExactOrFallback(
        alarmManager: AlarmManager,
        target: ZonedDateTime,
        operation: PendingIntent
    ) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.toInstant().toEpochMilli(),
                operation
            )
        } catch (exception: SecurityException) {
            Log.w(TAG, "exact alarm access changed; using inexact alarm", exception)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.toInstant().toEpochMilli(),
                operation
            )
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PrayerWidgetAlarmReceiver::class.java).setAction(
            PrayerWidgetAlarmReceiver.ACTION_PRAYER_BOUNDARY
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
