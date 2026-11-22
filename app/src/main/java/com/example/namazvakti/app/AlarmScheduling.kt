package com.example.namazvakti.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log

internal object AlarmScheduling {
    private const val TAG = "AlarmScheduling"

    @SuppressLint("ScheduleExactAlarm")
    fun set(
        context: Context,
        triggerAtMillis: Long,
        operation: PendingIntent,
        exact: Boolean
    ): Boolean {
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
        return set(
            exact = exact,
            setExact = {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            },
            setInexact = {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            },
            logWarning = { exception ->
                Log.w(TAG, "exact alarm access changed; using inexact alarm", exception)
            }
        )
    }

    internal fun set(
        exact: Boolean,
        setExact: () -> Unit,
        setInexact: () -> Unit,
        logWarning: (SecurityException) -> Unit
    ): Boolean {
        if (!exact) {
            setInexact()
            return false
        }

        return try {
            setExact()
            true
        } catch (exception: SecurityException) {
            logWarning(exception)
            setInexact()
            false
        }
    }
}
