package com.example.namazvakti.widget.alarm

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.policy.PrayerBoundaryCalculator
import com.example.namazvakti.widget.PrayerWidgetProvider
import com.example.namazvakti.widget.PrayerWidgetSnapshot
import com.example.namazvakti.widget.worker.PrayerWorkRequestFactory

object PrayerWidgetScheduler {
    private const val API_REFRESH_WORK_NAME = "prayer_times_refresh_api"
    private const val ONE_TIME_WORK_NAME = "prayer_times_refresh_now"
    private const val LEGACY_BOUNDARY_WORK_NAME = "prayer_widget_next_boundary_rerender"
    private const val TAG = "NamazWidget"
    private val requestFactory = PrayerWorkRequestFactory()

    suspend fun enqueueRefresh(
        context: Context,
        force: Boolean = false,
        snapshot: PrayerWidgetSnapshot? = null
    ) {
        val appContext = context.applicationContext
        if (!hasWidgets(appContext)) {
            cancelAll(appContext)
            return
        }
        val container = appContext.appContainer()
        val location = snapshot?.location ?: container.store.readLocation()
        val cached = if (snapshot != null) snapshot.cache else container.store.readCache()
        val needsRefresh = force || !container.cachePolicy.isFresh(
            cached,
            location,
            container.settings,
            snapshot?.now ?: container.timeProvider.now()
        )
        val workManager = WorkManager.getInstance(appContext)
        if (needsRefresh) {
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

    suspend fun scheduleNextPrayerBoundaryRerender(
        context: Context,
        cache: CachedPrayerDay? = null,
        snapshot: PrayerWidgetSnapshot? = null
    ) {
        val appContext = context.applicationContext
        if (!hasWidgets(appContext)) {
            cancelAll(appContext)
            return
        }
        val container = appContext.appContainer()
        val location = snapshot?.location ?: container.store.readLocation()
        val now = (snapshot?.now ?: container.timeProvider.now())
            .withZoneSameInstant(location.timezone)
        val today = now.toLocalDate()
        val snapshotCache = snapshot?.cache
        val currentCache = (cache ?: snapshotCache)
            ?.takeIf { it.matches(today, location, container.settings) }
            ?: if (snapshot == null && cache == null) {
                container.store.readCache(today, location, container.settings)
            } else {
                null
            }
        if (currentCache == null) {
            PrayerBoundaryAlarm.cancel(appContext)
            return
        }
        val tomorrow = today.plusDays(1)
        val nextDayCache = if (currentCache.prayerTimes.nextPrayerBoundary(now.toLocalTime()) == null) {
            container.store.readCache(tomorrow, location, container.settings)
        } else {
            null
        }
        val target = PrayerBoundaryCalculator.calculateNextBoundary(
            currentCache,
            now,
            nextDayCache
        )
        WorkManager.getInstance(appContext).cancelUniqueWork(LEGACY_BOUNDARY_WORK_NAME)
        PrayerBoundaryAlarm.schedule(appContext, target)
        Log.d(TAG, "next prayer boundary scheduled at=$target")
    }

    fun cancelAll(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        listOf(ONE_TIME_WORK_NAME, API_REFRESH_WORK_NAME, LEGACY_BOUNDARY_WORK_NAME)
            .forEach(manager::cancelUniqueWork)
        PrayerBoundaryAlarm.cancel(context.applicationContext)
    }

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context.applicationContext)
        val component = ComponentName(context, PrayerWidgetProvider::class.java)
        return manager.getAppWidgetIds(component).isNotEmpty()
    }

}
