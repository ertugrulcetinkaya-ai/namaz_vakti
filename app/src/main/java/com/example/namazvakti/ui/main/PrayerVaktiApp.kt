package com.example.namazvakti.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.widget.alarm.PrayerExactAlarmAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.ZonedDateTime

@Composable
fun PrayerVaktiApp(viewModel: PrayerViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val notificationSettingsError by viewModel.notificationSettingsError.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val location = when (val current = state) {
        is PrayerUiState.Ready -> current.location
        is PrayerUiState.Error -> current.location
        PrayerUiState.Loading -> null
    }
    val timezone = location?.timezone ?: PrayerTimeProvider.DEFAULT_ZONE
    var now by remember(timezone) { mutableStateOf(ZonedDateTime.now(timezone)) }
    val today = now.withZoneSameInstant(timezone).toLocalDate()
    var lastObservedDate by remember { mutableStateOf(today) }
    var showCities by rememberSaveable { mutableStateOf(false) }
    var showNotificationSettings by rememberSaveable { mutableStateOf(false) }
    var pendingNotificationSettings by remember { mutableStateOf<PrayerNotificationSettings?>(null) }
    var resumeVersion by remember { mutableIntStateOf(0) }
    val requestedRefreshDates = remember { mutableSetOf<LocalDate>() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeVersion += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner, timezone) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                now = ZonedDateTime.now(timezone)
                delay(CLOCK_TICK_MILLIS)
            }
        }
    }

    LaunchedEffect(today) {
        if (lastObservedDate != today) {
            viewModel.load().join()
            lastObservedDate = today
        }
    }

    val readyState = state as? PrayerUiState.Ready
    val afterIsha = readyState?.cache?.takeIf { it.date == today }?.let { cache ->
        now.withZoneSameInstant(readyState.location.timezone).toLocalTime() >= cache.prayerTimes.isha
    } == true

    LaunchedEffect(state, today, lastObservedDate, afterIsha) {
        if (lastObservedDate != today) return@LaunchedEffect
        val ready = state as? PrayerUiState.Ready ?: return@LaunchedEffect
        val cache = ready.cache
        val missingToday = cache?.date != today
        val missingTomorrowAfterIsha = afterIsha && ready.tomorrowCache?.date != today.plusDays(1)
        if ((missingToday || missingTomorrowAfterIsha) && requestedRefreshDates.add(today)) {
            viewModel.refresh()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingNotificationSettings ?: return@rememberLauncherForActivityResult
        pendingNotificationSettings = null
        if (granted) {
            viewModel.updateNotificationSettings(pending)
            showNotificationSettings = false
        } else {
            viewModel.updateNotificationSettings(pending.copy(notificationsEnabled = false))
            Toast.makeText(
                context,
                R.string.notification_prayer_permission_denied,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(notificationSettingsError) {
        if (notificationSettingsError) {
            Toast.makeText(context, R.string.notification_settings_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    val exactAlarmNeeded = remember(resumeVersion) {
        PrayerExactAlarmAccess.requiresUserAccess() &&
            !PrayerExactAlarmAccess.canScheduleExactAlarms(context)
    }
    val requestExactAlarm = {
        context.startActivity(PrayerExactAlarmAccess.requestAccessIntent(context))
    }

    BackHandler(enabled = showCities) { showCities = false }

    if (showCities) {
        CitySelectionScreen(
            selectedLocation = location,
            onBack = { showCities = false },
            onSelectCity = { option ->
                showCities = false
                viewModel.selectCity(option)
            }
        )
    } else {
        PrayerHomeScreen(
            state = state,
            now = now,
            onRefresh = viewModel::refresh,
            onOpenCities = { showCities = true },
            onOpenNotificationSettings = { showNotificationSettings = true },
            exactAlarmNeeded = exactAlarmNeeded,
            onRequestExactAlarm = requestExactAlarm
        )
    }

    if (showNotificationSettings && state is PrayerUiState.Ready) {
        PrayerNotificationSettingsSheet(
            settings = notificationSettings,
            saveFailed = notificationSettingsError,
            exactAlarmNeeded = exactAlarmNeeded,
            onDismiss = { showNotificationSettings = false },
            onRequestExactAlarm = requestExactAlarm,
            onSave = { settings ->
                if (
                    settings.notificationsEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    pendingNotificationSettings = settings
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.updateNotificationSettings(settings)
                    showNotificationSettings = false
                }
            }
        )
    }
}

private const val CLOCK_TICK_MILLIS = 1_000L
