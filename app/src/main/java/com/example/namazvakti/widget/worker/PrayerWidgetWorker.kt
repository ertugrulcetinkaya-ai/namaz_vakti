package com.example.namazvakti.widget.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.domain.model.classifyPrayerError
import com.example.namazvakti.domain.model.code
import com.example.namazvakti.domain.policy.PrayerRetryPolicy
import com.example.namazvakti.domain.policy.PrayerWorkDecision
import com.example.namazvakti.widget.PrayerWidgetProvider
import com.example.namazvakti.widget.PrayerWidgetSnapshotLoader
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import kotlinx.coroutines.CancellationException

class PrayerWidgetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val retryPolicy = PrayerRetryPolicy()

    override suspend fun doWork(): Result = try {
        performWork()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val error = classifyPrayerError(e)
        Log.e(TAG, "widget work failed unexpectedly category=${error.code}", e)
        retryOrFailure(error)
    }

    private suspend fun performWork(): Result {
        if (!PrayerWidgetScheduler.hasWidgets(applicationContext)) {
            PrayerWidgetScheduler.cancelAll(applicationContext)
            return Result.success()
        }
        val fetch = inputData.getBoolean(INPUT_FETCH, true)
        val container = applicationContext.appContainer()
        val repository = container.repository
        val locationAndCache = try {
            repository.selectedLocation() to repository.cachedWidget()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "widget storage read failed category=${PrayerError.Storage.code}", e)
            return retryOrFailure(PrayerError.Storage)
        }
        val location = locationAndCache.first
        val cached = locationAndCache.second
        val cacheStale = !container.cachePolicy.isFresh(
            cached, location, container.settings, container.timeProvider.now()
        )

        Log.d(TAG, "worker mode fetch=$fetch")
        Log.d(TAG, "cachedDate=${cached?.date}")
        Log.d(TAG, "cache stale=$cacheStale")

        val cache = if (fetch || cacheStale) {
            if (!fetch && cacheStale) {
                Log.d(TAG, "fetch=false upgraded to fetch because cache is stale")
            }
            when (val refreshed = repository.refreshAndCache()) {
                is RefreshResult.Success -> refreshed.cache
                is RefreshResult.StaleCache -> {
                    Log.w(
                        TAG,
                        "using stale cache category=${refreshed.error.code}",
                        refreshed.cause
                    )
                    renderWidgets(applicationContext)
                    return retryOrFailure(refreshed.error)
                }
                is RefreshResult.Failure -> {
                    Log.w(
                        TAG,
                        "refresh failed category=${refreshed.error.code}",
                        refreshed.cause
                    )
                    return retryOrFailure(refreshed.error)
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

    private fun retryOrFailure(error: PrayerError): Result {
        val output = workDataOf(OUTPUT_ERROR to error.code)
        return when (retryPolicy.decide(error, runAttemptCount)) {
            PrayerWorkDecision.Retry -> Result.retry()
            PrayerWorkDecision.Failure -> Result.failure(output)
        }
    }

    private suspend fun renderWidgets(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val componentName = ComponentName(appContext, PrayerWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) return
        val snapshot = PrayerWidgetSnapshotLoader.load(appContext)
        Log.d(TAG, "boundary rerender worker started")
        Log.d(TAG, "RemoteViews updateAll/updateAppWidget called count=${appWidgetIds.size}")
        PrayerWidgetProvider.updateWidgets(appContext, appWidgetManager, appWidgetIds, snapshot)
    }

    companion object {
        const val TAG = "NamazWidget"
        const val INPUT_FETCH = "fetch"
        const val OUTPUT_ERROR = "error"
    }
}
