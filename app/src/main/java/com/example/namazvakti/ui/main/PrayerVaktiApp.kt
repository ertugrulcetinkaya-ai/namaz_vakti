package com.example.namazvakti.ui.main

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import com.example.namazvakti.notification.PrayerNotificationPublisher
import com.example.namazvakti.widget.alarm.PrayerExactAlarmAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun PrayerVaktiApp(viewModel: PrayerViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val notificationSettingsError by viewModel.notificationSettingsError.collectAsStateWithLifecycle()
    val monthlyState by viewModel.monthlyState.collectAsStateWithLifecycle()
    val imsakiyeState by viewModel.imsakiyeState.collectAsStateWithLifecycle()
    val ramadanRange by viewModel.ramadanRange.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val location = when (val current = state) {
        is PrayerUiState.Ready -> current.location
        is PrayerUiState.Error -> current.location
        PrayerUiState.Loading -> null
    }
    val timezone = location?.timezone ?: PrayerTimeProvider.DEFAULT_ZONE
    var now by remember(timezone) { mutableStateOf(ZonedDateTime.now(timezone)) }
    val today = now.withZoneSameInstant(timezone).toLocalDate()
    val autoRefreshMinute = now.truncatedTo(ChronoUnit.MINUTES)
    var lastObservedDate by remember { mutableStateOf(today) }
    var backStack by rememberSaveable(stateSaver = AppBackStack.Saver) { mutableStateOf(AppBackStack()) }
    var showNotificationSettings by rememberSaveable { mutableStateOf(false) }
    var notificationDraft by rememberSaveable(stateSaver = NotificationSettingsSaver) {
        mutableStateOf<PrayerNotificationSettings?>(null)
    }
    var pendingNotificationSettings by rememberSaveable(stateSaver = NotificationSettingsSaver) {
        mutableStateOf<PrayerNotificationSettings?>(null)
    }
    val dismissNotificationSettings = {
        showNotificationSettings = false
        notificationDraft = null
    }
    var resumeVersion by remember { mutableIntStateOf(0) }
    val autoRefreshPolicy = remember { AutoRefreshPolicy() }

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

    LaunchedEffect(state, today, lastObservedDate, afterIsha, autoRefreshMinute) {
        if (lastObservedDate != today) return@LaunchedEffect
        val ready = state as? PrayerUiState.Ready ?: return@LaunchedEffect
        if (ready.operation == OperationState.Refreshing) return@LaunchedEffect

        val missingToday = ready.cache?.date != today
        val missingTomorrowAfterIsha = afterIsha && ready.tomorrowCache?.date != today.plusDays(1)
        val reason = when {
            missingToday -> Reason.MISSING_TODAY
            missingTomorrowAfterIsha -> Reason.MISSING_TOMORROW
            else -> return@LaunchedEffect
        }
        val key = AutoRefreshKey(today, ready.location.cacheKey, reason)
        val lastAttemptFailed = ready.operation == OperationState.RefreshFailed
        if (autoRefreshPolicy.shouldRefresh(key, now.toInstant(), lastAttemptFailed)) {
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
            dismissNotificationSettings()
        } else {
            val allowedSettings = pending.copy(
                notificationsEnabled = pending.notificationsEnabled && notificationSettings.notificationsEnabled,
                religiousDaysEnabled = pending.religiousDaysEnabled && notificationSettings.religiousDaysEnabled
            )
            notificationDraft = allowedSettings
            viewModel.updateNotificationSettings(allowedSettings)
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
    val requestExactAlarm = { openExactAlarmSettings(context) }

    val screen = backStack.current
    val goBack: () -> Unit = {
        backStack = backStack.pop()
        if (backStack.current == null) activity?.finish()
    }
    BackHandler(enabled = screen != null, onBack = goBack)

    val openImsakiye = {
        viewModel.loadImsakiye()
        backStack = backStack.push(Screen.Imsakiye)
    }
    val openReligiousDays = { backStack = backStack.push(Screen.ReligiousDays) }

    when (screen) {
        is Screen.Cities -> CitySelectionScreen(
            selectedLocation = location,
            selectionStep = screen.step,
            selectedProvinceCity = screen.provinceCity,
            onBack = goBack,
            onSelectProvince = { city ->
                backStack = backStack.push(Screen.Cities(step = 2, provinceCity = city))
            },
            onSelectDistrict = { option, districtId ->
                backStack = backStack.pop().pop()
                viewModel.selectDistrict(option, districtId)
            }
        )
        Screen.ReligiousDays -> ReligiousDaysScreen(
            today = today,
            eventsForYear = viewModel::religiousDaysFor,
            onBack = goBack
        )
        Screen.Monthly -> MonthlyPrayerScreen(
            state = monthlyState,
            ramadanRange = ramadanRange,
            today = today,
            onBack = goBack,
            onOpenReligiousDays = openReligiousDays,
            onOpenImsakiye = openImsakiye,
            onMonthChange = viewModel::loadMonth,
            onRetry = { viewModel.loadMonth(monthlyState.month) }
        )
        Screen.Imsakiye -> ImsakiyeScreen(
            state = imsakiyeState,
            today = today,
            onBack = goBack,
            onRetry = viewModel::loadImsakiye
        )
        Screen.Home -> PrayerHomeScreen(
            state = state,
            now = now,
            onRefresh = viewModel::refresh,
            onOpenCities = { backStack = backStack.push(Screen.Cities(step = 1, provinceCity = null)) },
            onOpenMonthly = {
                viewModel.loadMonth(YearMonth.from(today))
                backStack = backStack.push(Screen.Monthly)
            },
            onOpenReligiousDays = openReligiousDays,
            onOpenImsakiye = openImsakiye,
            ramadanRange = ramadanRange,
            onOpenNotificationSettings = { showNotificationSettings = true },
            exactAlarmNeeded = exactAlarmNeeded,
            onRequestExactAlarm = requestExactAlarm
        )
        null -> Unit
    }

    if (showNotificationSettings) {
        PrayerNotificationSettingsSheet(
            settings = notificationDraft ?: notificationSettings,
            onSettingsChange = { notificationDraft = it },
            saveFailed = notificationSettingsError,
            exactAlarmNeeded = exactAlarmNeeded,
            onDismiss = dismissNotificationSettings,
            onRequestExactAlarm = requestExactAlarm,
            onOpenSoundAndVibrationSettings = {
                PrayerNotificationPublisher.createChannels(context)
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, PrayerNotificationPublisher.CHANNEL_AT_TIME)
                    )
                } catch (_: ActivityNotFoundException) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    } catch (_: ActivityNotFoundException) {
                        // This device does not expose notification settings.
                    }
                }
            },
            onSave = { settings ->
                if (
                    (settings.notificationsEnabled || settings.religiousDaysEnabled) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    pendingNotificationSettings = settings
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.updateNotificationSettings(settings)
                    dismissNotificationSettings()
                }
            }
        )
    }
}

private const val CLOCK_TICK_MILLIS = 1_000L
