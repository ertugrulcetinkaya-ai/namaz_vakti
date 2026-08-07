package com.example.namazvakti

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface PrayerRefreshScheduler {
    suspend fun enqueueRefresh(context: android.content.Context, force: Boolean)
    suspend fun scheduleBoundary(context: android.content.Context)
}

private class WorkManagerPrayerRefreshScheduler : PrayerRefreshScheduler {
    override suspend fun enqueueRefresh(context: android.content.Context, force: Boolean) =
        PrayerWidgetScheduler.enqueueRefresh(context, force)

    override suspend fun scheduleBoundary(context: android.content.Context) =
        PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(context)
}

class PrayerViewModel @JvmOverloads constructor(
    application: Application,
    private val store: PrayerPreferences = (application as NamazVaktiApp).container.store,
    private val repository: PrayerRefreshRepository = (application as NamazVaktiApp).container.repository,
    private val scheduler: PrayerRefreshScheduler = WorkManagerPrayerRefreshScheduler(),
    private val widgetUpdater: PrayerWidgetUpdatePort = PrayerWidgetUpdater,
    private val timeProvider: PrayerTimeProvider = (application as NamazVaktiApp).container.timeProvider,
    private val settings: PrayerCalculationSettings = (application as NamazVaktiApp).container.settings,
    private val testScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<PrayerUiState>(PrayerUiState.Loading)
    val state: StateFlow<PrayerUiState> = _state.asStateFlow()

    init { load() }

    fun load() = scope().launch {
        try {
            val snapshot = withContext(ioDispatcher) {
                val location = store.readLocation()
                val cache = store.readCache()
                location to (cache == null || !cache.matches(timeProvider.today(), location, settings))
            }
            _state.value = PrayerUiState.Ready(
                snapshot.first,
                if (snapshot.second) Freshness.Stale else Freshness.Fresh
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = PrayerUiState.Error(null)
        }
    }

    fun refresh() = scope().launch { refreshInternal() }

    fun selectCity(option: PrayerLocationConfig.CityOption) = scope().launch {
        try {
            val location = PrayerLocation(option.city, option.country, option.displayCity)
            withContext(ioDispatcher) {
                store.saveLocation(location)
                store.clearCache()
            }
            refreshInternal()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = PrayerUiState.Error(null)
        }
    }

    private fun scope(): CoroutineScope = testScope ?: viewModelScope

    private suspend fun refreshInternal() {
        val location = try {
            store.readLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (location == null) {
            _state.value = PrayerUiState.Error(null)
            return
        }
        _state.value = PrayerUiState.Ready(location, Freshness.Stale, OperationState.Refreshing)
        try {
            when (val result = withContext(ioDispatcher) { repository.refreshAndCache() }) {
                is RefreshResult.Success -> {
                    widgetUpdater.updateAll(getApplication())
                    scheduler.scheduleBoundary(getApplication())
                    _state.value = PrayerUiState.Ready(
                        result.cache.location, Freshness.Fresh, OperationState.Refreshed
                    )
                }
                is RefreshResult.StaleCache -> _state.value = PrayerUiState.Ready(
                    result.cache.location, Freshness.Stale, OperationState.RefreshFailed
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
