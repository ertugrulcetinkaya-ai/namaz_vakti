package com.example.namazvakti.widget.renderer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DiyanetReligiousDay
import com.example.namazvakti.domain.model.displayHijriText
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeName
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
import java.util.Locale

enum class PrayerWidgetDataState { Fresh, Stale, Unavailable }

class PrayerWidgetRenderer {
    fun render(
        context: Context,
        snapshot: PrayerWidgetSnapshot,
        sizeClass: PrayerWidgetSizeClass,
        elapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
        widthDp: Int? = null
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
        religiousDays = snapshot.religiousDays,
        widthDp = widthDp
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
        religiousDays: List<DiyanetReligiousDay> = emptyList(),
        widthDp: Int? = null
    ): RemoteViews {
        val compactDetails = sizeClass == PrayerWidgetSizeClass.MEDIUM &&
            widthDp != null && widthDp < COMPACT_DETAILS_MIN_WIDTH_DP
        val views = RemoteViews(context.packageName, layoutFor(sizeClass, widthDp))
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
            context.getString(R.string.widget_stale_short)
        } else ""
        val hijriText = cache?.displayHijriText()?.trim().orEmpty()
        val showProvinceCenterNote = sizeClass == PrayerWidgetSizeClass.LARGE &&
            cache?.usesProvinceCenterFallback == true &&
            (widthDp == null || widthDp >= COMPACT_DETAILS_MIN_WIDTH_DP)
        val provinceCenterNote = if (showProvinceCenterNote) {
            context.getString(R.string.widget_province_center_short)
        } else {
            null
        }
        val city = when {
            sizeClass == PrayerWidgetSizeClass.SMALL -> location.displayCity.uppercase(TURKISH_LOCALE)
            compactDetails -> measuredCityText(
                context,
                listOf(
                    listOf(location.widgetTitleCaseName(), staleSuffix),
                    listOf(location.displayCity.widgetTitleCase(), staleSuffix)
                ),
                widthDp,
                COMPACT_TEXT_SIZE_SP
            )
            else -> location.widgetTitleCaseName()
        }
        val smallNextPrayer = cache?.prayerTimes?.nextPrayerName(localNow.toLocalTime())
            ?: tomorrowCache?.let { PrayerTimeName.IMSAK }
        val smallNextTime = smallNextPrayer?.let { prayer ->
            val prayerCache = if (cache?.prayerTimes?.nextPrayerName(localNow.toLocalTime()) == prayer) cache else tomorrowCache
            prayerCache?.prayerTimes?.timeAt(prayer)?.format(PrayerTimes.TIME_FORMATTER)
        }
        val cityText = if (sizeClass == PrayerWidgetSizeClass.SMALL && smallNextTime != null) {
            context.getString(R.string.widget_small_city_time, city, smallNextTime)
        } else {
            city
        }
        val renderedCity = if (staleSuffix.isNotEmpty() && sizeClass != PrayerWidgetSizeClass.SMALL) {
            styledStaleSuffix(cityText, staleSuffix, context.getColor(R.color.widget_secondary_text))
        } else cityText
        views.setTextViewText(R.id.prayer_widget_city, renderedCity)
        views.setTextViewText(R.id.prayer_widget_date, if (compactDetails) "" else hijriText)
        if (sizeClass == PrayerWidgetSizeClass.LARGE) {
            views.setTextViewText(R.id.prayer_widget_province_center, provinceCenterNote.orEmpty())
            views.setViewVisibility(
                R.id.prayer_widget_province_center,
                if (provinceCenterNote != null) View.VISIBLE else View.GONE
            )
        }

