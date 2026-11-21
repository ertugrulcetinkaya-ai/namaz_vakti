package com.example.namazvakti.ui.main

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class PrayerHomeScreenTest {
    private val today = LocalDate.of(2026, 11, 22)
    private val location = PrayerLocation("Ankara", "Turkey", "ANKARA")

    @Test
    fun staleReadyStateShowsUnavailableMessageLastDataAndRetry() {
        val state = ready(
            operation = OperationState.Idle,
            freshness = Freshness.Stale,
            cache = cache(today.minusDays(1))
        )

        val emptyState = assertNotNullAndReturn(homeEmptyStateFor(state, today))

        assertEquals(HomeEmptyMessage.TodayUnavailable, emptyState.message)
        assertEquals(today.minusDays(1), emptyState.lastDataDate)
        assertFalse(emptyState.isLoading)
        assertTrue(emptyState.showRetry)
    }

    @Test
    fun refreshingReadyStateShowsProgressWithoutRetry() {
        val state = ready(
            operation = OperationState.Refreshing,
            freshness = Freshness.Stale,
            cache = cache(today.minusDays(1))
        )

        val emptyState = assertNotNullAndReturn(homeEmptyStateFor(state, today))

        assertEquals(HomeEmptyMessage.Refreshing, emptyState.message)
        assertEquals(today.minusDays(1), emptyState.lastDataDate)
        assertTrue(emptyState.isLoading)
        assertFalse(emptyState.showRetry)
    }

    @Test
    fun failedRefreshUsesPrayerErrorAndOffersRetry() {
        val state = ready(
            operation = OperationState.RefreshFailed,
            freshness = Freshness.Stale,
            cache = cache(today.minusDays(2)),
            lastError = PrayerError.Network
        )

        val emptyState = assertNotNullAndReturn(homeEmptyStateFor(state, today))

        assertEquals(HomeEmptyMessage.Error(PrayerError.Network), emptyState.message)
        assertEquals(today.minusDays(2), emptyState.lastDataDate)
        assertFalse(emptyState.isLoading)
        assertTrue(emptyState.showRetry)
    }

    @Test
    fun initialReadyStateWithoutCacheKeepsNoDataMessageAndRetry() {
        val state = ready(
            operation = OperationState.Idle,
            freshness = Freshness.Stale,
            cache = null
        )

        val emptyState = assertNotNullAndReturn(homeEmptyStateFor(state, today))

        assertEquals(HomeEmptyMessage.NoPrayerData, emptyState.message)
        assertNull(emptyState.lastDataDate)
        assertFalse(emptyState.isLoading)
        assertTrue(emptyState.showRetry)
    }

    @Test
    fun freshTodayCacheDoesNotProduceAnEmptyState() {
        val state = ready(
            operation = OperationState.Idle,
            freshness = Freshness.Fresh,
            cache = cache(today)
        )

        assertNull(homeEmptyStateFor(state, today))
    }

    private fun ready(
        operation: OperationState,
        freshness: Freshness,
        cache: CachedPrayerDay?,
        lastError: PrayerError? = null
    ) = PrayerUiState.Ready(
        location = location,
        freshness = freshness,
        operation = operation,
        cache = cache,
        lastError = lastError
    )

    private fun cache(date: LocalDate) = CachedPrayerDay(
        date = date,
        location = location,
        timezone = PrayerTimeProvider.DEFAULT_ZONE,
        settings = PrayerCalculationSettings(),
        prayerTimes = PrayerTimes(
            LocalTime.of(4, 12),
            LocalTime.of(5, 49),
            LocalTime.of(13, 8),
            LocalTime.of(17, 2),
            LocalTime.of(20, 21),
            LocalTime.of(22, 1)
        ),
        hijriText = null,
        fetchedAt = Instant.parse("2026-11-21T12:00:00Z")
    )

    private fun assertNotNullAndReturn(state: HomeEmptyState?): HomeEmptyState {
        assertNotNull(state)
        return requireNotNull(state)
    }
}
