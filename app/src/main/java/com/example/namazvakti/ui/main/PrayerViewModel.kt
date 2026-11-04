package com.example.namazvakti.ui.main

import android.app.Application
import com.example.namazvakti.app.NamazVaktiApp
import com.example.namazvakti.data.local.PrayerPreferences
import com.example.namazvakti.data.local.PrayerNotificationSettingsSource
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerLocationConfig
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.RamadanRange
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.domain.model.classifyPrayerError
import com.example.namazvakti.domain.model.classifyPrayerStorageError
import com.example.namazvakti.domain.policy.PrayerCachePolicy
import com.example.namazvakti.domain.port.PrayerRefreshRepository
import com.example.namazvakti.domain.port.PrayerRefreshScheduler
import com.example.namazvakti.domain.port.PrayerWidgetUpdatePort
import com.example.namazvakti.widget.PrayerWidgetUpdateAdapter
import com.example.namazvakti.widget.alarm.PrayerRefreshSchedulerAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

class PrayerViewModel @JvmOverloads constructor(
    application: Application,
    private val store: PrayerPreferences = (application as NamazVaktiApp).container.store,
    private val repository: PrayerRefreshRepository = (application as NamazVaktiApp).container.repository,
    private val scheduler: PrayerRefreshScheduler = PrayerRefreshSchedulerAdapter(application),
    private val widgetUpdater: PrayerWidgetUpdatePort = PrayerWidgetUpdateAdapter(application),
    private val timeProvider: PrayerTimeProvider = (application as NamazVaktiApp).container.timeProvider,
    private val settings: PrayerCalculationSettings = (application as NamazVaktiApp).container.settings,
    private val testScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cachePolicy: PrayerCachePolicy = PrayerCachePolicy(),
    private val notificationSettingsStore: PrayerNotificationSettingsSource =
        (application as NamazVaktiApp).container.notificationSettingsStore
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<PrayerUiState>(PrayerUiState.Loading)
    val state: StateFlow<PrayerUiState> = _state.asStateFlow()
    private val _notificationSettings = MutableStateFlow(PrayerNotificationSettings())
    val notificationSettings: StateFlow<PrayerNotificationSettings> = _notificationSettings.asStateFlow()
    private val _notificationSettingsError = MutableStateFlow(false)
    val notificationSettingsError: StateFlow<Boolean> = _notificationSettingsError.asStateFlow()
    private val _monthlyState = MutableStateFlow(MonthlyUiState(YearMonth.now()))
    val monthlyState: StateFlow<MonthlyUiState> = _monthlyState.asStateFlow()
    private val _imsakiyeState = MutableStateFlow(ImsakiyeUiState())
    val imsakiyeState: StateFlow<ImsakiyeUiState> = _imsakiyeState.asStateFlow()
    private val _ramadanRange = MutableStateFlow<RamadanRange?>(null)
    val ramadanRange: StateFlow<RamadanRange?> = _ramadanRange.asStateFlow()
    private var actionJob: Job? = null
    private var monthJob: Job? = null
    private var imsakiyeJob: Job? = null
    private var rangeJob: Job? = null
    private var requestedMonth: YearMonth? = null
    private var imsakiyeRequested = false

    init {
        load()
    }

    fun load(): Job {
        loadRamadanRange()
        return launchLatest {
        try {
            val snapshot = withContext(ioDispatcher) {
                val location = store.readLocation()
                val cache = store.readCache()
                Triple(
                    location,
                    cache,
                    cachePolicy.isFresh(cache, location, settings, timeProvider.now())
                )
            }
            val tomorrowCache = readTomorrowCache(
                snapshot.first,
                timeProvider.today(snapshot.first.timezone)
            )
            _notificationSettings.value = withContext(ioDispatcher) {
                notificationSettingsStore.read()
            }
            scheduler.scheduleNotifications()
            _state.value = PrayerUiState.Ready(
                location = snapshot.first,
                freshness = if (snapshot.third) Freshness.Fresh else Freshness.Stale,
                cache = snapshot.second,
                tomorrowCache = tomorrowCache
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = PrayerUiState.Error(null, classifyPrayerStorageError(e))
        }
        }
    }

    fun refresh() = launchLatest { refreshInternal() }

    fun updateNotificationSettings(settings: PrayerNotificationSettings) = launchLatest {
        try {
            withContext(ioDispatcher) { notificationSettingsStore.save(settings) }
            _notificationSettings.value = settings
            _notificationSettingsError.value = false
            scheduler.scheduleNotifications()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _notificationSettingsError.value = true
        }
    }

    fun selectCity(option: PrayerLocationConfig.CityOption) = launchLatest {
        try {
            val location = PrayerLocation(
                city = option.city,
                country = option.country,
                displayCity = option.displayCity,
                latitude = option.latitude,
                longitude = option.longitude
            )
            withContext(ioDispatcher) {
                store.saveLocation(location)
            }
            scheduler.scheduleNotifications()
            refreshInternal()
            requestedMonth?.let(::loadMonth)
            if (imsakiyeRequested) loadImsakiye()
            loadRamadanRange()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = PrayerUiState.Error(null, classifyPrayerStorageError(e))
        }
    }

    private fun scope(): CoroutineScope = testScope ?: viewModelScope

    fun loadMonth(month: YearMonth) {
        requestedMonth = month
        monthJob?.cancel()
        _monthlyState.value = MonthlyUiState(month, loading = true)
        monthJob = scope().launch {
            try {
                val result = withContext(ioDispatcher) { repository.ensureMonth(month) }
                _monthlyState.value = MonthlyUiState(
                    month = month,
                    days = result.days.sortedBy { it.date },
                    source = result.source,
                    error = result.error
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _monthlyState.value = MonthlyUiState(month, error = e)
            }
        }
    }

    fun loadRamadanRange() {
        rangeJob?.cancel()
        rangeJob = scope().launch {
            try {
                _ramadanRange.value = withContext(ioDispatcher) { repository.ramadanRange() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ramadanRange.value = null
            }
        }
    }

    fun loadImsakiye() {
        imsakiyeRequested = true
        imsakiyeJob?.cancel()
        _imsakiyeState.value = _imsakiyeState.value.copy(loading = true, error = null)
        imsakiyeJob = scope().launch {
            try {
                val range = _ramadanRange.value
                    ?: withContext(ioDispatcher) { repository.ramadanRange() }
                if (range == null) {
                    _imsakiyeState.value = ImsakiyeUiState(
                        error = IllegalStateException("Ramadan dates unavailable")
                    )
                    return@launch
                }
                _ramadanRange.value = range
                val location = withContext(ioDispatcher) { store.readLocation() }
                val lastMonth = YearMonth.from(range.endDate)
                val months = generateSequence(YearMonth.from(range.startDate)) { month ->
                    if (month < lastMonth) month.plusMonths(1) else null
                }.toList()
                val monthResults = months.map { month ->
                    withContext(ioDispatcher) { repository.ensureMonth(month) }
                }
                val days = monthResults.flatMap { it.days }
                    .filter { it.date in range.startDate..range.endDate }
                    .distinctBy { it.date }
                    .sortedBy { it.date }
                _imsakiyeState.value = ImsakiyeUiState(
                    location = location,
                    range = range,
                    days = days,
                    error = if (days.size < range.dayCount) {
                        monthResults.firstNotNullOfOrNull { it.error }
                    } else {
                        null
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imsakiyeState.value = _imsakiyeState.value.copy(loading = false, error = e)
            }
        }
    }

    private fun launchLatest(block: suspend () -> Unit): Job {
        actionJob?.cancel()
        return scope().launch { block() }.also { actionJob = it }
    }

    private suspend fun refreshInternal() {
        val snapshot = try {
            withContext(ioDispatcher) { store.readLocation() to store.readCache() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = PrayerUiState.Error(null, classifyPrayerStorageError(e))
            return
        }
        val (location, cache) = snapshot
        val today = timeProvider.today(location.timezone)
        val tomorrowCache = readTomorrowCache(location, today)
        val currentFreshness = if (
            cachePolicy.isFresh(cache, location, settings, timeProvider.now())
        ) Freshness.Fresh else Freshness.Stale
        _state.value = PrayerUiState.Ready(
            location,
            currentFreshness,
            OperationState.Refreshing,
            cache,
            tomorrowCache = tomorrowCache
        )
        try {
            when (val result = withContext(ioDispatcher) { repository.refreshAndCache() }) {
                is RefreshResult.Success -> {
                    val tomorrowCache = readTomorrowCache(
                        result.cache.location,
                        result.cache.date
                    )
                    widgetUpdater.updateAll()
                    scheduler.scheduleBoundary()
                    scheduler.scheduleNotifications()
                    loadRamadanRange()
                    _state.value = PrayerUiState.Ready(
                        result.cache.location,
                        Freshness.Fresh,
                        OperationState.Refreshed,
                        result.cache,
                        result.origin,
                        null,
                        tomorrowCache
                    )
                }
                is RefreshResult.StaleCache -> {
                    val tomorrowCache = readTomorrowCache(
                        result.cache.location,
                        result.cache.date
                    )
                    _state.value = PrayerUiState.Ready(
                        result.cache.location,
                        Freshness.Stale,
                        OperationState.RefreshFailed,
                        result.cache,
                        null,
                        result.error,
                        tomorrowCache
                    )
                }
                is RefreshResult.Failure -> _state.value = PrayerUiState.Error(
                    location,
                    result.error
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = PrayerUiState.Error(location, classifyPrayerError(e))
        }
    }

    private suspend fun readTomorrowCache(
        location: PrayerLocation,
        date: java.time.LocalDate
    ) = try {
        withContext(ioDispatcher) {
            store.readCache(date.plusDays(1), location, settings)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
