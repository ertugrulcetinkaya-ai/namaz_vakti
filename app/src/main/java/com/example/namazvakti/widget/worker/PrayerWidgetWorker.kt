package com.example.namazvakti.widget.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.domain.model.classifyPrayerError
import com.example.namazvakti.domain.model.classifyPrayerStorageError
import com.example.namazvakti.domain.model.code
import com.example.namazvakti.domain.policy.PrayerRetryPolicy
import com.example.namazvakti.domain.policy.PrayerWorkDecision
import com.example.namazvakti.widget.PrayerWidgetSnapshot
import com.example.namazvakti.widget.PrayerWidgetSnapshotLoader
import com.example.namazvakti.widget.PrayerWidgetUpdater
import com.example.namazvakti.widget.withCache
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import com.example.namazvakti.widget.renderer.PrayerWidgetDataState
import kotlinx.coroutines.CancellationException

/** Dependencies kept behind an adapter so the worker orchestration can be tested directly. */
internal interface PrayerWidgetWorkerDependencies {
    fun hasWidgets(): Boolean
    suspend fun cancelAll()
    suspend fun loadSnapshot(): PrayerWidgetSnapshot
    suspend fun refresh(): RefreshResult
    fun snapshotFor(cache: CachedPrayerDay?, current: PrayerWidgetSnapshot): PrayerWidgetSnapshot
    suspend fun render(snapshot: PrayerWidgetSnapshot)
    suspend fun scheduleBoundary(snapshot: PrayerWidgetSnapshot)
}

private class AndroidPrayerWidgetWorkerDependencies(
    context: Context
) : PrayerWidgetWorkerDependencies {
    private val appContext = context.applicationContext

    override fun hasWidgets(): Boolean = PrayerWidgetScheduler.hasWidgets(appContext)

    override suspend fun cancelAll() {
        PrayerWidgetScheduler.cancelAll(appContext)
    }

    override suspend fun loadSnapshot(): PrayerWidgetSnapshot =
        PrayerWidgetSnapshotLoader.load(appContext)

    override suspend fun refresh(): RefreshResult =
        appContext.appContainer().repository.refreshAndCache()

    override fun snapshotFor(
        cache: CachedPrayerDay?,
        current: PrayerWidgetSnapshot
    ): PrayerWidgetSnapshot {
        val container = appContext.appContainer()
        return current.withCache(
            cache = cache,
            settings = container.settings,
            cachePolicy = container.cachePolicy,
            now = container.timeProvider.now()
        )
    }

    override suspend fun render(snapshot: PrayerWidgetSnapshot) {
        PrayerWidgetUpdater.updateAll(appContext, snapshot)
    }

    override suspend fun scheduleBoundary(snapshot: PrayerWidgetSnapshot) {
        PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(
            appContext,
            snapshot = snapshot
        )
    }
}

class PrayerWidgetWorker : CoroutineWorker {
    private var dependencies: PrayerWidgetWorkerDependencies
    private val retryPolicy = PrayerRetryPolicy()

    constructor(context: Context, params: WorkerParameters) : super(context, params) {
        dependencies = AndroidPrayerWidgetWorkerDependencies(context)
    }

    internal constructor(
        context: Context,
        params: WorkerParameters,
        dependencies: PrayerWidgetWorkerDependencies
    ) : super(context, params) {
        this.dependencies = dependencies
    }

    internal fun setDependenciesForTesting(dependencies: PrayerWidgetWorkerDependencies) {
        this.dependencies = dependencies
    }

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
        if (!dependencies.hasWidgets()) {
            dependencies.cancelAll()
            return Result.success()
        }

        val snapshot = try {
            dependencies.loadSnapshot()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = classifyPrayerStorageError(e)
            Log.e(TAG, "widget storage read failed category=${error.code}", e)
            return retryOrFailure(error)
        }
        val fetch = inputData.getBoolean(INPUT_FETCH, true)
        val cacheStale = snapshot.cache == null || snapshot.dataState != PrayerWidgetDataState.Fresh

        Log.d(TAG, "worker mode fetch=$fetch")
        Log.d(TAG, "cachedDate=${snapshot.cache?.date}")
        Log.d(TAG, "cache stale=$cacheStale")

        val cache = if (fetch || cacheStale) {
            if (!fetch && cacheStale) {
                Log.d(TAG, "fetch=false upgraded to fetch because cache is stale")
            }
            when (val refreshed = dependencies.refresh()) {
                is RefreshResult.Success -> refreshed.cache
                is RefreshResult.StaleCache -> {
                    val staleSnapshot = dependencies.snapshotFor(refreshed.cache, snapshot)
                    Log.w(
                        TAG,
                        "using stale cache category=${refreshed.error.code}",
                        refreshed.cause
                    )
                    dependencies.render(staleSnapshot)
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
            snapshot.cache
        }

        val renderSnapshot = dependencies.snapshotFor(cache, snapshot)
        dependencies.render(renderSnapshot)
        dependencies.scheduleBoundary(renderSnapshot)
        Log.d(TAG, "rendered widget date=${cache.date}")
        return Result.success()
    }

    private fun retryOrFailure(error: PrayerError): Result {
        val output = workDataOf(OUTPUT_ERROR to error.code)
        return when (retryPolicy.decide(error, runAttemptCount)) {
            PrayerWorkDecision.Retry -> Result.retry()
            PrayerWorkDecision.Failure -> Result.failure(output)
        }
    }

    companion object {
        const val TAG = "NamazWidget"
        const val INPUT_FETCH = "fetch"
        const val OUTPUT_ERROR = "error"
    }
}
