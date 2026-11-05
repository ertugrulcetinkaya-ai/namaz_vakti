package com.example.namazvakti.widget.renderer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DiyanetReligiousDay
import com.example.namazvakti.domain.model.displayHijriText
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.RamadanRange
import com.example.namazvakti.domain.policy.RamadanCountdownCalculator
import com.example.namazvakti.domain.policy.RamadanCountdownKind
import com.example.namazvakti.ui.main.MainActivity
import com.example.namazvakti.widget.PrayerWidgetCountdown
import com.example.namazvakti.widget.PrayerWidgetCountdownCalculator
import com.example.namazvakti.widget.PrayerWidgetSizeClass
import com.example.namazvakti.widget.PrayerWidgetSnapshot
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PrayerWidgetDataState { Fresh, Stale, Unavailable }

class PrayerWidgetRenderer {
    fun render(
        context: Context,
        snapshot: PrayerWidgetSnapshot,
        sizeClass: PrayerWidgetSizeClass,
        elapsedRealtime: Long = android.os.SystemClock.elapsedRealtime()
    ): RemoteViews = render(
        context = context,
        cache = snapshot.cache,
        location = snapshot.location,
        now = snapshot.now,
        dataState = snapshot.dataState,
        sizeClass = sizeClass,
        tomorrowCache = snapshot.tomorrowCache,
        ramadanRange = snapshot.ramadanRange,
        elapsedRealtime = elapsedRealtime,
        religiousDays = snapshot.religiousDays
    )

    fun render(
        context: Context,
        cache: CachedPrayerDay?,
        location: PrayerLocation,
        now: ZonedDateTime,
        dataState: PrayerWidgetDataState,
        sizeClass: PrayerWidgetSizeClass = PrayerWidgetSizeClass.MEDIUM,
        tomorrowCache: CachedPrayerDay? = null,
        ramadanRange: RamadanRange? = null,
        elapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
        religiousDays: List<DiyanetReligiousDay> = emptyList()
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutFor(sizeClass))
        val localNow = now.withZoneSameInstant(cache?.timezone ?: location.timezone)
        views.setOnClickPendingIntent(
            android.R.id.background,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val staleSuffix = if (dataState == PrayerWidgetDataState.Stale) {
            " • ${context.getString(R.string.widget_stale_label)}"
        } else ""
        val hijriText = cache?.displayHijriText()?.trim().orEmpty()
        val cityText = when (sizeClass) {
            PrayerWidgetSizeClass.LARGE -> location.displayCity + staleSuffix
            PrayerWidgetSizeClass.MEDIUM -> listOf(location.displayCity, hijriText)
                .filter(String::isNotBlank).joinToString(" • ") + staleSuffix
            PrayerWidgetSizeClass.SMALL -> if (dataState == PrayerWidgetDataState.Stale) {
                context.getString(R.string.widget_stale_label)
            } else location.displayCity
        }
        views.setTextViewText(R.id.prayer_widget_city, cityText)
        val gregorianDate = localNow.format(GREGORIAN_FORMATTER)
        views.setTextViewText(
            R.id.prayer_widget_date,
            if (sizeClass == PrayerWidgetSizeClass.LARGE) {
                listOf(gregorianDate, hijriText).filter(String::isNotBlank).joinToString(" • ")
            } else ""
        )

        val activeItems = cache?.prayerTimes?.toHighlightedDisplayItems(localNow.toLocalTime())
        if (activeItems == null) {
            views.setTextViewText(R.id.prayer_widget_item_1, context.getString(R.string.prayer_times_unavailable))
            PRAYER_ITEM_IDS.drop(1).forEach { views.setTextViewText(it, "") }
        } else {
            activeItems.forEachIndexed { index, item ->
                val viewId = PRAYER_ITEM_IDS[index]
                views.setTextViewText(viewId, item.render())
                views.setTextColor(
                    viewId,
                    if (item.highlighted) context.getColor(R.color.widget_active_text)
                    else context.getColor(R.color.widget_text)
                )
            }
        }
        views.setViewVisibility(
            R.id.prayer_widget_prayer_grid,
            if (sizeClass == PrayerWidgetSizeClass.SMALL || activeItems == null) View.GONE else View.VISIBLE
        )

        val countdown = PrayerWidgetCountdownCalculator.calculate(
            cache = cache,
            tomorrowCache = tomorrowCache,
            now = localNow,
            elapsedRealtime = elapsedRealtime,
            dataState = dataState
        )
        val nextText = countdown?.let { nextPrayerText(context, it, sizeClass) }
            ?: fallbackNextPrayerText(context, cache, localNow, dataState, sizeClass)
        views.setTextViewText(
            R.id.prayer_widget_next_prayer,
            nextText ?: if (dataState == PrayerWidgetDataState.Unavailable) {
                context.getString(R.string.prayer_times_unavailable)
            } else ""
        )
        views.setTextColor(R.id.prayer_widget_next_prayer, context.getColor(R.color.widget_active_text))
        if (sizeClass == PrayerWidgetSizeClass.SMALL && dataState == PrayerWidgetDataState.Unavailable) {
            views.setTextViewTextSize(
                R.id.prayer_widget_next_prayer,
                TypedValue.COMPLEX_UNIT_SP,
                11f
            )
        }
        val showCountdown = countdown != null && dataState == PrayerWidgetDataState.Fresh
        if (showCountdown) {
            views.setChronometer(
                R.id.prayer_widget_chronometer,
                countdown.chronometerBase,
                if (sizeClass == PrayerWidgetSizeClass.SMALL) {
                    null
                } else {
                    context.getString(R.string.widget_chronometer_format)
                },
                true
            )
            views.setChronometerCountDown(R.id.prayer_widget_chronometer, true)
        }
        views.setViewVisibility(
            R.id.prayer_widget_chronometer,
            if (showCountdown) View.VISIBLE else View.GONE
        )

        val ramadanText = ramadanText(context, cache, tomorrowCache, ramadanRange, localNow, dataState)
        val religiousText = religiousText(context, religiousDays)
        val bottomInfo = bottomInfoText(context, ramadanText, religiousText)
        views.setTextViewText(R.id.prayer_widget_ramadan, bottomInfo.orEmpty())
        views.setViewVisibility(
            R.id.prayer_widget_ramadan,
            if (sizeClass == PrayerWidgetSizeClass.LARGE && bottomInfo != null) View.VISIBLE else View.GONE
        )

        val spoken = when {
            dataState == PrayerWidgetDataState.Unavailable ->
                context.getString(R.string.prayer_times_unavailable)
            else -> buildString {
                if (dataState == PrayerWidgetDataState.Stale) {
                    append(context.getString(R.string.stale_data))
                    append(". ")
                }
                if (countdown != null) {
                    append(context.getString(
                        R.string.widget_next_prayer_accessibility,
                        "${countdown.prayer.displayName} ${countdown.timeText}"
                    ))
                } else if (nextText != null) {
                    append(context.getString(
                        R.string.widget_next_prayer_accessibility,
                        nextText.removePrefix("${context.getString(R.string.next_prayer_title)}: ")
                    ))
                }
            }
        }
        views.setContentDescription(
            android.R.id.background,
            context.getString(R.string.widget_content_description, location.displayCity, spoken.trim())
        )
        return views
    }

