package com.example.namazvakti.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.policy.PrayerCountdown
import com.example.namazvakti.domain.policy.PrayerCountdownCalculator
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerHomeScreen(
    state: PrayerUiState,
    now: ZonedDateTime,
    onRefresh: () -> Unit,
    onOpenCities: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    exactAlarmNeeded: Boolean,
    onRequestExactAlarm: () -> Unit
) {
    val ready = state as? PrayerUiState.Ready
    val location = ready?.location ?: (state as? PrayerUiState.Error)?.location
    val zone = location?.timezone ?: now.zone
    val localNow = now.withZoneSameInstant(zone)
    val today = localNow.toLocalDate()
    val todayCache = ready?.cache?.takeIf { it.date == today }
    val countdown = todayCache?.let { cache ->
        PrayerCountdownCalculator.calculate(
            today = cache.prayerTimes,
            date = cache.date,
            timezone = cache.timezone,
            tomorrow = ready.tomorrowCache
                ?.takeIf { it.date == cache.date.plusDays(1) }
                ?.prayerTimes,
            now = localNow
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenNotificationSettings,
                        enabled = ready != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.notification_settings_open)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = ready?.operation == OperationState.Refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = onOpenCities,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = location?.displayCity ?: stringResource(R.string.home_city_hint),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                        Text(
                            text = today.format(TURKISH_DATE_FORMATTER),
                            style = MaterialTheme.typography.titleMedium
                        )
                        todayCache?.hijriText?.takeIf(String::isNotBlank)?.let { hijri ->
                            Text(
                                text = hijri,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    NextPrayerCard(countdown = countdown)
                }

                item {
                    Text(
                        text = stringResource(R.string.today_prayer_times),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (todayCache != null && countdown != null) {
                    items(prayerRows(todayCache.prayerTimes), key = { it.first.name }) { (prayer, time) ->
                        PrayerTimeRow(
                            prayer = prayer,
                            time = time,
                            active = prayer == countdown.activePrayer,
                            past = localNow.toLocalTime() >= time && prayer != countdown.activePrayer
                        )
                    }
                    item { CacheFooter(ready, todayCache) }
                } else {
                    item {
                        StateMessage(state = state, onRefresh = onRefresh)
                    }
                }
                if (exactAlarmNeeded) {
                    item { ExactAlarmWarning(onRequestExactAlarm) }
                }
            }
        }
    }
}

@Composable
private fun ExactAlarmWarning(onRequestExactAlarm: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.precise_widget_updates_disabled),
                style = MaterialTheme.typography.bodyMedium
            )
            androidx.compose.material3.OutlinedButton(
                onClick = onRequestExactAlarm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.enable_precise_widget_updates))
            }
        }
    }
}

