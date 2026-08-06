package com.example.namazvakti

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.time.Duration

object PrayerWidgetScheduler {
    private const val API_REFRESH_WORK_NAME = "prayer_times_refresh_api"
    private const val ONE_TIME_WORK_NAME = "prayer_times_refresh_now"
    private const val NEXT_BOUNDARY_WORK_NAME = "prayer_widget_next_boundary_rerender"
    private const val TAG = "NamazWidget"
    private val requestFactory = PrayerWorkRequestFactory()

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
            if (force) workManager.cancelUniqueWork(NEXT_BOUNDARY_WORK_NAME)
            workManager.enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                requestFactory.immediateRefresh()
            )
        }

        workManager.enqueueUniquePeriodicWork(
            API_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            requestFactory.dailySafetyRefresh()
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
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            NEXT_BOUNDARY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            requestFactory.boundaryRerender(delay)
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

}
