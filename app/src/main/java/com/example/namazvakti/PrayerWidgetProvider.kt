package com.example.namazvakti

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ids.forEach { updateWidget(context, manager, it) }
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(context)
                PrayerWidgetScheduler.enqueueRefresh(context)
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
                PrayerWidgetScheduler.enqueueRefresh(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        PrayerWidgetScheduler.cancelWork(context)
    }

    companion object {
        private const val TAG = "NamazWidget"
        private const val FALLBACK_TEXT = "Vakitler alınamadı"
        private const val NORMAL_COLOR = "#FFFFFFFF"
        private const val ACTIVE_COLOR = "#00FF00"
        private val ITEM_IDS = intArrayOf(
            R.id.prayer_widget_item_1,
            R.id.prayer_widget_item_2,
            R.id.prayer_widget_item_3,
            R.id.prayer_widget_item_4,
            R.id.prayer_widget_item_5,
            R.id.prayer_widget_item_6
        )

        private fun renderFallback(views: RemoteViews) {
            views.setTextViewText(R.id.prayer_widget_item_1, FALLBACK_TEXT)
            ITEM_IDS.drop(1).forEach { views.setTextViewText(it, "") }
        }

        suspend fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val store = PrayerTimesStore(context)
            val cache = store.readCache()
            val location = store.readLocation()
            val now = PrayerTimeProvider().now()
            val views = RemoteViews(context.packageName, R.layout.prayer_widget)
            views.setOnClickPendingIntent(
                R.id.prayer_widget_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setTextViewText(R.id.prayer_widget_city, buildTopRowText(location.displayCity, cache?.hijriText))

            val items = cache?.prayerTimes?.toHighlightedDisplayItems(now.toLocalTime())
            if (items == null) {
                renderFallback(views)
            } else {
                val normalColor = Color.parseColor(NORMAL_COLOR)
                val activeColor = Color.parseColor(ACTIVE_COLOR)
                items.forEachIndexed { index, item ->
                    val viewId = ITEM_IDS[index]
                    views.setTextViewText(viewId, item.render())
                    views.setTextColor(viewId, if (item.highlighted) activeColor else normalColor)
                }
            }
            Log.d(TAG, "rendered widget id=$appWidgetId cacheDate=${cache?.date}")
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun buildTopRowText(cityText: String, hijriText: String?): String {
            val cleanedHijri = hijriText?.trim().orEmpty()
            return if (cleanedHijri.isBlank()) cityText else "$cityText • $cleanedHijri"
        }
    }
}

object PrayerWidgetScheduler {
    private const val API_REFRESH_WORK_NAME = "prayer_times_refresh_api"
    private const val RERENDER_WORK_NAME = "prayer_times_refresh_rerender"
    private const val ONE_TIME_WORK_NAME = "prayer_times_refresh_now"
    private const val NEXT_BOUNDARY_WORK_NAME = "prayer_widget_next_boundary_rerender"
    private const val TAG = "NamazWidget"

    suspend fun enqueueRefresh(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val store = PrayerTimesStore(appContext)
        val location = store.readLocation()
        val cached = store.readCache()
        val needsRefresh = force || cached == null || !cached.matches(
            PrayerTimeProvider().today(), location, PrayerCalculationSettings()
        )

        if (needsRefresh) {
            val oneTime = OneTimeWorkRequestBuilder<PrayerWidgetWorker>()
                .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to true))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.REPLACE, oneTime)
        }

        val apiPeriodic = PeriodicWorkRequestBuilder<PrayerWidgetWorker>(6, TimeUnit.HOURS)
            .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(API_REFRESH_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, apiPeriodic)

        val rerenderPeriodic = PeriodicWorkRequestBuilder<PrayerWidgetWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to false))
            .build()
        workManager.enqueueUniquePeriodicWork(RERENDER_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, rerenderPeriodic)
    }

    suspend fun scheduleNextPrayerBoundaryRerender(context: Context, cache: CachedPrayerDay? = null) {
        val appContext = context.applicationContext
        val currentCache = cache ?: PrayerTimesStore(appContext).readCache() ?: return
        val zone = currentCache.timezone
        val now = PrayerTimeProvider().now().withZoneSameInstant(zone)
        val target = PrayerBoundaryCalculator.calculateNextBoundary(currentCache, now)
        val computedDelay = Duration.between(now, target).plusSeconds(45)
        val delay = if (computedDelay < Duration.ofMinutes(1)) Duration.ofMinutes(1) else computedDelay

        val oneTime = OneTimeWorkRequestBuilder<PrayerWidgetWorker>()
            .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to false))
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            NEXT_BOUNDARY_WORK_NAME, ExistingWorkPolicy.REPLACE, oneTime
        )
        Log.d(TAG, "next boundary scheduled at=$target")
    }

    fun cancelWork(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        listOf(ONE_TIME_WORK_NAME, API_REFRESH_WORK_NAME, RERENDER_WORK_NAME, NEXT_BOUNDARY_WORK_NAME)
            .forEach(workManager::cancelUniqueWork)
    }
}
