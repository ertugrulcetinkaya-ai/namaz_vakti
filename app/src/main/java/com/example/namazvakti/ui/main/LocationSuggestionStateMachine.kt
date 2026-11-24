package com.example.namazvakti.ui.main

import com.example.namazvakti.domain.policy.NearestDistrictFinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Coordinates(val latitude: Double, val longitude: Double)

sealed interface OneShotLocationResult {
    data class Found(val coordinates: Coordinates) : OneShotLocationResult
    data object ServicesDisabled : OneShotLocationResult
    data object TimedOut : OneShotLocationResult
    data object PermissionDenied : OneShotLocationResult
}

fun interface OneShotLocationProvider {
    suspend fun currentLocation(): OneShotLocationResult
}

sealed interface LocationSuggestionState {
    data object Idle : LocationSuggestionState
    data object Locating : LocationSuggestionState
    data class PermissionDenied(val permanently: Boolean) : LocationSuggestionState
    data object ServicesDisabled : LocationSuggestionState
    data object TimedOut : LocationSuggestionState
    data object NoNearbyDistrict : LocationSuggestionState
    data class Suggested(val match: NearestDistrictFinder.Match) : LocationSuggestionState
}

class LocationSuggestionStateMachine(
    private val locationProvider: OneShotLocationProvider
) {
    private val mutableState = MutableStateFlow<LocationSuggestionState>(LocationSuggestionState.Idle)
    val state: StateFlow<LocationSuggestionState> = mutableState.asStateFlow()

    fun permissionDenied(permanently: Boolean) {
        mutableState.value = LocationSuggestionState.PermissionDenied(permanently)
    }

    fun reset() {
        mutableState.value = LocationSuggestionState.Idle
    }

    suspend fun locate() {
        if (mutableState.value == LocationSuggestionState.Locating) return
        mutableState.value = LocationSuggestionState.Locating
        mutableState.value = try {
            when (val result = locationProvider.currentLocation()) {
                is OneShotLocationResult.Found -> NearestDistrictFinder.find(
                    result.coordinates.latitude,
                    result.coordinates.longitude
                )?.let(LocationSuggestionState::Suggested)
                    ?: LocationSuggestionState.NoNearbyDistrict
                OneShotLocationResult.ServicesDisabled -> LocationSuggestionState.ServicesDisabled
                OneShotLocationResult.TimedOut -> LocationSuggestionState.TimedOut
                OneShotLocationResult.PermissionDenied -> LocationSuggestionState.PermissionDenied(permanently = false)
            }
        } catch (cancellation: CancellationException) {
            mutableState.value = LocationSuggestionState.Idle
            throw cancellation
        } catch (_: Exception) {
            LocationSuggestionState.TimedOut
        }
    }
}