    private fun fallbackNextPrayerText(
        context: Context,
        cache: CachedPrayerDay?,
        now: ZonedDateTime,
        dataState: PrayerWidgetDataState,
        sizeClass: PrayerWidgetSizeClass
    ): String? {
        if (dataState == PrayerWidgetDataState.Unavailable || cache == null) return null
        val name = cache.prayerTimes.nextPrayerName(now.toLocalTime()) ?: return null
        val time = cache.prayerTimes.timeAt(name).format(PrayerTimes.TIME_FORMATTER)
        val prayerAndTime = "${name.displayName} $time"
        return if (sizeClass == PrayerWidgetSizeClass.LARGE) {
            context.getString(R.string.widget_next_prayer, prayerAndTime)
        } else prayerAndTime
    }

    private fun nextPrayerText(
        context: Context,
        countdown: PrayerWidgetCountdown,
        sizeClass: PrayerWidgetSizeClass
    ): String {
        val timeText = "${countdown.prayer.displayName} ${countdown.timeText}"
        return if (sizeClass == PrayerWidgetSizeClass.LARGE) {
            context.getString(R.string.widget_next_prayer, timeText)
        } else timeText
    }

    private fun ramadanText(
        context: Context,
        cache: CachedPrayerDay?,
        tomorrowCache: CachedPrayerDay?,
        range: RamadanRange?,
        now: ZonedDateTime,
        dataState: PrayerWidgetDataState
    ): String? {
        if (dataState != PrayerWidgetDataState.Fresh || cache == null || range == null) return null
        val countdown = RamadanCountdownCalculator.calculate(
            date = cache.date,
            range = range,
            today = cache.prayerTimes,
            tomorrow = tomorrowCache?.prayerTimes,
            timezone = cache.timezone,
            now = now
        ) ?: return null
        val time = countdown.targetAt.format(PrayerTimes.TIME_FORMATTER)
        val event = when (countdown.kind) {
            RamadanCountdownKind.IFTAR -> context.getString(R.string.widget_ramadan_iftar, time)
            RamadanCountdownKind.SAHUR -> context.getString(R.string.widget_ramadan_sahur, time)
        }
        return "$event • ${context.getString(R.string.ramadan_day, countdown.dayNumber)}"
    }


    private fun bottomInfoText(context: Context, ramadanText: String?, religiousText: String?): String? {
        if (ramadanText == null) return religiousText
        if (religiousText == null) return ramadanText
        val combined = "$ramadanText • $religiousText"
        val paint = TextPaint().apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                11f,
                context.resources.displayMetrics
            )
        }
        val availableWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            218f,
            context.resources.displayMetrics
        )
        return if (paint.measureText(combined) <= availableWidth) combined else ramadanText
    }

    private fun religiousText(context: Context, events: List<DiyanetReligiousDay>): String? {
        if (events.isEmpty()) return null
        val paint = TextPaint().apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                11f,
                context.resources.displayMetrics
            )
        }
        val availableWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            218f,
            context.resources.displayMetrics
        )
        val selected = mutableListOf<String>()
        events.sortedBy { if (it.isNight) 0 else 1 }.forEach { event ->
            val text = context.getString(
                if (event.isNight) R.string.widget_religious_night else R.string.widget_religious_day,
                event.displayName
            )
            val candidate = (selected + text).joinToString(" · ")
            if (paint.measureText(candidate) <= availableWidth) selected += text
        }
        return selected.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun layoutFor(sizeClass: PrayerWidgetSizeClass): Int = when (sizeClass) {
        PrayerWidgetSizeClass.SMALL -> R.layout.prayer_widget_small
        PrayerWidgetSizeClass.MEDIUM -> R.layout.prayer_widget
        PrayerWidgetSizeClass.LARGE -> R.layout.prayer_widget_large
    }

    private companion object {
        val GREGORIAN_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))
        val PRAYER_ITEM_IDS = listOf(
            R.id.prayer_widget_item_1,
            R.id.prayer_widget_item_2,
            R.id.prayer_widget_item_3,
            R.id.prayer_widget_item_4,
            R.id.prayer_widget_item_5,
            R.id.prayer_widget_item_6
        )
    }
}
