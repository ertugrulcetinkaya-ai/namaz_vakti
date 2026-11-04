package com.example.namazvakti.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.displayHijriDate
import com.example.namazvakti.domain.model.displayHijriText
import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.RamadanRange
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MonthlyPrayerScreen(
    state: MonthlyUiState,
    ramadanRange: RamadanRange?,
    today: LocalDate,
    onBack: () -> Unit,
    onOpenImsakiye: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onRetry: () -> Unit
) {
    val month = state.month
    val minimum = YearMonth.from(today).minusMonths(3)
    val maximum = YearMonth.from(today).plusMonths(12)
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val columnLabels = prayerHeaderLabels()
    val columnWidths = rememberPrayerColumnWidths(columnLabels)
    val dayColumnWidth = calendarDayColumnWidth(isImsakiye = false)
    val stickyHeaderOffsetPx = with(LocalDensity.current) { 32.dp.roundToPx() }
    LaunchedEffect(month, state.loading, state.days) {
        val todayIndex = state.days.indexOfFirst { it.date == today }
        if (!state.loading && todayIndex >= 0 && month == YearMonth.from(today)) {
            val leadingItems = 1 +
                (if (state.loading) 1 else 0) +
                (if (state.source == com.example.namazvakti.domain.model.MonthDataSource.PARTIAL) 1 else 0) +
                (if (state.error != null && state.days.isEmpty()) 1 else 0)
            listState.animateScrollToItem(leadingItems + todayIndex, scrollOffset = -stickyHeaderOffsetPx)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        month.format(MONTH_FORMATTER),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenImsakiye) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.imsakiye_open)
                        )
                    }
                    IconButton(
                        enabled = month > minimum,
                        onClick = { onMonthChange(month.minusMonths(1)) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.previous_month))
                    }
                    IconButton(
                        enabled = month < maximum,
                        onClick = { onMonthChange(month.plusMonths(1)) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.next_month))
                    }
                }
            )
        }
    ) { insets ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stickyHeader(key = "monthly_header") {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                    PrayerTableHeader(
                        labels = columnLabels,
                        columnWidths = columnWidths,
                        horizontalScrollState = horizontalScrollState,
                        dayColumnWidth = dayColumnWidth
                    )
                }
            }
            if (state.loading) {
                item { CalendarLoading() }
            }
            if (state.source == com.example.namazvakti.domain.model.MonthDataSource.PARTIAL) {
                item { CalendarNotice(stringResource(R.string.monthly_partial)) }
            }
            if (state.error != null && state.days.isEmpty()) {
                item { CalendarRetry(onRetry, showFailureMessage = true) }
            }
            items(state.days, key = { it.date.toString() }) { cache ->
                val inOfficialRange = ramadanRange?.dayNumber(cache.date) != null
                val hijriDate = cache.displayHijriDate()
                val ramadanDay = ramadanRange?.dayNumber(cache.date)
                    ?: hijriDate?.takeIf { it.month == 9 }?.day
                PrayerCalendarRow(
                    cache = cache,
                    dateLabel = buildString {
                        append(cache.date.format(MONTHLY_DATE_LABEL_FORMATTER))
                        hijriDate?.toDisplayText()?.let {
                            append('\n')
                            append(it)
                        }
                    },
                    accessibilityDate = cache.date.format(ACCESSIBLE_DATE_FORMATTER),
                    highlighted = cache.date == today,
                    columnWidths = columnWidths,
                    horizontalScrollState = horizontalScrollState,
                    dayColumnWidth = dayColumnWidth,
                    ramadan = inOfficialRange || hijriDate?.month == 9,
                    ramadanDay = ramadanDay
                )
            }
            if (state.error != null && state.days.isNotEmpty()) {
                item { CalendarRetry(onRetry, showFailureMessage = false) }
            } else if (
                state.source == com.example.namazvakti.domain.model.MonthDataSource.PARTIAL &&
                state.days.isNotEmpty()
            ) {
                item { CalendarRetry(onRetry, showFailureMessage = false) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ImsakiyeScreen(
    state: ImsakiyeUiState,
    today: LocalDate,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val range = state.range
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val columnLabels = prayerHeaderLabels()
    val columnWidths = rememberPrayerColumnWidths(columnLabels)
    val dayColumnWidth = calendarDayColumnWidth(isImsakiye = true)
    val stickyHeaderOffsetPx = with(LocalDensity.current) { 32.dp.roundToPx() }
    LaunchedEffect(range, state.days, state.loading, state.error) {
        val todayIndex = state.days.indexOfFirst { it.date == today }
        if (!state.loading && todayIndex >= 0) {
            val leadingItems = 1 +
                (if (state.loading) 1 else 0) +
                (if (range != null && state.days.size < range.dayCount) 1 else 0)
            listState.animateScrollToItem(leadingItems + todayIndex, scrollOffset = -stickyHeaderOffsetPx)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            range?.let { "Ramazan ${it.hijriYear} İmsakiyesi" }
                                ?: stringResource(R.string.imsakiye_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.location?.displayCity?.let { city ->
                            Text(city, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        if (range?.estimated == true) {
                            Text(
                                stringResource(R.string.ramadan_estimated_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { insets ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            stickyHeader(key = "imsakiye_header") {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                    PrayerTableHeader(
                        labels = columnLabels,
                        columnWidths = columnWidths,
                        horizontalScrollState = horizontalScrollState,
                        dayColumnWidth = dayColumnWidth,
                        isImsakiye = true
                    )
                }
            }
            if (state.loading) item { CalendarLoading() }
            if (range == null && state.error != null) {
                item { CalendarRetry(onRetry, showFailureMessage = true) }
            }
            if (range != null && state.days.size < range.dayCount) {
                item { CalendarNotice(stringResource(R.string.monthly_partial)) }
            }
            items(state.days, key = { it.date.toString() }) { cache ->
                val dayNumber = range?.dayNumber(cache.date)
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    PrayerCalendarRow(
                        cache = cache,
                        dateLabel = buildString {
                            append(dayNumber?.let { "$it. gün" } ?: "–")
                            append('\n')
                            append(cache.date.format(IMSAKIYE_DATE_FORMATTER))
                            append(' ')
                            append(cache.date.format(WEEKDAY_SHORT_FORMATTER))
                            cache.displayHijriText()?.let {
                                append('\n')
                                append(it)
                            }
                        },
                        accessibilityDate = cache.date.format(ACCESSIBLE_DATE_FORMATTER),
                        highlighted = cache.date == today,
                        columnWidths = columnWidths,
                        horizontalScrollState = horizontalScrollState,
                        dayColumnWidth = dayColumnWidth,
                        ramadanDay = dayNumber,
                        isIftar = true,
                        qadrNight = dayNumber == 26
                    )
                    if (dayNumber == 26) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp + dayColumnWidth, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🌙", style = MaterialTheme.typography.labelSmall)
                            Text(
                                stringResource(R.string.qadr_night_marker),
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            if (range != null) {
                item {
                    Text(
                        stringResource(
                            R.string.eid_date,
                            range.eidDate.format(ACCESSIBLE_DATE_FORMATTER)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (state.error != null && state.days.isNotEmpty()) {
                item { CalendarRetry(onRetry, showFailureMessage = false) }
            }
        }
    }
}

@Composable
private fun PrayerTableHeader(
    labels: List<String>,
    columnWidths: List<androidx.compose.ui.unit.Dp>,
    horizontalScrollState: ScrollState,
    dayColumnWidth: androidx.compose.ui.unit.Dp,
    isImsakiye: Boolean = false
) {
    PrayerTableRowShell(
        dayLabel = if (isImsakiye) stringResource(R.string.ramadan_day_label) else stringResource(R.string.day_label),
        prayerLabels = labels,
        columnWidths = columnWidths,
        horizontalScrollState = horizontalScrollState,
        dayColumnWidth = dayColumnWidth,
        bold = true,
        header = true,
        iftarColumn = isImsakiye
    )
}

@Composable
private fun PrayerCalendarRow(
    cache: CachedPrayerDay,
    dateLabel: String,
    accessibilityDate: String,
    highlighted: Boolean,
    columnWidths: List<androidx.compose.ui.unit.Dp>,
    horizontalScrollState: ScrollState,
    dayColumnWidth: androidx.compose.ui.unit.Dp,
    ramadan: Boolean = false,
    ramadanDay: Int? = null,
    isIftar: Boolean = false,
    qadrNight: Boolean = false
) {
    val prayerLabels = PrayerTimeName.entries.map { name ->
        val time = cache.prayerTimes.timeAt(name).format(PrayerTimes.TIME_FORMATTER)
        time
    }
    val fullDescription = buildString {
        append(accessibilityDate)
        cache.displayHijriText()?.let {
            append(", ")
            append(it)
        }
        ramadanDay?.let { append(", Ramazan'ın $it. günü") }
        if (ramadan) append(", Ramazan")
        if (qadrNight) append(", Kadir Gecesi, 26. günün akşamı 27. güne bağlanır")
        PrayerTimeName.entries.forEachIndexed { index, prayer ->
            append(", ")
            append(prayer.accessibleName())
            if (isIftar && prayer == PrayerTimeName.MAGHRIB) append(" (iftar)")
            append(' ')
            append(cache.prayerTimes.timeAt(prayer).format(PrayerTimes.TIME_FORMATTER))
        }
    }
    val rowBackground = when {
        highlighted -> MaterialTheme.colorScheme.primaryContainer
        cache.date.dayOfWeek == java.time.DayOfWeek.FRIDAY -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = fullDescription
        },
        colors = CardDefaults.cardColors(containerColor = rowBackground),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        PrayerTableRowShell(
            dayLabel = buildString {
                append(dateLabel)
                if (ramadan && ramadanDay == null) append("\n☾")
            },
            prayerLabels = prayerLabels,
            columnWidths = columnWidths,
            horizontalScrollState = horizontalScrollState,
            dayColumnWidth = dayColumnWidth,
            bold = highlighted,
            iftarColumn = isIftar
        )
    }
}

@Composable
private fun PrayerTableRowShell(
    dayLabel: String,
    prayerLabels: List<String>,
    columnWidths: List<androidx.compose.ui.unit.Dp>,
    horizontalScrollState: ScrollState,
    dayColumnWidth: androidx.compose.ui.unit.Dp,
    bold: Boolean,
    header: Boolean = false,
    iftarColumn: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (header) 40.dp else 48.dp)
            .padding(horizontal = 4.dp, vertical = if (header) 4.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayLabel,
            modifier = Modifier.width(dayColumnWidth),
            style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = if (bold || header) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Start,
            maxLines = if (header) 1 else 4,
            overflow = TextOverflow.Clip
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            prayerLabels.forEachIndexed { index, label ->
                val emphasized = iftarColumn && index == PrayerTimeName.MAGHRIB.ordinal
                Text(
                    text = label,
                    modifier = Modifier
                        .width(columnWidths[index])
                        .then(
                            if (emphasized) Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(5.dp))
                                .padding(vertical = 3.dp, horizontal = 6.dp)
                            else Modifier.padding(horizontal = 6.dp)
                        )
                        .then(
                            if (header && iftarColumn && index == PrayerTimeName.MAGHRIB.ordinal) {
                                Modifier.clearAndSetSemantics { contentDescription = "Akşam (iftar)" }
                            } else {
                                Modifier
                            }
                        ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                    fontWeight = if (bold || emphasized || header) FontWeight.Bold else FontWeight.Medium,
                    color = if (emphasized) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun prayerHeaderLabels(): List<String> = PrayerTimeName.entries.map { name ->
    when (name) {
        PrayerTimeName.IMSAK -> stringResource(R.string.prayer_imsak)
        PrayerTimeName.SUNRISE -> stringResource(R.string.prayer_sunrise)
        PrayerTimeName.DHUHR -> stringResource(R.string.prayer_dhuhr)
        PrayerTimeName.ASR -> stringResource(R.string.prayer_asr)
        PrayerTimeName.MAGHRIB -> stringResource(R.string.prayer_maghrib)
        PrayerTimeName.ISHA -> stringResource(R.string.prayer_isha)
    }
}

@Composable
private fun rememberPrayerColumnWidths(labels: List<String>): List<androidx.compose.ui.unit.Dp> {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val timeStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    return remember(textMeasurer, labels, density.density, density.fontScale, headerStyle, timeStyle) {
        labels.map { label ->
            val labelWidth = textMeasurer.measure(
                text = AnnotatedString(label),
                style = headerStyle,
                maxLines = 1
            ).size.width
            val timeWidth = textMeasurer.measure(
                text = AnnotatedString("00:00"),
                style = timeStyle,
                maxLines = 1
            ).size.width
            with(density) { max(labelWidth, timeWidth).toDp() + 16.dp }
        }
    }
}

@Composable
private fun calendarDayColumnWidth(isImsakiye: Boolean): androidx.compose.ui.unit.Dp {
    val largeScale = LocalDensity.current.fontScale >= 1.5f
    return when {
        isImsakiye && largeScale -> 168.dp
        isImsakiye -> 144.dp
        largeScale -> 168.dp
        else -> 144.dp
    }
}

@Composable
private fun CalendarLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.refreshing))
    }
}

@Composable
private fun CalendarNotice(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun CalendarRetry(onRetry: () -> Unit, showFailureMessage: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showFailureMessage) Text(stringResource(R.string.monthly_load_failed))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

private fun PrayerTimeName.accessibleName(): String = when (this) {
    PrayerTimeName.IMSAK -> "İmsak"
    PrayerTimeName.SUNRISE -> "Güneş"
    PrayerTimeName.DHUHR -> "Öğle"
    PrayerTimeName.ASR -> "İkindi"
    PrayerTimeName.MAGHRIB -> "Akşam"
    PrayerTimeName.ISHA -> "Yatsı"
}

private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("tr-TR"))
private val MONTHLY_DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("d EEE", Locale.forLanguageTag("tr-TR"))
private val IMSAKIYE_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr-TR"))
private val WEEKDAY_SHORT_FORMATTER = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("tr-TR"))
private val ACCESSIBLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM EEEE", Locale.forLanguageTag("tr-TR"))
