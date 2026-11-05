package com.example.namazvakti.ui.main

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.DiyanetReligiousDay
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReligiousDaysScreen(
    today: LocalDate,
    onBack: () -> Unit,
    eventsForYear: (Int) -> List<DiyanetReligiousDay> = DiyanetReligiousDays::forYear
) {
    var selectedYear by rememberSaveable {
        mutableIntStateOf(today.year.coerceIn(DiyanetReligiousDays.FIRST_YEAR, DiyanetReligiousDays.LAST_YEAR))
    }
    val years = remember(selectedYear, today) {
        buildList {
            add(selectedYear)
            val daysUntilYearEnd = ChronoUnit.DAYS.between(today, today.withMonth(12).withDayOfMonth(31))
            if (selectedYear == today.year && daysUntilYearEnd < 60 && selectedYear < DiyanetReligiousDays.LAST_YEAR) {
                add(selectedYear + 1)
            }
        }
    }
    val events = remember(years) {
        years.flatMap(eventsForYear)
            .distinctBy { it.name to it.date }
            .sortedBy { it.date }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(events, today) {
        if (events.isEmpty()) return@LaunchedEffect
        val index = events.indexOfFirst { today in it.dates }
            .takeIf { it >= 0 }
            ?: events.indexOfFirst { event -> event.dates.any { !it.isBefore(today) } }.coerceAtLeast(0)
        listState.animateScrollToItem(index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.religious_days_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = selectedYear > DiyanetReligiousDays.FIRST_YEAR,
                        onClick = { selectedYear-- }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.previous_year))
                    }
                    Text(selectedYear.toString(), style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        enabled = selectedYear < DiyanetReligiousDays.LAST_YEAR,
                        onClick = { selectedYear++ }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.next_year))
                    }
                }
            )
        }
    ) { insets ->
        if (events.isEmpty()) {
            Text(
                stringResource(R.string.religious_days_empty),
                modifier = Modifier.fillMaxSize().padding(insets).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events, key = { "${it.name}-${it.date}" }) { event ->
                    ReligiousDayListItem(event = event, today = today)
                }
            }
        }
    }
}

@Composable
private fun ReligiousDayListItem(event: DiyanetReligiousDay, today: LocalDate) {
    val isToday = today in event.dates
    val nextDate = event.dates.filter { !it.isBefore(today) }.minOrNull()
    val isPast = nextDate == null
    val daysUntil = nextDate?.let { ChronoUnit.DAYS.between(today, it).toInt() }
    val alpha = if (isPast && !isToday) 0.55f else 1f
    val dateText = event.dates.joinToString(" / ") { it.format(EVENT_DATE_FORMATTER) }
    val status = when {
        isToday -> stringResource(R.string.religious_days_today)
        daysUntil != null && daysUntil > 0 -> stringResource(R.string.religious_days_remaining, daysUntil)
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isToday) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (event.isNight) {
                Text("☾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(event.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(dateText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            status?.let {
                Spacer(Modifier.width(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private val EVENT_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM EEEE", Locale.forLanguageTag("tr-TR"))
