package com.example.namazvakti

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.ZoneId

class PrayerWidgetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val fetch = inputData.getBoolean(INPUT_FETCH, true)
        val repository = PrayerTimesRepository(store = PrayerTimesStore(applicationContext))
        val today = LocalDate.now(ZoneId.of("Europe/Istanbul")).toString()
        val cached = repository.cachedWidget()
        val cachedDate = cached?.date
        val cacheStale = cachedDate.isNullOrBlank() || cachedDate < today

        Log.d(TAG, "worker mode fetch=$fetch")
        Log.d(TAG, "cachedDate=$cachedDate")
        Log.d(TAG, "today=$today")
        Log.d(TAG, "cache stale=$cacheStale")

        val cache = if (fetch || cacheStale) {
            if (!fetch && cacheStale) {
                Log.d(TAG, "fetch=false upgraded to fetch because cache is stale")
            }
            val refreshed = repository.refreshAndCache()
            Log.d(TAG, "refreshed date=${refreshed.date}")
            Log.d(TAG, "formatted widget text value=${refreshed.widgetText}")
            Log.d(TAG, "formatted hijri text value=${refreshed.hijriText}")
            refreshed
        } else {
            Log.d(TAG, "rerender-only worker using cached widget state")
            cached ?: PrayerWidgetCache(
                date = today,
                widgetText = "Vakitler alınamadı",
                hijriText = null
            )
        }
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val componentName = ComponentName(applicationContext, PrayerWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        Log.d(TAG, "boundary rerender worker started fetch=$fetch")
        Log.d(TAG, "RemoteViews updateAll/updateAppWidget called count=${appWidgetIds.size}")
        for (appWidgetId in appWidgetIds) {
            PrayerWidgetProvider.updateWidget(applicationContext, appWidgetManager, appWidgetId)
        }
        PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(applicationContext, cache)
        Log.d(TAG, "rendered widget date=${cache.date}")
        return Result.success()
    }

    companion object {
        const val TAG = "NamazWidget"
        const val INPUT_FETCH = "fetch"
    }
}
