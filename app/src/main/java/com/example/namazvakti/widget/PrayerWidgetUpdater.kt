package com.example.namazvakti.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object PrayerWidgetUpdater {
    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, PrayerWidgetProvider::class.java)
        val appWidgetIds = manager.getAppWidgetIds(component)
        if (appWidgetIds.isEmpty()) return
        val snapshot = PrayerWidgetSnapshotLoader.load(appContext)
        updateAll(appContext, manager, appWidgetIds, snapshot)
    }

    suspend fun updateAll(context: Context, snapshot: PrayerWidgetSnapshot) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, PrayerWidgetProvider::class.java)
        val appWidgetIds = manager.getAppWidgetIds(component)
        if (appWidgetIds.isEmpty()) return
        updateAll(appContext, manager, appWidgetIds, snapshot)
    }

    suspend fun updateAllAndReturnSnapshot(context: Context): PrayerWidgetSnapshot? {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, PrayerWidgetProvider::class.java)
        val appWidgetIds = manager.getAppWidgetIds(component)
        if (appWidgetIds.isEmpty()) return null
        val snapshot = PrayerWidgetSnapshotLoader.load(appContext)
        updateAll(appContext, manager, appWidgetIds, snapshot)
        return snapshot
    }

    private suspend fun updateAll(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: PrayerWidgetSnapshot
    ) {
        PrayerWidgetProvider.updateWidgets(context, manager, appWidgetIds, snapshot)
    }
}
