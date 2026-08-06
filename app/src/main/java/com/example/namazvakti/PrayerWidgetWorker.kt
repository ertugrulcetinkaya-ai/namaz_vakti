package com.example.namazvakti

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PrayerWidgetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!PrayerWidgetScheduler.hasWidgets(applicationContext)) {
            PrayerWidgetScheduler.cancelAll(applicationContext)
            return Result.success()
        }
        val fetch = inputData.getBoolean(INPUT_FETCH, true)
        val container = applicationContext.appContainer()
        val repository = container.repository
        val today = container.timeProvider.today()
        val location = repository.selectedLocation()
        val cached = repository.cachedWidget()
        val cacheStale = cached == null || !cached.matches(today, location, container.settings)

        Log.d(TAG, "worker mode fetch=$fetch")
        Log.d(TAG, "cachedDate=${cached?.date}")
        Log.d(TAG, "today=$today")
        Log.d(TAG, "cache stale=$cacheStale")

        val cache = if (fetch || cacheStale) {
            if (!fetch && cacheStale) {
                Log.d(TAG, "fetch=false upgraded to fetch because cache is stale")
            }
            when (val refreshed = repository.refreshAndCache()) {
                is RefreshResult.Success -> refreshed.cache
                is RefreshResult.StaleCache -> {
                    Log.w(TAG, "using stale cache; scheduling retry", refreshed.cause)
                    renderWidgets(applicationContext)
                    return Result.retry()
                }
                is RefreshResult.Failure -> {
                    Log.w(TAG, "refresh failed; scheduling retry", refreshed.cause)
                    return Result.retry()
                }
            }
        } else {
            Log.d(TAG, "rerender-only worker using cached widget state")
            cached
        }
        renderWidgets(applicationContext)
        if (cache != null) PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(applicationContext, cache)
        Log.d(TAG, "rendered widget date=${cache?.date}")
        return Result.success()
    }

    private suspend fun renderWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, PrayerWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        Log.d(TAG, "boundary rerender worker started")
        Log.d(TAG, "RemoteViews updateAll/updateAppWidget called count=${appWidgetIds.size}")
        for (appWidgetId in appWidgetIds) {
            PrayerWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val TAG = "NamazWidget"
        const val INPUT_FETCH = "fetch"
    }
}
