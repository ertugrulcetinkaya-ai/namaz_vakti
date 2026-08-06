package com.example.namazvakti

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.util.concurrent.TimeUnit

object PrayerWidgetScheduler {
    private const val API_REFRESH_WORK_NAME = "prayer_times_refresh_api"
    private const val ONE_TIME_WORK_NAME = "prayer_times_refresh_now"
    private const val NEXT_BOUNDARY_WORK_NAME = "prayer_widget_next_boundary_rerender"
    private const val TAG = "NamazWidget"

    suspend fun enqueueRefresh(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        if (!hasWidgets(appContext)) {
            cancelAll(appContext)
            return
        }
        val container = appContext.appContainer()
        val location = container.store.readLocation()
        val cached = container.store.readCache()
        val needsRefresh = force || cached == null || !cached.matches(
            container.timeProvider.today(), location, container.settings
        )
        val workManager = WorkManager.getInstance(appContext)
        if (needsRefresh) {
            val oneTime = OneTimeWorkRequestBuilder<PrayerWidgetWorker>()
                .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to true))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(networkConstraints())
                .build()
            workManager.enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                oneTime
            )
        }

        val dailySafetyRefresh = PeriodicWorkRequestBuilder<PrayerWidgetWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to true))
            .setConstraints(networkConstraints())
            .build()
        workManager.enqueueUniquePeriodicWork(
            API_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dailySafetyRefresh
        )
    }

    suspend fun scheduleNextPrayerBoundaryRerender(context: Context, cache: CachedPrayerDay? = null) {
        val appContext = context.applicationContext
        if (!hasWidgets(appContext)) {
            cancelAll(appContext)
            return
        }
        val container = appContext.appContainer()
        val currentCache = cache ?: container.store.readCache() ?: return
        val now = container.timeProvider.now().withZoneSameInstant(currentCache.timezone)
        val target = PrayerBoundaryCalculator.calculateNextBoundary(currentCache, now)
        val delay = Duration.between(now, target).plusSeconds(45).let {
            if (it < Duration.ofMinutes(1)) Duration.ofMinutes(1) else it
        }
        val request = OneTimeWorkRequestBuilder<PrayerWidgetWorker>()
            .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to false))
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            NEXT_BOUNDARY_WORK_NAME, ExistingWorkPolicy.REPLACE, request
        )
        Log.d(TAG, "next boundary scheduled at=$target")
    }

    fun cancelAll(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        listOf(ONE_TIME_WORK_NAME, API_REFRESH_WORK_NAME, NEXT_BOUNDARY_WORK_NAME)
            .forEach(manager::cancelUniqueWork)
    }

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context.applicationContext)
        val component = ComponentName(context, PrayerWidgetProvider::class.java)
        return manager.getAppWidgetIds(component).isNotEmpty()
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
