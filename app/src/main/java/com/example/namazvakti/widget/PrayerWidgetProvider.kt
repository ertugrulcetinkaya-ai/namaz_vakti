package com.example.namazvakti.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import com.example.namazvakti.widget.renderer.PrayerWidgetRenderer

class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        launchAsync(context, TAG, "widget update failed") {
            val appContext = context.applicationContext
            val snapshot = PrayerWidgetSnapshotLoader.load(appContext)
            updateWidgets(appContext, manager, ids, snapshot)
            PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(
                appContext,
                snapshot = snapshot
            )
            PrayerWidgetScheduler.enqueueRefresh(appContext, snapshot = snapshot)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        launchAsync(context, TAG, "widget resize update failed") {
            val appContext = context.applicationContext
            updateWidgets(
                appContext,
                manager,
                intArrayOf(appWidgetId),
                PrayerWidgetSnapshotLoader.load(appContext)
            )
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        launchAsync(context, TAG, "widget enable setup failed") {
            PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(context.applicationContext)
            PrayerWidgetScheduler.enqueueRefresh(context.applicationContext)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        launchAsync(context, TAG, "widget disable cleanup failed") {
            PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(context.applicationContext)
            PrayerWidgetScheduler.enqueueRefresh(context.applicationContext)
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
                    val options = manager.getAppWidgetOptions(appWidgetId)
                    val sizes = options.getAppWidgetSizes()
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf(
                            SizeF(
                                maxOf(
                                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                                ).toFloat(),
                                maxOf(
                                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                                ).toFloat()
                            )
                        )
                    val responsiveViews = sizes.associateWith { size ->
                        renderer.render(
                            appContext,
                            snapshot,
                            PrayerWidgetSizeClass.from(size.width.toInt(), size.height.toInt()),
                            widthDp = size.width.toInt()
                        )
                    }
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
                        renderer.render(appContext, snapshot, sizeClass, widthDp = widthDp)
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

@RequiresApi(Build.VERSION_CODES.S)
@Suppress("DEPRECATION")
private fun Bundle.getAppWidgetSizes(): ArrayList<SizeF>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
    } else {
        getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
    }
