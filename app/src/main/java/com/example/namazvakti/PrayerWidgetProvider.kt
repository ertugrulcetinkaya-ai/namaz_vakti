package com.example.namazvakti

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import androidx.work.workDataOf
import androidx.work.BackoffPolicy

class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        Log.d(TAG, "PrayerWidgetProvider.onUpdate called")
        Log.d(TAG, "appWidgetIds count=${appWidgetIds.size}")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(context)
                PrayerWidgetScheduler.enqueueRefresh(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "PrayerWidgetProvider.onEnabled called")
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

        private fun renderFallback(views: RemoteViews) {
            views.setTextViewText(R.id.prayer_widget_item_1, FALLBACK_TEXT)
            views.setTextViewText(R.id.prayer_widget_item_2, "")
            views.setTextViewText(R.id.prayer_widget_item_3, "")
            views.setTextViewText(R.id.prayer_widget_item_4, "")
            views.setTextViewText(R.id.prayer_widget_item_5, "")
            views.setTextViewText(R.id.prayer_widget_item_6, "")
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val store = PrayerTimesStore(context)
            val cached = runCatching { store.read() }.getOrNull()
            val today = LocalDate.now(ZoneId.of("Europe/Istanbul")).toString()
            val cachedText = cached?.widgetText
            val cachedDate = cached?.date
            val selectedCity = PrayerLocationConfig.optionForCityAndCountry(
                store.getSelectedCity(),
                store.getSelectedCountry()
            )
            val cityText = selectedCity.displayCity
            val topRowText = buildTopRowText(cityText, cached?.hijriText)

            Log.d(TAG, "rendered widget date cacheDate=$cachedDate today=$today")
            Log.d(TAG, "cached widget text read result=$cachedText")
            Log.d(TAG, "cached widget date read result=$cachedDate")
            Log.d(TAG, "selected city result=$cityText")

            val widgetText = when {
                cachedText.isNullOrBlank() -> {
                    Log.d(TAG, "fallback text used=$FALLBACK_TEXT")
                    PrayerWidgetScheduler.enqueueRefresh(context)
                    FALLBACK_TEXT
                }
                cachedDate.isNullOrBlank() || cachedDate < today -> {
                    Log.d(TAG, "cache date missing or stale; enqueue refresh")
                    PrayerWidgetScheduler.enqueueRefresh(context)
                    cachedText
                }
                else -> cachedText
            }

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
            views.setTextViewText(R.id.prayer_widget_city, topRowText)

            val parsedPrayerTimes = parsePrayerTimesText(widgetText)
            val items = parsedPrayerTimes?.toHighlightedDisplayItems()
            if (items.isNullOrEmpty()) {
                renderFallback(views)
            } else {
                val normalColor = Color.parseColor(NORMAL_COLOR)
                val activeColor = Color.parseColor(ACTIVE_COLOR)
                items.forEach { item ->
                    views.setTextViewText(item.viewId, item.value)
                    views.setTextColor(item.viewId, normalColor)
                    Log.d(TAG, "applying normal color viewId=${item.viewId} color=$NORMAL_COLOR")
                }
                items.firstOrNull { it.highlighted }?.let { active ->
                    views.setTextColor(active.viewId, activeColor)
                    Log.d(TAG, "highlighted item=${active.value}")
                    Log.d(TAG, "applying active color viewId=${active.viewId} color=$ACTIVE_COLOR")
                }
            }

            Log.d(TAG, "RemoteViews updateAppWidget called appWidgetId=$appWidgetId city=$cityText text=$widgetText")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildTopRowText(cityText: String, hijriText: String?): String {
            val cleanedHijri = hijriText?.trim().orEmpty()
            return if (cleanedHijri.isBlank()) {
                cityText
            } else {
                "$cityText • $cleanedHijri"
            }
        }
    }
}

