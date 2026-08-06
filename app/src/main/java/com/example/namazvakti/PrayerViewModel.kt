package com.example.namazvakti

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrayerViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as NamazVaktiApp).container
    private val _state = MutableStateFlow<PrayerUiState>(PrayerUiState.Loading)
    val state: StateFlow<PrayerUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val snapshot = withContext(Dispatchers.IO) {
            val location = container.store.readLocation()
            val cache = container.store.readCache()
            location to (cache == null || !cache.matches(container.timeProvider.today(), location, container.settings))
        }
        _state.value = PrayerUiState.Ready(snapshot.first, stale = snapshot.second)
    }

    fun refresh() = viewModelScope.launch {
        val location = container.store.readLocation()
        _state.value = PrayerUiState.Ready(location, message = "refreshing")
        val result = withContext(Dispatchers.IO) { container.repository.refreshAndCache() }
        when (result) {
            is RefreshResult.Success -> {
                PrayerWidgetUpdater.updateAll(getApplication())
                _state.value = PrayerUiState.Ready(result.cache.location, message = "success")
            }
            is RefreshResult.StaleCache -> _state.value = PrayerUiState.Ready(
                result.cache.location, stale = true, message = "error"
            )
            is RefreshResult.Failure -> _state.value = PrayerUiState.Error(
                getApplication<Application>().getString(com.example.namazvakti.R.string.refresh_error), location
            )
        }
    }

    fun selectCity(option: PrayerLocationConfig.CityOption) = viewModelScope.launch {
        val location = PrayerLocation(option.city, option.country, option.displayCity)
        withContext(Dispatchers.IO) {
            container.store.saveLocation(location)
            container.store.clearCache()
        }
        _state.value = PrayerUiState.Ready(location, stale = true, message = "refreshing")
        PrayerWidgetScheduler.enqueueRefresh(getApplication(), force = true)
    }
}
