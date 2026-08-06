package com.example.namazvakti

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                ids.forEach { updateWidget(appContext, manager, it) }
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(appContext)
                PrayerWidgetScheduler.enqueueRefresh(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(context.applicationContext)
                PrayerWidgetScheduler.enqueueRefresh(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        PrayerWidgetScheduler.cancelAll(context)
    }

    companion object {
        private const val TAG = "NamazWidget"
        private val renderer = PrayerWidgetRenderer()

        suspend fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val appContext = context.applicationContext
            val container = appContext.appContainer()
            val cache = container.store.readCache()
            val location = container.store.readLocation()
            val views = renderer.render(appContext, cache, location, container.timeProvider.now())
            manager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "rendered widget id=$appWidgetId cacheDate=${cache?.date}")
        }
    }
}
