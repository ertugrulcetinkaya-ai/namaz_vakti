package com.example.namazvakti.widget.alarm

import android.content.Context
import com.example.namazvakti.domain.port.PrayerRefreshScheduler

/** Android adapter for the context-free scheduler port used by the UI/domain layer. */
class PrayerRefreshSchedulerAdapter(
    context: Context
) : PrayerRefreshScheduler {
    private val appContext = context.applicationContext

    override suspend fun enqueueRefresh(force: Boolean) {
        PrayerWidgetScheduler.enqueueRefresh(appContext, force)
    }

    override suspend fun scheduleBoundary() {
        PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(appContext)
    }
}
