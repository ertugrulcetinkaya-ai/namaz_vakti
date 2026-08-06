package com.example.namazvakti

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerViewModelTest {
    @Test
    fun androidViewModelFactoryConstructorIsAvailable() {
        PrayerViewModel::class.java.getConstructor(Application::class.java)
    }

    @Test
    fun loadWithoutCacheReportsStaleReadyState() = runTest {
        val store = FakeStore()
        val viewModel = viewModel(store, FakeRepository(RefreshResult.Failure(Exception())), CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        val state = viewModel.state.value as PrayerUiState.Ready
        assertEquals(Freshness.Stale, state.freshness)
        assertEquals(OperationState.Idle, state.operation)
    }

    @Test
    fun successfulRefreshReportsRefreshedFreshState() = runTest {
        val store = FakeStore()
        val cache = sampleCache(store.location)
        val viewModel = viewModel(store, FakeRepository(RefreshResult.Success(cache)), CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value as PrayerUiState.Ready
        assertEquals(Freshness.Fresh, state.freshness)
        assertEquals(OperationState.Refreshed, state.operation)
    }

    @Test
    fun failedRefreshReportsErrorAndStaleRefreshReportsRefreshFailed() = runTest {
        val store = FakeStore()
        val errorViewModel = viewModel(store, FakeRepository(RefreshResult.Failure(Exception())), CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        errorViewModel.refresh()
        advanceUntilIdle()
        assertTrue(errorViewModel.state.value is PrayerUiState.Error)

        val stale = sampleCache(store.location)
        val staleViewModel = viewModel(store, FakeRepository(RefreshResult.StaleCache(stale, Exception())), CoroutineScope(StandardTestDispatcher(testScheduler)))
        staleViewModel.refresh()
        advanceUntilIdle()
        val state = staleViewModel.state.value as PrayerUiState.Ready
        assertEquals(Freshness.Stale, state.freshness)
        assertEquals(OperationState.RefreshFailed, state.operation)
    }

    @Test
    fun selectingCityRunsRefreshAndDoesNotRemainRefreshing() = runTest {
        val store = FakeStore()
        val selected = PrayerLocationConfig.cityOptions.first { it.city == "Istanbul" }
        val newLocation = PrayerLocation(selected.city, selected.country, selected.displayCity)
        val viewModel = viewModel(store, FakeRepository(RefreshResult.Success(sampleCache(newLocation))), CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        viewModel.selectCity(selected)
        advanceUntilIdle()

        val state = viewModel.state.value as PrayerUiState.Ready
        assertEquals(newLocation, store.location)
        assertEquals(OperationState.Refreshed, state.operation)
        assertEquals(Freshness.Fresh, state.freshness)
    }

    private fun viewModel(store: FakeStore, repository: FakeRepository, scope: CoroutineScope) = PrayerViewModel(
        Application(), store, repository, FakeScheduler(), FakeWidgetUpdater(),
        PrayerTimeProvider(), PrayerCalculationSettings(), scope,
        scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
    )

    private fun sampleCache(location: PrayerLocation) = CachedPrayerDay(
        LocalDate.now(), location, PrayerTimeProvider.DEFAULT_ZONE, PrayerCalculationSettings(),
        PrayerTimes(
            LocalTime.of(4, 12), LocalTime.of(5, 49), LocalTime.of(13, 8),
            LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
        ), null, Instant.now()
    )

    private class FakeStore : PrayerPreferences {
        var location = PrayerLocation("Ankara", "Turkey", "ANKARA")
        var cache: CachedPrayerDay? = null
        override suspend fun readCache() = cache
        override suspend fun saveCache(cache: CachedPrayerDay) { this.cache = cache }
        override suspend fun readLocation() = location
        override suspend fun saveLocation(location: PrayerLocation) { this.location = location }
        override suspend fun clearCache() { cache = null }
    }

    private class FakeRepository(private val result: RefreshResult) : PrayerRefreshRepository {
        override suspend fun refreshAndCache() = result
    }

    private class FakeScheduler : PrayerRefreshScheduler {
        override suspend fun enqueueRefresh(context: Context, force: Boolean) = Unit
        override suspend fun scheduleBoundary(context: Context) = Unit
    }

    private class FakeWidgetUpdater : PrayerWidgetUpdatePort {
        override suspend fun updateAll(context: Context) = Unit
    }
}