@Composable
private fun NextPrayerCard(countdown: PrayerCountdown?) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.next_prayer_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (countdown?.nextPrayer != null && countdown.nextPrayerAt != null && countdown.remaining != null) {
                val prayerLabel = stringResource(countdown.nextPrayer.stringId())
                Text(
                    text = stringResource(
                        R.string.prayer_time_with_time,
                        prayerLabel,
                        countdown.nextPrayerAt.format(PrayerTimes.TIME_FORMATTER)
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = countdown.remaining.formatCountdown(context, includeDays = true),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = countdown.accessibilityDescription(context, prayerLabel)
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFeatureSettings = "'tnum'"
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = stringResource(R.string.next_prayer_unknown),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.tomorrow_times_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.prayer_countdown_unknown_accessibility),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = context.getString(R.string.prayer_countdown_unknown_accessibility)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = countdown?.let {
                    stringResource(R.string.current_prayer_value, stringResource(it.activePrayer.stringId()))
                } ?: stringResource(R.string.next_prayer_unknown),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PrayerTimeRow(
    prayer: PrayerTimeName,
    time: java.time.LocalTime,
    active: Boolean,
    past: Boolean
) {
    val color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (past) 0.55f else 1f),
        colors = CardDefaults.cardColors(containerColor = color),
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (active) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.active_prayer),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(Modifier.width(24.dp))
            }
            Text(
                text = stringResource(prayer.stringId()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = time.format(PrayerTimes.TIME_FORMATTER),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "'tnum'"),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CacheFooter(state: PrayerUiState.Ready, cache: CachedPrayerDay) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val status = when {
            state.operation == OperationState.Refreshing -> stringResource(R.string.refreshing)
            state.operation == OperationState.Refreshed && state.refreshOrigin == com.example.namazvakti.domain.model.RefreshOrigin.LOCAL_CACHE ->
                stringResource(R.string.offline_data)
            state.operation == OperationState.RefreshFailed || state.freshness == Freshness.Stale ->
                stringResource(R.string.stale_data)
            state.operation == OperationState.Refreshed -> stringResource(R.string.refresh_success)
            else -> null
        }
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        val school = if (cache.settings.school == 1) {
            stringResource(R.string.school_hanafi)
        } else {
            stringResource(R.string.school_standard)
        }
        val source = when (cache.source) {
            com.example.namazvakti.domain.model.PrayerDataSource.ALADHAN ->
                stringResource(R.string.prayer_source_aladhan)
            com.example.namazvakti.domain.model.PrayerDataSource.LEGACY_CACHE ->
                stringResource(R.string.prayer_source_legacy_cache)
        }
        val metadata = stringResource(
            R.string.cache_metadata,
            source,
            cache.settings.method,
            school,
            cache.fetchedAt.atZone(cache.timezone).format(METADATA_TIME_FORMATTER)
        )
        Text(
            text = metadata,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StateMessage(state: PrayerUiState, onRefresh: () -> Unit) {
    val text = when (state) {
        PrayerUiState.Loading -> stringResource(R.string.refreshing)
        is PrayerUiState.Ready -> stringResource(R.string.no_prayer_data)
        is PrayerUiState.Error -> when (state.error) {
            com.example.namazvakti.domain.model.PrayerError.Network -> stringResource(R.string.network_error)
            is com.example.namazvakti.domain.model.PrayerError.Service -> stringResource(R.string.service_error)
            com.example.namazvakti.domain.model.PrayerError.InvalidData -> stringResource(R.string.invalid_data_error)
            com.example.namazvakti.domain.model.PrayerError.Storage,
            com.example.namazvakti.domain.model.PrayerError.TransientStorage,
            com.example.namazvakti.domain.model.PrayerError.PermanentStorage -> stringResource(R.string.storage_error)
            com.example.namazvakti.domain.model.PrayerError.Unknown -> stringResource(R.string.refresh_error)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        if (state is PrayerUiState.Error) {
            androidx.compose.material3.Button(onClick = onRefresh) {
                Text(stringResource(R.string.retry))
            }
        } else if (state == PrayerUiState.Loading) {
            CircularProgressIndicator()
        }
    }
}

private fun prayerRows(times: PrayerTimes): List<Pair<PrayerTimeName, java.time.LocalTime>> =
    PrayerTimeName.entries.map { it to times.timeAt(it) }

private fun PrayerTimeName.stringId(): Int = when (this) {
    PrayerTimeName.IMSAK -> R.string.prayer_imsak
    PrayerTimeName.SUNRISE -> R.string.prayer_sunrise
    PrayerTimeName.DHUHR -> R.string.prayer_dhuhr
    PrayerTimeName.ASR -> R.string.prayer_asr
    PrayerTimeName.MAGHRIB -> R.string.prayer_maghrib
    PrayerTimeName.ISHA -> R.string.prayer_isha
}

private fun Duration.formatCountdown(context: android.content.Context, includeDays: Boolean): String {
    val totalSeconds = seconds.coerceAtLeast(0)
    val days = totalSeconds / SECONDS_PER_DAY
    val hours = totalSeconds % SECONDS_PER_DAY / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (includeDays && days > 0) {
        context.getString(R.string.prayer_countdown_display_days, days, hours, minutes, seconds)
    } else {
        context.getString(
            R.string.prayer_countdown_display,
            days * HOURS_PER_DAY + hours,
            minutes,
            seconds
        )
    }
}

private fun PrayerCountdown.accessibilityDescription(
    context: android.content.Context,
    prayerLabel: String
): String {
    val duration = remaining ?: return context.getString(R.string.prayer_countdown_unknown_accessibility)
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    val totalMinutes = (totalSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationText = if (hours > 0) {
        val hoursText = context.resources.getQuantityString(R.plurals.countdown_hours, hours.toInt(), hours.toInt())
        val minutesText = context.resources.getQuantityString(R.plurals.countdown_minutes, minutes.toInt(), minutes.toInt())
        context.getString(R.string.prayer_countdown_duration_hours, hoursText, minutesText)
    } else {
        context.resources.getQuantityString(R.plurals.countdown_minutes, minutes.toInt(), minutes.toInt())
    }
    val description = if (hours > 0) {
        R.string.prayer_countdown_accessibility_hours
    } else {
        R.string.prayer_countdown_accessibility_minutes
    }
    return context.getString(description, prayerLabel, durationText)
}

private val TURKISH_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy EEEE", Locale.forLanguageTag("tr-TR"))
private val METADATA_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.forLanguageTag("tr-TR"))

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
private const val HOURS_PER_DAY = 24L
