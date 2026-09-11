package com.example.namazvakti.ui.main

import android.app.Activity
import android.view.View
import com.example.namazvakti.widget.alarm.PrayerExactAlarmAccess

class PrayerExactAlarmController(
    private val activity: Activity
) {
    fun render(infoView: View, requestButton: View) {
        val accessNeeded = PrayerExactAlarmAccess.requiresUserAccess() &&
            !PrayerExactAlarmAccess.canScheduleExactAlarms(activity)
        infoView.visibility = if (accessNeeded) View.VISIBLE else View.GONE
        requestButton.visibility = if (accessNeeded) View.VISIBLE else View.GONE
    }

    fun requestAccess() {
        activity.startActivity(PrayerExactAlarmAccess.requestAccessIntent(activity))
    }
}
