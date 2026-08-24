package com.example.namazvakti

import android.app.Application
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

interface PrayerRefreshScheduler {
    suspend fun enqueueRefresh(context: android.content.Context, force: Boolean)
    suspend fun scheduleBoundary(context: android.content.Context)
}

private class DefaultPrayerRefreshScheduler : PrayerRefreshScheduler {
    override suspend fun enqueueRefresh(context: android.content.Context, force: Boolean) =
        PrayerWidgetScheduler.enqueueRefresh(context, force)

    override suspend fun scheduleBoundary(context: android.content.Context) =
        PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(context)
}

class PrayerViewModel @JvmOverloads constructor(
    application: Application,
    private val store: PrayerPreferences = (application as NamazVaktiApp).container.store,
    private val repository: PrayerRefreshRepository = (application as NamazVaktiApp).container.repository,
    private val scheduler: PrayerRefreshScheduler = DefaultPrayerRefreshScheduler(),
    private val widgetUpdater: PrayerWidgetUpdatePort = PrayerWidgetUpdater,
    private val timeProvider: PrayerTimeProvider = (application as NamazVaktiApp).container.timeProvider,
    private val settings: PrayerCalculationSettings = (application as NamazVaktiApp).container.settings,
    private val testScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cachePolicy: PrayerCachePolicy = PrayerCachePolicy()
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<PrayerUiState>(PrayerUiState.Loading)
    val state: StateFlow<PrayerUiState> = _state.asStateFlow()
    private var actionJob: Job? = null

    init { load() }

    fun load() = launchLatest {
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
            _state.value = PrayerUiState.Ready(
                location = snapshot.first,
                freshness = if (snapshot.third) Freshness.Fresh else Freshness.Stale,
                cache = snapshot.second
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = PrayerUiState.Error(null)
        }
    }

    fun refresh() = launchLatest { refreshInternal() }

    fun selectCity(option: PrayerLocationConfig.CityOption) = launchLatest {
        try {
            val location = PrayerLocation(option.city, option.country, option.displayCity)
            withContext(ioDispatcher) {
                store.saveLocation(location)
            }
            refreshInternal()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = PrayerUiState.Error(null)
        }
    }

    private fun scope(): CoroutineScope = testScope ?: viewModelScope

    private fun launchLatest(block: suspend () -> Unit): Job {
        actionJob?.cancel()
        return scope().launch { block() }.also { actionJob = it }
    }

    private suspend fun refreshInternal() {
        val snapshot = try {
            withContext(ioDispatcher) { store.readLocation() to store.readCache() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (snapshot == null) {
            _state.value = PrayerUiState.Error(null)
            return
        }
        val (location, cache) = snapshot
        val currentFreshness = if (
            cachePolicy.isFresh(cache, location, settings, timeProvider.now())
        ) Freshness.Fresh else Freshness.Stale
        _state.value = PrayerUiState.Ready(
            location, currentFreshness, OperationState.Refreshing, cache
        )
        try {
            when (val result = withContext(ioDispatcher) { repository.refreshAndCache() }) {
                is RefreshResult.Success -> {
                    widgetUpdater.updateAll(getApplication())
                    scheduler.scheduleBoundary(getApplication())
                    _state.value = PrayerUiState.Ready(
                        result.cache.location,
                        Freshness.Fresh,
                        OperationState.Refreshed,
                        result.cache,
                        result.origin
                    )
                }
                is RefreshResult.StaleCache -> _state.value = PrayerUiState.Ready(
                    result.cache.location,
                    Freshness.Stale,
                    OperationState.RefreshFailed,
                    result.cache
                )
                is RefreshResult.Failure -> _state.value = PrayerUiState.Error(location)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = PrayerUiState.Error(location)
        }
    }
}
