package com.example.namazvakti.widget

import android.content.Context
import com.example.namazvakti.domain.port.PrayerWidgetUpdatePort

/** Android adapter for the context-free widget update port used by the UI/domain layer. */
class PrayerWidgetUpdateAdapter(
    context: Context
) : PrayerWidgetUpdatePort {
    private val appContext = context.applicationContext

    override suspend fun updateAll() {
        PrayerWidgetUpdater.updateAll(appContext)
    }
}
