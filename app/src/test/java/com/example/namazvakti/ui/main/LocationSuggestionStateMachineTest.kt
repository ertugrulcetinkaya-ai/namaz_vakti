package com.example.namazvakti.ui.main

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSuggestionStateMachineTest {
    @Test
    fun locationResultSuggestsNearestDistrict() = runTest {
        val provider = FakeLocationProvider(
            OneShotLocationResult.Found(Coordinates(40.888, 29.185))
        )
        val machine = LocationSuggestionStateMachine(provider)

        machine.locate()

        val suggested = machine.state.value as LocationSuggestionState.Suggested
        assertEquals("KARTAL", suggested.match.district.name)
        assertTrue(provider.called)
    }

    @Test
    fun permissionDenialIsShownWithoutRequestingLocation() = runTest {
        val provider = FakeLocationProvider(OneShotLocationResult.ServicesDisabled)
        val machine = LocationSuggestionStateMachine(provider)

        machine.permissionDenied(permanently = false)

        assertEquals(LocationSuggestionState.PermissionDenied(permanently = false), machine.state.value)
        assertFalse(provider.called)
    }

    @Test
    fun timeoutIsExposedAsItsOwnState() = runTest {
        val machine = LocationSuggestionStateMachine(FakeLocationProvider(OneShotLocationResult.TimedOut))

        machine.locate()

        assertEquals(LocationSuggestionState.TimedOut, machine.state.value)
    }

    @Test
    fun distantLocationHasNoNearbyDistrict() = runTest {
        val machine = LocationSuggestionStateMachine(
            FakeLocationProvider(OneShotLocationResult.Found(Coordinates(38.5, 25.5)))
        )

        machine.locate()

        assertEquals(LocationSuggestionState.NoNearbyDistrict, machine.state.value)
    }

    private class FakeLocationProvider(
        private val result: OneShotLocationResult
    ) : OneShotLocationProvider {
        var called = false
            private set

        override suspend fun currentLocation(): OneShotLocationResult {
            called = true
            return result
        }
    }
}
