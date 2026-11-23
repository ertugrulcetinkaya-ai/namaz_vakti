package com.example.namazvakti.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.namazvakti.BuildConfig
import com.example.namazvakti.R
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.data.local.DiyanetDataSourceName
import com.example.namazvakti.data.local.DiyanetSourceHealth
import com.example.namazvakti.data.local.PrayerCacheDateBounds
import com.example.namazvakti.domain.model.PrayerLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    location: PrayerLocation?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    var sourceHealth by remember(location) { mutableStateOf<DiyanetSourceHealth?>(null) }
    var sourceHealthLoaded by remember(location) { mutableStateOf(false) }
    var sourceHealthFailed by remember(location) { mutableStateOf(false) }
    var cacheBounds by remember(location) { mutableStateOf<PrayerCacheDateBounds?>(null) }
    var cacheBoundsLoaded by remember(location) { mutableStateOf(false) }
    var cacheBoundsFailed by remember(location) { mutableStateOf(false) }

    LaunchedEffect(location, container) {
        sourceHealthLoaded = false
        sourceHealthFailed = false
        cacheBoundsLoaded = false
        cacheBoundsFailed = false

        try {
            sourceHealth = withContext(Dispatchers.IO) {
                container.diyanetSourceStateStore.readSourceHealth()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            sourceHealthFailed = true
        } finally {
            sourceHealthLoaded = true
        }

        try {
            cacheBounds = withContext(Dispatchers.IO) {
                val selectedLocation = location ?: container.store.readLocation()
                container.store.readCacheDateBounds(selectedLocation)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cacheBoundsFailed = true
        } finally {
            cacheBoundsLoaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AboutSection(title = stringResource(R.string.about_app_section)) {
                    Text(
                        stringResource(
                            R.string.about_version_and_package,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.APPLICATION_ID
                        )
                    )
                    AboutExternalLink(R.string.about_license_link, R.string.about_license_url)
                }
            }
            item {
                AboutSection(title = stringResource(R.string.about_attributions_title)) {
                    Text(stringResource(R.string.about_attributions_intro))
                    Text(
                        stringResource(R.string.about_diyanet_attribution),
                        fontWeight = FontWeight.Medium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        AboutExternalLink(R.string.about_mtopal_link, R.string.about_mtopal_url)
                        AboutExternalLink(R.string.about_mtopal_docs_link, R.string.about_mtopal_docs_url)
                        AboutExternalLink(R.string.about_mirror_link, R.string.about_mirror_url)
                    }
                    Text(stringResource(R.string.about_aladhan_attribution), fontWeight = FontWeight.Medium)
                    AboutExternalLink(R.string.about_aladhan_link, R.string.about_aladhan_url)
                    Text(stringResource(R.string.about_province_attribution), fontWeight = FontWeight.Medium)
                    AboutExternalLink(R.string.about_province_data_link, R.string.about_province_data_url)
                    Text(stringResource(R.string.about_district_attribution), fontWeight = FontWeight.Medium)
                    AboutExternalLink(R.string.about_codab_link, R.string.about_codab_url)
                }
            }
            item {
                AboutSection(title = stringResource(R.string.about_privacy_title)) {
                    Text(stringResource(R.string.about_privacy_summary_one))
                    Text(stringResource(R.string.about_privacy_summary_two))
                    Text(stringResource(R.string.about_privacy_summary_three))
                    AboutExternalLink(R.string.about_privacy_link, R.string.about_privacy_url)
                }
            }
            item {
                AboutSection(title = stringResource(R.string.about_source_health_title)) {
                    Text(
                        stringResource(
                            R.string.about_selected_location,
                            location?.displayName ?: stringResource(R.string.about_location_unavailable)
                        ),
                        fontWeight = FontWeight.Medium
                    )
                    when {
                        !sourceHealthLoaded -> Text(stringResource(R.string.about_loading))
                        sourceHealthFailed -> Text(stringResource(R.string.about_source_health_unavailable))
                        sourceHealth == null -> Text(stringResource(R.string.about_source_health_none))
                        else -> Text(sourceHealthDescription(sourceHealth!!))
                    }
                }
            }
            item {
                AboutSection(title = stringResource(R.string.about_cache_title)) {
                    when {
                        !cacheBoundsLoaded -> Text(stringResource(R.string.about_loading))
                        cacheBoundsFailed -> Text(stringResource(R.string.about_cache_unavailable))
                        cacheBounds == null -> Text(stringResource(R.string.about_cache_empty))
                        else -> Text(cacheBoundsDescription(cacheBounds!!))
                    }
                }
            }
            item {
                AboutSection(title = stringResource(R.string.about_repository_title)) {
                    Text(stringResource(R.string.about_repository_description))
                    AboutExternalLink(R.string.about_repository_link, R.string.about_repository_url)
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun AboutExternalLink(label: Int, url: Int) {
    val context = LocalContext.current
    val linkUrl = stringResource(url)
    TextButton(
        onClick = { openExternalLink(context, linkUrl) },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(stringResource(label))
    }
}

private fun openExternalLink(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_link_unavailable, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun sourceHealthDescription(health: DiyanetSourceHealth): String {
    val source = when (health.source) {
        DiyanetDataSourceName.MIRROR -> stringResource(R.string.about_source_health_mirror)
        DiyanetDataSourceName.MTOPAL -> stringResource(R.string.about_source_health_mtopal)
    }
    val time = Instant.ofEpochMilli(health.succeededAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .format(ABOUT_DATE_TIME_FORMATTER)
    return stringResource(R.string.about_source_health_success, source, time)
}

@Composable
private fun cacheBoundsDescription(bounds: PrayerCacheDateBounds): String {
    val first = bounds.firstDate.format(ABOUT_DATE_FORMATTER)
    val last = bounds.lastDate.format(ABOUT_DATE_FORMATTER)
    return stringResource(R.string.about_cache_range, first, last)
}

private val ABOUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))
private val ABOUT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr-TR"))
