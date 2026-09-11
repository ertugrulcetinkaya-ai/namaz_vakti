package com.example.namazvakti.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.namazvakti.domain.port.PrayerWidgetUpdatePort

object PrayerWidgetUpdater : PrayerWidgetUpdatePort {
    override suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, PrayerWidgetProvider::class.java)
        val appWidgetIds = manager.getAppWidgetIds(component)
        if (appWidgetIds.isEmpty()) return
        val snapshot = PrayerWidgetSnapshotLoader.load(appContext)
        PrayerWidgetProvider.updateWidgets(appContext, manager, appWidgetIds, snapshot)
    }
}
