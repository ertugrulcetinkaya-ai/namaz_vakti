package com.example.namazvakti

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object PrayerWidgetUpdater {
    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, PrayerWidgetProvider::class.java)
        manager.getAppWidgetIds(component).forEach { id ->
            PrayerWidgetProvider.updateWidget(appContext, manager, id)
        }
    }
}