object PrayerWidgetScheduler {
    private const val API_REFRESH_WORK_NAME = "prayer_times_refresh_api"
    private const val RERENDER_WORK_NAME = "prayer_times_refresh_rerender"
    private const val ONE_TIME_WORK_NAME = "prayer_times_refresh_now"
    private const val NEXT_BOUNDARY_WORK_NAME = "prayer_widget_next_boundary_rerender"

    fun enqueueRefresh(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).cancelUniqueWork("prayer_times_refresh")
        val needsRefresh = if (force) {
            true
        } else {
            val store = PrayerTimesStore(appContext)
            val cached = runCatching { store.read() }.getOrNull()
            val today = LocalDate.now(ZoneId.of("Europe/Istanbul")).toString()
            val cachedDate = cached?.date
            cachedDate.isNullOrBlank() || cachedDate < today
        }

        if (needsRefresh) {
            val oneTime = OneTimeWorkRequestBuilder<PrayerWidgetWorker>()
                .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to true))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTime
            )
            Log.d(TAG, "worker enqueue result=one_time_enqueued force=$force")
        } else {
            Log.d(TAG, "worker enqueue result=skipped_cached_text_present")
        }

        val apiPeriodic = PeriodicWorkRequestBuilder<PrayerWidgetWorker>(6, TimeUnit.HOURS)
            .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to true))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            API_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            apiPeriodic
        )
        Log.d(TAG, "worker enqueue result=periodic_api_enqueued")

        val rerenderPeriodic = PeriodicWorkRequestBuilder<PrayerWidgetWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to false))
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            RERENDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            rerenderPeriodic
        )
        Log.d(TAG, "worker enqueue result=periodic_rerender_enqueued")
    }

    fun scheduleNextPrayerBoundaryRerender(context: Context, cache: PrayerWidgetCache? = null) {
        val appContext = context.applicationContext
        val store = PrayerTimesStore(appContext)
        val currentCache = cache ?: runCatching { store.read() }.getOrNull()

        if (currentCache == null) {
            Log.d(TAG, "scheduling next boundary rerender skipped cache missing")
            return
        }

        val prayerTimes = parsePrayerTimesText(currentCache.widgetText)
        if (prayerTimes == null) {
            Log.d(TAG, "scheduling next boundary rerender skipped invalid cached prayer text")
            return
        }

        val zone = ZoneId.of("Europe/Istanbul")
        val now = LocalDateTime.now(zone)
        val boundary = prayerTimes.nextPrayerBoundary(now.toLocalTime())
        val delay = when {
            boundary != null -> {
                val boundaryDateTime = now.toLocalDate().atTime(boundary.time)
                val targetTime = if (boundaryDateTime.isAfter(now)) boundaryDateTime else boundaryDateTime.plusDays(1)
                val computedDelay = Duration.between(now, targetTime).plusSeconds(45)
                if (computedDelay.isNegative || computedDelay.isZero) {
                    Duration.ofMinutes(1)
                } else {
                    computedDelay
                }
            }
            else -> Duration.ofMinutes(30)
        }

        val nextBoundaryName = boundary?.name ?: "İmsak"
        val nextBoundaryTime = boundary?.time?.toString() ?: "unavailable"
        Log.d(
            TAG,
            "scheduling next boundary rerender now=$now nextBoundaryName=$nextBoundaryName nextBoundaryTime=$nextBoundaryTime delayMinutes=${delay.toMinutes()}"
        )

        val oneTime = OneTimeWorkRequestBuilder<PrayerWidgetWorker>()
            .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to false))
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            NEXT_BOUNDARY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTime
        )
        Log.d(TAG, "one-time boundary rerender enqueued workName=$NEXT_BOUNDARY_WORK_NAME")
    }

    fun cancelWork(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        workManager.cancelUniqueWork(API_REFRESH_WORK_NAME)
        workManager.cancelUniqueWork(RERENDER_WORK_NAME)
        workManager.cancelUniqueWork(NEXT_BOUNDARY_WORK_NAME)
        workManager.cancelUniqueWork("prayer_times_refresh")
    }

    private const val TAG = "NamazWidget"
}
