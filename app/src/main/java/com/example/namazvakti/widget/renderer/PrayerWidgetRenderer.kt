package com.example.namazvakti.widget.renderer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.ui.main.MainActivity
import com.example.namazvakti.widget.PrayerWidgetSnapshot
import java.time.ZonedDateTime

enum class PrayerWidgetDataState { Fresh, Stale, Unavailable }

class PrayerWidgetRenderer {
    fun render(context: Context, snapshot: PrayerWidgetSnapshot): RemoteViews =
        render(
            context = context,
            cache = snapshot.cache,
            location = snapshot.location,
            now = snapshot.now,
            dataState = snapshot.dataState
        )

    fun render(
        context: Context,
        cache: CachedPrayerDay?,
        location: PrayerLocation,
        now: ZonedDateTime,
        dataState: PrayerWidgetDataState
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.prayer_widget)
        val localNow = cache?.let { now.withZoneSameInstant(it.timezone) } ?: now
        views.setOnClickPendingIntent(
            R.id.prayer_widget_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        val staleSuffix = if (dataState == PrayerWidgetDataState.Stale) {
            context.getString(R.string.widget_stale_suffix)
        } else ""
        views.setTextViewText(
            R.id.prayer_widget_city,
            buildTopRowText(location.displayCity, cache?.hijriText) + staleSuffix
        )
        val activeItem = itemsForDescription(cache, localNow)
        views.setContentDescription(
            R.id.prayer_widget_root,
            context.getString(
                R.string.widget_content_description,
                location.displayCity,
                when (dataState) {
                    PrayerWidgetDataState.Fresh -> context.getString(R.string.refresh_success)
                    PrayerWidgetDataState.Stale -> context.getString(R.string.stale_data)
                    PrayerWidgetDataState.Unavailable -> context.getString(R.string.prayer_times_unavailable)
                },
                activeItem ?: context.getString(R.string.prayer_times_unavailable)
            )
        )

        val items = cache?.prayerTimes?.toHighlightedDisplayItems(localNow.toLocalTime())
        if (items == null) {
            views.setTextViewText(ITEM_IDS.first(), context.getString(R.string.prayer_times_unavailable))
            ITEM_IDS.drop(1).forEach { views.setTextViewText(it, "") }
        } else {
            items.forEachIndexed { index, item ->
                val viewId = ITEM_IDS[index]
                views.setTextViewText(viewId, item.render())
                views.setTextColor(
                    viewId,
                    if (item.highlighted) context.getColor(R.color.widget_active_text)
                    else context.getColor(R.color.widget_text)
                )
            }
        }
        return views
    }

    private fun buildTopRowText(cityText: String, hijriText: String?): String {
        val cleanedHijri = hijriText?.trim().orEmpty()
        return if (cleanedHijri.isBlank()) cityText else "$cityText • $cleanedHijri"
    }

    private fun itemsForDescription(cache: CachedPrayerDay?, now: ZonedDateTime): String? =
        cache?.prayerTimes?.toHighlightedDisplayItems(now.toLocalTime())
            ?.firstOrNull { it.highlighted }
            ?.render()

    private companion object {
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