        val activeItems = cache?.prayerTimes?.toHighlightedDisplayItems(localNow.toLocalTime())
        if (activeItems == null) {
            if (compactDetails) {
                views.setTextViewText(R.id.prayer_widget_item_1, context.getString(R.string.prayer_times_unavailable))
                COMPACT_PRAYER_ITEM_IDS.drop(1).forEach { views.setTextViewText(it, "") }
            }
        } else if (sizeClass != PrayerWidgetSizeClass.SMALL) {
            activeItems.forEachIndexed { index, item ->
                val prayerName = PRAYER_NAMES_IN_DISPLAY_ORDER[index]
                val timeText = item.time.format(PrayerTimes.TIME_FORMATTER)
                val cellId = PRAYER_CELL_IDS[index]
                views.setContentDescription(
                    cellId,
                    if (item.highlighted) {
                        context.getString(R.string.current_prayer_value, item.label)
                    } else {
                        context.getString(R.string.prayer_time_with_time, item.label, timeText)
                    }
                )
                val label = if (compactDetails) {
                    context.getString(PRAYER_SHORT_LABELS.getValue(prayerName))
                } else {
                    item.label.uppercase(TURKISH_LOCALE)
                }
                if (compactDetails) {
                    views.setTextViewText(COMPACT_PRAYER_ITEM_IDS[index], "$label $timeText")
                    views.setTextColor(
                        COMPACT_PRAYER_ITEM_IDS[index],
                        context.getColor(if (item.highlighted) R.color.widget_active_text else R.color.widget_text)
                    )
                } else {
                    val activeLabelId = PRAYER_ACTIVE_LABEL_IDS[index]
                    val labelId = PRAYER_LABEL_IDS[index]
                    val activeTimeId = PRAYER_ACTIVE_TIME_IDS[index]
                    val timeId = PRAYER_TIME_IDS[index]
                    views.setTextViewText(labelId, label)
                    views.setTextViewText(activeLabelId, label)
                    views.setTextViewText(timeId, timeText)
                    views.setTextViewText(activeTimeId, timeText)
                    views.setViewVisibility(labelId, if (item.highlighted) View.GONE else View.VISIBLE)
                    views.setViewVisibility(timeId, if (item.highlighted) View.GONE else View.VISIBLE)
                    views.setViewVisibility(activeLabelId, if (item.highlighted) View.VISIBLE else View.GONE)
                    views.setViewVisibility(activeTimeId, if (item.highlighted) View.VISIBLE else View.GONE)
                }
                views.setViewVisibility(
                    PRAYER_UNDERLINE_IDS[index],
                    if (item.highlighted) View.VISIBLE else View.GONE
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
        val nextText = countdown?.let(::nextPrayerText)
            ?: fallbackNextPrayerText(cache, localNow, dataState)
        val displayNextText = when {
            sizeClass == PrayerWidgetSizeClass.SMALL && dataState == PrayerWidgetDataState.Unavailable ->
                context.getString(R.string.widget_times_unavailable_short)
            sizeClass == PrayerWidgetSizeClass.SMALL -> smallNextPrayer?.let { prayer ->
                context.getString(SMALL_NEXT_LABELS.getValue(prayer))
            } ?: ""
            else -> nextText
        }
        views.setTextViewText(
            R.id.prayer_widget_next_prayer,
            displayNextText ?: if (dataState == PrayerWidgetDataState.Unavailable) {
                context.getString(R.string.prayer_times_unavailable)
            } else ""
        )
        views.setTextColor(
            R.id.prayer_widget_next_prayer,
            context.getColor(if (sizeClass == PrayerWidgetSizeClass.SMALL) R.color.widget_hijri_text else R.color.widget_active_text)
        )
        val showCountdown = countdown != null &&
            dataState == PrayerWidgetDataState.Fresh &&
            sizeClass != PrayerWidgetSizeClass.MEDIUM
        val showSmallStaleLabel = sizeClass == PrayerWidgetSizeClass.SMALL &&
            dataState == PrayerWidgetDataState.Stale
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
        } else if (showSmallStaleLabel) {
            views.setTextViewText(R.id.prayer_widget_chronometer, context.getString(R.string.widget_stale_short))
            views.setTextColor(R.id.prayer_widget_chronometer, context.getColor(R.color.widget_secondary_text))
        }
        views.setViewVisibility(
            R.id.prayer_widget_chronometer,
            if (showCountdown || showSmallStaleLabel) View.VISIBLE else View.GONE
        )

        val ramadanText = ramadanText(context, cache, tomorrowCache, ramadanRange, localNow, dataState)
        val religiousText = religiousText(context, religiousDays)
        val bottomInfo = if (dataState == PrayerWidgetDataState.Stale) {
            context.getString(R.string.widget_stale_short)
        } else {
            bottomInfoText(context, ramadanText, religiousText)
        }
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
                activeItems?.firstOrNull { it.highlighted }?.let { active ->
                    append(context.getString(R.string.current_prayer_value, active.label))
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
            context.getString(R.string.widget_content_description, location.widgetTitleCaseName(), spoken.trim())
        )
        return views
    }

    private fun fallbackNextPrayerText(
        cache: CachedPrayerDay?,
        now: ZonedDateTime,
        dataState: PrayerWidgetDataState
    ): String? {
        if (dataState == PrayerWidgetDataState.Unavailable || cache == null) return null
        val name = cache.prayerTimes.nextPrayerName(now.toLocalTime()) ?: return null
        val time = cache.prayerTimes.timeAt(name).format(PrayerTimes.TIME_FORMATTER)
        return "${name.displayName} $time"
    }

    private fun nextPrayerText(
        countdown: PrayerWidgetCountdown
    ): String {
        return "${countdown.prayer.displayName} ${countdown.timeText}"
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
                18f,
                context.resources.displayMetrics
            )
        }
        val availableWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            204f,
            context.resources.displayMetrics
        )
        return if (paint.measureText(combined) <= availableWidth) combined else ramadanText
    }

    private fun largeCityText(
        context: Context,
        location: PrayerLocation,
        hijriText: String,
        provinceCenterNote: String?,
        widthDp: Int?,
        reducedTextSize: Boolean
    ): String {
        val city = location.displayName
        if (provinceCenterNote == null || widthDp == null) {
            return listOf(city, hijriText).filter(String::isNotBlank).joinToString(" • ")
        }
        val textSizeSp = if (reducedTextSize) COMPACT_TEXT_SIZE_SP else LARGE_CITY_TEXT_SIZE_SP
        val candidates = listOf(
            listOf(city, hijriText, provinceCenterNote),
            listOf(city, provinceCenterNote),
            listOf(location.displayCity, provinceCenterNote)
        )
        return measuredCityText(context, candidates, widthDp, textSizeSp)
    }

    private fun mediumCityText(
        context: Context,
        location: PrayerLocation,
        hijriText: String,
        staleSuffix: String,
        widthDp: Int?,
        compactDetails: Boolean,
        reducedTextSize: Boolean
    ): String {
        val optionalDetails = if (compactDetails) emptyList() else listOf(hijriText)
        val districtName = location.selectedDistrictName()
        val candidates = listOfNotNull(
            listOf(location.displayName) + optionalDetails + staleSuffix,
            listOf(location.displayName, staleSuffix),
            districtName?.let { listOf(it, staleSuffix) },
            listOf(location.displayCity) + optionalDetails + staleSuffix,
            listOf(location.displayCity, staleSuffix)
        )
        val textSizeSp = if (reducedTextSize) COMPACT_TEXT_SIZE_SP else LARGE_CITY_TEXT_SIZE_SP
        return measuredCityText(context, candidates, widthDp, textSizeSp)
    }

    private fun measuredCityText(
        context: Context,
        candidates: List<List<String>>,
        widthDp: Int?,
        textSizeSp: Float
    ): String {
        val candidateTexts = candidates
            .map { it.filter(String::isNotBlank).joinToString(" • ") }
            .distinct()
        if (widthDp == null) return candidateTexts.first()
        val paint = TextPaint().apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics
            )
        }
        val availableWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            widthDp.toFloat(),
            context.resources.displayMetrics
        ) - context.resources.getDimension(R.dimen.widget_padding_horizontal) * 2
        return candidateTexts.firstOrNull { paint.measureText(it) <= availableWidth }
            ?: candidateTexts.last()
    }

    // District alone ("KARTAL") is a better fallback than the province when "KARTAL, İSTANBUL" does not fit.
    private fun PrayerLocation.selectedDistrictName(): String? =
        displayName.takeIf { it != displayCity }?.substringBefore(',')?.trim()?.takeIf(String::isNotBlank)

    private fun PrayerLocation.widgetTitleCaseName(): String = displayName.widgetTitleCase()

    private fun String.widgetTitleCase(): String = lowercase(TURKISH_LOCALE)
        .split(' ')
        .joinToString(" ") { word ->
            word.split('-').joinToString("-") { part ->
                part.replaceFirstChar { character ->
                    if (character.isLowerCase()) character.titlecase(TURKISH_LOCALE) else character.toString()
                }
            }
        }

    private fun religiousText(context: Context, events: List<DiyanetReligiousDay>): String? {
        if (events.isEmpty()) return null
        val paint = TextPaint().apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                18f,
                context.resources.displayMetrics
            )
        }
        val availableWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            204f,
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

    private fun layoutFor(sizeClass: PrayerWidgetSizeClass, widthDp: Int?): Int = when (sizeClass) {
        PrayerWidgetSizeClass.SMALL -> R.layout.prayer_widget_small
        PrayerWidgetSizeClass.MEDIUM -> when {
            widthDp != null && widthDp < COMPACT_DETAILS_MIN_WIDTH_DP -> R.layout.prayer_widget_compact
            widthDp != null && widthDp >= WIDE_LAYOUT_MIN_WIDTH_DP -> R.layout.prayer_widget_wide
            else -> R.layout.prayer_widget
        }
        PrayerWidgetSizeClass.LARGE -> R.layout.prayer_widget_large
    }

    private fun styledStaleSuffix(text: String, suffix: String, color: Int): CharSequence {
        val fullText = "$text $suffix"
        return SpannableString(fullText).apply {
            setSpan(
                ForegroundColorSpan(color),
                text.length + 1,
                fullText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private companion object {
        // Applied RemoteViews fit full 16sp labels at 320/360/380dp (and 22sp at 515dp);
        // at 280dp, Güneş, active Öğle, and Akşam clip, so short labels are only needed below 320dp.
        const val COMPACT_DETAILS_MIN_WIDTH_DP = 320
        const val WIDE_LAYOUT_MIN_WIDTH_DP = 400
        const val COMPACT_TEXT_SIZE_SP = 16f
        const val LARGE_CITY_TEXT_SIZE_SP = 24f
        const val SMALL_CITY_TEXT_SIZE_SP = 18f
        const val REDUCED_TEXT_SIZE_MIN_WIDTH_DP = 400
        val TURKISH_LOCALE: Locale = Locale.forLanguageTag("tr-TR")

        val PRAYER_NAMES_IN_DISPLAY_ORDER = listOf(
            PrayerTimeName.IMSAK,
            PrayerTimeName.SUNRISE,
            PrayerTimeName.DHUHR,
            PrayerTimeName.ASR,
            PrayerTimeName.MAGHRIB,
            PrayerTimeName.ISHA
        )
        val PRAYER_SHORT_LABELS = mapOf(
            PrayerTimeName.IMSAK to R.string.prayer_imsak_short,
            PrayerTimeName.SUNRISE to R.string.prayer_sunrise_short,
            PrayerTimeName.DHUHR to R.string.prayer_dhuhr_short,
            PrayerTimeName.ASR to R.string.prayer_asr_short,
            PrayerTimeName.MAGHRIB to R.string.prayer_maghrib_short,
            PrayerTimeName.ISHA to R.string.prayer_isha_short
        )

        val PRAYER_ITEM_IDS = listOf(
            R.id.prayer_widget_item_1,
            R.id.prayer_widget_item_2,
            R.id.prayer_widget_item_3,
            R.id.prayer_widget_item_4,
            R.id.prayer_widget_item_5,
            R.id.prayer_widget_item_6
        )

        val COMPACT_PRAYER_ITEM_IDS = PRAYER_ITEM_IDS

        val PRAYER_CELL_IDS = listOf(
            R.id.prayer_widget_cell_1,
            R.id.prayer_widget_cell_2,
            R.id.prayer_widget_cell_3,
            R.id.prayer_widget_cell_4,
            R.id.prayer_widget_cell_5,
            R.id.prayer_widget_cell_6
        )

        val PRAYER_LABEL_IDS = listOf(
            R.id.prayer_widget_label_1,
            R.id.prayer_widget_label_2,
            R.id.prayer_widget_label_3,
            R.id.prayer_widget_label_4,
            R.id.prayer_widget_label_5,
            R.id.prayer_widget_label_6
        )

        val PRAYER_ACTIVE_LABEL_IDS = listOf(
            R.id.prayer_widget_active_label_1,
            R.id.prayer_widget_active_label_2,
            R.id.prayer_widget_active_label_3,
            R.id.prayer_widget_active_label_4,
            R.id.prayer_widget_active_label_5,
            R.id.prayer_widget_active_label_6
        )

        val PRAYER_TIME_IDS = listOf(
            R.id.prayer_widget_time_1,
            R.id.prayer_widget_time_2,
            R.id.prayer_widget_time_3,
            R.id.prayer_widget_time_4,
            R.id.prayer_widget_time_5,
            R.id.prayer_widget_time_6
        )

        val PRAYER_ACTIVE_TIME_IDS = listOf(
            R.id.prayer_widget_active_time_1,
            R.id.prayer_widget_active_time_2,
            R.id.prayer_widget_active_time_3,
            R.id.prayer_widget_active_time_4,
            R.id.prayer_widget_active_time_5,
            R.id.prayer_widget_active_time_6
        )

        val PRAYER_UNDERLINE_IDS = listOf(
            R.id.prayer_widget_underline_1,
            R.id.prayer_widget_underline_2,
            R.id.prayer_widget_underline_3,
            R.id.prayer_widget_underline_4,
            R.id.prayer_widget_underline_5,
            R.id.prayer_widget_underline_6
        )

        val SMALL_NEXT_LABELS = mapOf(
            PrayerTimeName.IMSAK to R.string.widget_small_next_imsak,
            PrayerTimeName.SUNRISE to R.string.widget_small_next_sunrise,
            PrayerTimeName.DHUHR to R.string.widget_small_next_dhuhr,
            PrayerTimeName.ASR to R.string.widget_small_next_asr,
            PrayerTimeName.MAGHRIB to R.string.widget_small_next_maghrib,
            PrayerTimeName.ISHA to R.string.widget_small_next_isha
        )
    }
}
