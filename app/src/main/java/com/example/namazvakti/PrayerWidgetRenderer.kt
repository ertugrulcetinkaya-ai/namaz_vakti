package com.example.namazvakti

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.time.ZonedDateTime

class PrayerWidgetRenderer {
    fun render(
        context: Context,
        cache: CachedPrayerDay?,
        location: PrayerLocation,
        now: ZonedDateTime
    ): RemoteViews {
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

        val localNow = cache?.let { now.withZoneSameInstant(it.timezone) } ?: now
        val items = cache?.prayerTimes?.toHighlightedDisplayItems(localNow.toLocalTime())
        if (items == null) {
            views.setTextViewText(ITEM_IDS.first(), FALLBACK_TEXT)
            ITEM_IDS.drop(1).forEach { views.setTextViewText(it, "") }
        } else {
            items.forEachIndexed { index, item ->
                val viewId = ITEM_IDS[index]
                views.setTextViewText(viewId, item.render())
                views.setTextColor(viewId, if (item.highlighted) ACTIVE_COLOR else NORMAL_COLOR)
            }
        }
        return views
    }

    private fun buildTopRowText(cityText: String, hijriText: String?): String {
        val cleanedHijri = hijriText?.trim().orEmpty()
        return if (cleanedHijri.isBlank()) cityText else "$cityText • $cleanedHijri"
    }

    private companion object {
        const val FALLBACK_TEXT = "Vakitler alınamadı"
        val NORMAL_COLOR = Color.parseColor("#FFFFFFFF")
        val ACTIVE_COLOR = Color.parseColor("#00FF00")
        val ITEM_IDS = listOf(
            R.id.prayer_widget_item_1,
            R.id.prayer_widget_item_2,
            R.id.prayer_widget_item_3,
            R.id.prayer_widget_item_4,
            R.id.prayer_widget_item_5,
            R.id.prayer_widget_item_6
        )
    }
}
