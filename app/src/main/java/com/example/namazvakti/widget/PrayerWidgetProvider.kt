package com.example.namazvakti.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import com.example.namazvakti.widget.renderer.PrayerWidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val snapshot = PrayerWidgetSnapshotLoader.load(appContext)
                updateWidgets(appContext, manager, ids, snapshot)
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(
                    appContext,
                    snapshot = snapshot
                )
                PrayerWidgetScheduler.enqueueRefresh(appContext, snapshot = snapshot)
            } catch (e: Exception) {
                Log.e(TAG, "widget update failed", e)
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
            } catch (e: Exception) {
                Log.e(TAG, "widget enable setup failed", e)
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

        suspend fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: PrayerWidgetSnapshot
        ) {
            if (appWidgetIds.isEmpty()) return
            val appContext = context.applicationContext
            val views = renderer.render(appContext, snapshot)
            manager.updateAppWidget(appWidgetIds, views)
            Log.d(
                TAG,
                "rendered widget count=${appWidgetIds.size} cacheDate=${snapshot.cache?.date}"
            )
        }
    }
}
