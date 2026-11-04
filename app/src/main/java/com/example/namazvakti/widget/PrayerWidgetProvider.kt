package com.example.namazvakti.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import com.example.namazvakti.widget.renderer.PrayerWidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "widget update failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                updateWidgets(
                    appContext,
                    manager,
                    intArrayOf(appWidgetId),
                    PrayerWidgetSnapshotLoader.load(appContext)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "widget resize update failed", e)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "widget enable setup failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(context.applicationContext)
                PrayerWidgetScheduler.enqueueRefresh(context.applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "widget disable cleanup failed", e)
            } finally {
                pendingResult.finish()
            }
        }
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
            appWidgetIds.forEach { appWidgetId ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val responsiveViews = mapOf(
                        SizeF(110f, 60f) to renderer.render(
                            appContext, snapshot, PrayerWidgetSizeClass.SMALL
                        ),
                        SizeF(250f, 80f) to renderer.render(
                            appContext, snapshot, PrayerWidgetSizeClass.MEDIUM
                        ),
                        SizeF(250f, 140f) to renderer.render(
                            appContext, snapshot, PrayerWidgetSizeClass.LARGE
                        )
                    )
                    manager.updateAppWidget(appWidgetId, RemoteViews(responsiveViews))
                } else {
                    val options = manager.getAppWidgetOptions(appWidgetId)
                    val widthDp = maxOf(
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                    )
                    val heightDp = maxOf(
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    )
                    val sizeClass = PrayerWidgetSizeClass.from(widthDp, heightDp)
                    manager.updateAppWidget(
                        appWidgetId,
                        renderer.render(appContext, snapshot, sizeClass)
                    )
                }
            }
            Log.d(
                TAG,
                "rendered widget count=${appWidgetIds.size} cacheDate=${snapshot.cache?.date}"
            )
        }
    }
}
