package com.example.namazvakti.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.RamadanRange
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    LaunchedEffect(month, state.loading, state.days) {
        if (!state.loading && state.days.isNotEmpty() && month == YearMonth.from(today)) {
            val statusItems = if (state.source == com.example.namazvakti.domain.model.MonthDataSource.PARTIAL) 1 else 0
            val target = statusItems + today.dayOfMonth - 1
            listState.animateScrollToItem(target.coerceAtMost(statusItems + state.days.lastIndex))
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(month.format(MONTH_FORMATTER)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = onOpenImsakiye) {
                        Text(stringResource(R.string.imsakiye_open))
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
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            PrayerTableHeader()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.loading) {
                    item { CalendarLoading() }
                }
                if (state.source == com.example.namazvakti.domain.model.MonthDataSource.PARTIAL) {
                    item { CalendarNotice(stringResource(R.string.monthly_partial)) }
                }
                if (state.error != null && state.days.isEmpty() && !state.loading) {
                    item { CalendarRetry(onRetry) }
                }
                items(state.days, key = { it.date.toString() }) { cache ->
                    val inOfficialRange = ramadanRange?.dayNumber(cache.date) != null
                    PrayerCalendarRow(
                        cache = cache,
                        dateLabel = cache.date.format(DAY_LABEL_FORMATTER),
                        accessibilityDate = cache.date.format(ACCESSIBLE_DATE_FORMATTER),
                        highlighted = cache.date == today,
                        ramadan = inOfficialRange || cache.hijriMonth == 9
                    )
                }
                if (state.error != null && state.days.isNotEmpty()) {
                    item { CalendarRetry(onRetry) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImsakiyeScreen(
    state: ImsakiyeUiState,
    today: LocalDate,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val range = state.range
    val listState = rememberLazyListState()
    LaunchedEffect(range, state.days, state.loading) {
        val dayNumber = range?.dayNumber(today)
        if (!state.loading && dayNumber != null && state.days.isNotEmpty()) {
            val partialOffset = if (state.days.size < range.dayCount) 1 else 0
            val firstRowIndex = partialOffset
            val target = firstRowIndex + dayNumber - 1
            val lastRowIndex = firstRowIndex + state.days.lastIndex
            listState.animateScrollToItem(target.coerceAtMost(lastRowIndex))
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
                            Text("· $city", style = MaterialTheme.typography.labelMedium)
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
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            PrayerTableHeader(isImsakiye = true)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.loading) item { CalendarLoading() }
                if (range == null && state.error != null) {
                    item { CalendarRetry(onRetry) }
                }
                if (range != null && state.days.size < range.dayCount) {
                    item { CalendarNotice(stringResource(R.string.monthly_partial)) }
                }
                items(state.days, key = { it.date.toString() }) { cache ->
                    val dayNumber = range?.dayNumber(cache.date)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PrayerCalendarRow(
                            cache = cache,
                            dateLabel = buildString {
                                append(dayNumber ?: "–")
                                append('\n')
                                append(cache.date.format(IMSAKIYE_DATE_FORMATTER))
                                append('\n')
                                append(cache.date.format(WEEKDAY_SHORT_FORMATTER))
                            },
                            accessibilityDate = cache.date.format(ACCESSIBLE_DATE_FORMATTER),
                            highlighted = cache.date == today,
                            ramadanDay = dayNumber,
                            isIftar = true
                        )
                        if (dayNumber == 26) {
                            Text(
                                stringResource(R.string.qadr_night_marker),
                                modifier = Modifier.padding(start = 62.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
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
                if (state.error != null) item { CalendarRetry(onRetry) }
            }
        }
    }
}

@Composable
private fun PrayerTableHeader(isImsakiye: Boolean = false) {
    PrayerTimeName.entries.toList().let { prayers ->
        PrayerTableRowShell(
            dayLabel = if (isImsakiye) stringResource(R.string.ramadan_day_label) else stringResource(R.string.day_label),
            prayerLabels = prayers.map { name ->
                when (name) {
                    PrayerTimeName.IMSAK -> stringResource(R.string.prayer_imsak)
                    PrayerTimeName.SUNRISE -> stringResource(R.string.prayer_sunrise)
                    PrayerTimeName.DHUHR -> stringResource(R.string.prayer_dhuhr)
                    PrayerTimeName.ASR -> stringResource(R.string.prayer_asr)
                    PrayerTimeName.MAGHRIB -> if (isImsakiye) {
                        stringResource(R.string.prayer_maghrib_iftar)
                    } else {
                        stringResource(R.string.prayer_maghrib)
                    }
                    PrayerTimeName.ISHA -> stringResource(R.string.prayer_isha)
                }
            },
            bold = true,
            header = true,
            iftarColumn = isImsakiye
        )
    }
}

@Composable
private fun PrayerCalendarRow(
    cache: CachedPrayerDay,
    dateLabel: String,
    accessibilityDate: String,
    highlighted: Boolean,
    ramadan: Boolean = false,
    ramadanDay: Int? = null,
    isIftar: Boolean = false
) {
    val prayerLabels = PrayerTimeName.entries.map { name ->
        val time = cache.prayerTimes.timeAt(name).format(PrayerTimes.TIME_FORMATTER)
        time
    }
    val fullDescription = buildString {
        append(accessibilityDate)
        ramadanDay?.let { append(", Ramazan'ın $it. günü") }
        if (ramadan) append(", Ramazan")
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
            bold = highlighted,
            iftarColumn = isIftar
        )
    }
}

@Composable
private fun PrayerTableRowShell(
    dayLabel: String,
    prayerLabels: List<String>,
    bold: Boolean,
    header: Boolean = false,
    iftarColumn: Boolean = false
) {
    val largeScale = LocalDensity.current.fontScale >= 1.5f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = if (header) 8.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayLabel,
            modifier = Modifier.width(if (iftarColumn) 66.dp else 54.dp),
            style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = if (bold || header) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Start
        )
        val scroll = rememberScrollState()
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (largeScale) Modifier.horizontalScroll(scroll) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            prayerLabels.forEachIndexed { index, label ->
                val emphasized = iftarColumn && index == PrayerTimeName.MAGHRIB.ordinal
                Text(
                    text = label,
                    modifier = Modifier
                        .then(if (largeScale) Modifier.width(68.dp) else Modifier.weight(1f))
                        .then(
                            if (emphasized) Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(5.dp))
                                .padding(vertical = 3.dp)
                            else Modifier
                        ),
                    textAlign = TextAlign.Center,
                    maxLines = if (header) 2 else 1,
                    style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                    fontWeight = if (bold || emphasized || header) FontWeight.Bold else FontWeight.Medium,
                    color = if (emphasized) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
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
private fun CalendarRetry(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.monthly_load_failed))
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
private val DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("d\nEEE", Locale.forLanguageTag("tr-TR"))
private val IMSAKIYE_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr-TR"))
private val WEEKDAY_SHORT_FORMATTER = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("tr-TR"))
private val ACCESSIBLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM EEEE", Locale.forLanguageTag("tr-TR"))
