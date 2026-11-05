package com.example.namazvakti

import com.example.namazvakti.data.local.PrayerPreferences
import com.example.namazvakti.data.local.PrayerNotificationSettingsSource
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerLocationConfig
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.MonthDataSource
import com.example.namazvakti.domain.model.MonthResult
import com.example.namazvakti.domain.model.RamadanRange
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.RefreshOrigin
import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.domain.port.PrayerRefreshRepository
import com.example.namazvakti.domain.port.PrayerRefreshScheduler
import com.example.namazvakti.domain.port.PrayerWidgetUpdatePort
import com.example.namazvakti.ui.main.Freshness
import com.example.namazvakti.ui.main.OperationState
import com.example.namazvakti.ui.main.PrayerUiState
import com.example.namazvakti.ui.main.PrayerViewModel
import com.example.namazvakti.domain.policy.PrayerCachePolicy
import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerViewModelTest {

    @Test
    fun religiousDaysViewModelReturnsOfficialRowsForSelectedYear() = runTest {
        val viewModel = viewModel(
            FakeStore(),
            FakeRepository(RefreshResult.Failure(Exception())),
            CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        advanceUntilIdle()

        val regaip = viewModel.religiousDaysFor(2031).single { it.name.contains("REGAİB") }
        assertEquals(LocalDate.of(2031, 10, 16), regaip.date)
        assertFalse(com.example.namazvakti.domain.model.DiyanetReligiousDays.on(LocalDate.of(2031, 10, 17)).any { it.displayName == "Regaip Kandili" })
    }

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
    fun loadIncludesTomorrowCacheForCountdown() = runTest {
        val store = FakeStore()
        val today = LocalDate.now(PrayerTimeProvider.DEFAULT_ZONE)
        val tomorrow = sampleCache(store.location, today.plusDays(1))
        store.caches[tomorrow.date] = tomorrow
        val viewModel = viewModel(
            store,
            FakeRepository(RefreshResult.Failure(Exception())),
            CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        advanceUntilIdle()

        assertEquals(tomorrow, (viewModel.state.value as PrayerUiState.Ready).tomorrowCache)
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
        assertEquals(cache, state.cache)
        assertEquals(RefreshOrigin.NETWORK, state.refreshOrigin)
    }

    @Test
    fun failedRefreshReportsErrorAndStaleRefreshReportsRefreshFailed() = runTest {
        val store = FakeStore()
        val errorViewModel = viewModel(
            store,
            FakeRepository(RefreshResult.Failure(Exception(), PrayerError.InvalidData)),
            CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        advanceUntilIdle()
        errorViewModel.refresh()
        advanceUntilIdle()
        assertEquals(
            PrayerError.InvalidData,
            (errorViewModel.state.value as PrayerUiState.Error).error
        )

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
        val newLocation = PrayerLocation(
            selected.city, selected.country, selected.displayCity,
            latitude = selected.latitude, longitude = selected.longitude
        )
        val viewModel = viewModel(store, FakeRepository(RefreshResult.Success(sampleCache(newLocation))), CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        viewModel.selectCity(selected)
        advanceUntilIdle()

        val state = viewModel.state.value as PrayerUiState.Ready
        assertEquals(newLocation, store.location)
        assertEquals(0, store.clearCalls)
        assertEquals(OperationState.Refreshed, state.operation)
        assertEquals(Freshness.Fresh, state.freshness)
    }

    @Test
    fun selectingCityCancelsAnOlderRefreshSoLatestSelectionWins() = runTest {
        val store = FakeStore()
        val selected = PrayerLocationConfig.cityOptions.first { it.city == "Istanbul" }
        val newLocation = PrayerLocation(
            selected.city, selected.country, selected.displayCity,
            latitude = selected.latitude, longitude = selected.longitude
        )
        val repository = CancelThenSucceedRepository { sampleCache(newLocation) }
        val viewModel = viewModel(store, repository, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        viewModel.refresh()
        runCurrent()
        viewModel.selectCity(selected)
        advanceUntilIdle()

        assertTrue(repository.firstRefreshCancelled)
        assertEquals(newLocation, store.location)
        assertEquals(newLocation, (viewModel.state.value as PrayerUiState.Ready).location)
    }

    @Test
    fun savingNotificationSettingsUpdatesStateAndSchedulesNotifications() = runTest {
        val source = FakeNotificationSettingsStore()
        val scheduler = FakeScheduler()
        val viewModel = viewModel(
            FakeStore(),
            FakeRepository(RefreshResult.Failure(Exception())),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            source,
            scheduler
        )
        advanceUntilIdle()
        val settings = PrayerNotificationSettings(notificationsEnabled = true, leadMinutes = 10)

        viewModel.updateNotificationSettings(settings)
        advanceUntilIdle()

        assertEquals(settings, source.read())
        assertEquals(settings, viewModel.notificationSettings.value)
        assertTrue(scheduler.notificationSchedules >= 2)
    }

    @Test
    fun changingMonthCancelsThePreviousMonthRequest() = runTest {
        val store = FakeStore()
        val repository = MonthRepository(store)
        val viewModel = viewModel(store, repository, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        viewModel.loadMonth(YearMonth.of(2026, 8))
        runCurrent()
        viewModel.loadMonth(YearMonth.of(2026, 9))
        advanceUntilIdle()

        assertTrue(repository.firstMonthCancelled)
        assertEquals(YearMonth.of(2026, 9), viewModel.monthlyState.value.month)
        assertEquals(MonthDataSource.CACHE, viewModel.monthlyState.value.source)
    }

    @Test
    fun monthlyStateExposesPartialOfflineRows() = runTest {
        val store = FakeStore()
        val repository = MonthRepository(store, monthSource = MonthDataSource.PARTIAL)
        val viewModel = viewModel(store, repository, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        viewModel.loadMonth(YearMonth.of(2026, 11))
        advanceUntilIdle()

        assertEquals(MonthDataSource.PARTIAL, viewModel.monthlyState.value.source)
        assertTrue(viewModel.monthlyState.value.days.isNotEmpty())
    }

    @Test
    fun imsakiyeStateCarriesRamadanRangeForInsideAndOutsideDayLabels() = runTest {
        val store = FakeStore()
        val range = RamadanRange(
            1448,
            LocalDate.of(2027, 2, 8),
            LocalDate.of(2027, 3, 8),
            LocalDate.of(2027, 3, 9),
            estimated = false
        )
        val repository = MonthRepository(store, range = range)
        val viewModel = viewModel(store, repository, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        viewModel.loadImsakiye()
        advanceUntilIdle()

        val stateRange = requireNotNull(viewModel.imsakiyeState.value.range)
        assertEquals(5, stateRange.dayNumber(LocalDate.of(2027, 2, 12)))
        assertEquals(null, stateRange.dayNumber(LocalDate.of(2027, 2, 7)))
    }

    @Test
    fun changingCityReloadsAnOpenImsakiye() = runTest {
        val store = FakeStore()
        val range = RamadanRange(
            1448,
            LocalDate.of(2027, 2, 8),
            LocalDate.of(2027, 3, 8),
            LocalDate.of(2027, 3, 9),
            estimated = false
        )
        val repository = MonthRepository(store, range = range)
        val viewModel = viewModel(store, repository, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        viewModel.loadImsakiye()
        advanceUntilIdle()
        val before = repository.monthRequests

        val selected = PrayerLocationConfig.cityOptions.first { it.city == "Istanbul" }
        repository.refreshResult = RefreshResult.Success(
            sampleCache(
                PrayerLocation(
                    selected.city,
                    selected.country,
                    selected.displayCity,
                    latitude = selected.latitude,
                    longitude = selected.longitude
                )
            )
        )
        viewModel.selectCity(selected)
        advanceUntilIdle()

        assertTrue(repository.monthRequests > before)
        assertEquals(selected.city, store.location.city)
    }

    private fun viewModel(
        store: FakeStore,
        repository: PrayerRefreshRepository,
        scope: CoroutineScope,
        notificationSettingsStore: FakeNotificationSettingsStore = FakeNotificationSettingsStore(),
        scheduler: FakeScheduler = FakeScheduler()
    ) = PrayerViewModel(
        Application(), store, repository, scheduler, FakeWidgetUpdater(),
        PrayerTimeProvider(), PrayerCalculationSettings(), scope,
        scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
        PrayerCachePolicy(), notificationSettingsStore
    )

    private fun sampleCache(
        location: PrayerLocation,
        date: LocalDate = LocalDate.now(PrayerTimeProvider.DEFAULT_ZONE)
    ) = CachedPrayerDay(
        date, location, PrayerTimeProvider.DEFAULT_ZONE, PrayerCalculationSettings(),
        PrayerTimes(
            LocalTime.of(4, 12), LocalTime.of(5, 49), LocalTime.of(13, 8),
            LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
        ), null, Instant.now()
    )

    private class FakeStore : PrayerPreferences {
        var location = PrayerLocation("Ankara", "Turkey", "ANKARA")
        var cache: CachedPrayerDay? = null
        val caches = mutableMapOf<LocalDate, CachedPrayerDay>()
        var clearCalls = 0
        override suspend fun readCache() = cache
        override suspend fun readCache(
            date: LocalDate,
            location: PrayerLocation,
            settings: PrayerCalculationSettings
        ) = caches[date]?.takeIf { it.matches(date, location, settings) }
            ?: cache?.takeIf { it.matches(date, location, settings) }
        override suspend fun saveCache(cache: CachedPrayerDay) { this.cache = cache }
        override suspend fun readLocation() = location
        override suspend fun saveLocation(location: PrayerLocation) { this.location = location }
        override suspend fun clearCache() {
            clearCalls += 1
            cache = null
        }
    }

    private class FakeRepository(private val result: RefreshResult) : PrayerRefreshRepository {
        override suspend fun refreshAndCache() = result
    }

    private inner class MonthRepository(
        private val store: FakeStore,
        private val range: RamadanRange? = null,
        private val monthSource: MonthDataSource = MonthDataSource.CACHE
    ) : PrayerRefreshRepository {
        var refreshResult: RefreshResult = RefreshResult.Failure(Exception())
        var firstMonthCancelled = false
        var monthRequests = 0
        private var firstMonthCall = true

        override suspend fun refreshAndCache() = refreshResult

        override suspend fun ensureMonth(yearMonth: YearMonth): MonthResult {
            monthRequests++
            if (firstMonthCall && yearMonth == YearMonth.of(2026, 8)) {
                firstMonthCall = false
                try {
                    awaitCancellation()
                } finally {
                    firstMonthCancelled = true
                }
            }
            val days = (1..yearMonth.lengthOfMonth()).map { day ->
                sampleCache(store.location, yearMonth.atDay(day))
            }
            return MonthResult(yearMonth, days, monthSource)
        }

        override suspend fun ramadanRange() = range
    }

    private class CancelThenSucceedRepository(
        private val cache: () -> CachedPrayerDay
    ) : PrayerRefreshRepository {
        private var callCount = 0
        var firstRefreshCancelled = false
            private set

        override suspend fun refreshAndCache(): RefreshResult {
            callCount += 1
            if (callCount == 1) {
                try {
                    awaitCancellation()
                } finally {
                    firstRefreshCancelled = true
                }
            }
            return RefreshResult.Success(cache())
        }
    }

    private class FakeScheduler : PrayerRefreshScheduler {
        var notificationSchedules = 0
        override suspend fun enqueueRefresh(force: Boolean) = Unit
        override suspend fun scheduleBoundary() = Unit
        override suspend fun scheduleNotifications() { notificationSchedules += 1 }
    }

    private class FakeNotificationSettingsStore : PrayerNotificationSettingsSource {
        private val state = MutableStateFlow(PrayerNotificationSettings())
        override val settings: Flow<PrayerNotificationSettings> = state
        override suspend fun read() = state.first()
        override suspend fun save(settings: PrayerNotificationSettings) { state.value = settings }
    }

    private class FakeWidgetUpdater : PrayerWidgetUpdatePort {
        override suspend fun updateAll() = Unit
    }
}
