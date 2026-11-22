package com.example.namazvakti

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.widget.PrayerWidgetSnapshot
import com.example.namazvakti.widget.renderer.PrayerWidgetDataState
import com.example.namazvakti.widget.worker.PrayerWidgetWorker
import com.example.namazvakti.widget.worker.PrayerWidgetWorkerDependencies
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerWidgetWorkerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cache = sampleCache(LocalDate.of(2026, 8, 6))

    @Test
    fun widgetMissingReturnsSuccessWithoutCallingApi() = runBlocking {
        val dependencies = FakeDependencies(hasWidgets = false, notificationsEnabled = false)

        val result = worker(dependencies, fetch = true).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, dependencies.refreshCalls)
        assertTrue(dependencies.cancelled)
    }

    @Test
    fun notificationsKeepBackgroundRefreshRunningWithoutWidget() = runBlocking {
        val dependencies = FakeDependencies(hasWidgets = false, notificationsEnabled = true)

        val result = worker(dependencies, fetch = true).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, dependencies.refreshCalls)
        assertTrue(dependencies.rendered.isEmpty())
        assertTrue(dependencies.scheduled.isEmpty())
        assertEquals(1, dependencies.notificationSchedules)
        assertFalse(dependencies.cancelled)
    }

    @Test
    fun freshCacheWithRerenderModeDoesNotCallApi() = runBlocking {
        val dependencies = FakeDependencies(snapshot = freshSnapshot(cache))

        val result = worker(dependencies, fetch = false).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, dependencies.refreshCalls)
        assertEquals(1, dependencies.snapshotLoads)
        assertEquals(1, dependencies.snapshotForCalls)
        assertEquals(1, dependencies.rendered.size)
        assertEquals(1, dependencies.scheduled.size)
    }

    @Test
    fun http503RetriesOnFirstAttemptAndFailsAtMaximumAttempt() = runBlocking {
        val dependencies = FakeDependencies(
            refreshResult = RefreshResult.Failure(
                IOException("service unavailable"),
                PrayerError.Service(503)
            )
        )

        val first = worker(dependencies, runAttemptCount = 0).doWork()
        val maximum = worker(dependencies, runAttemptCount = 2).doWork()

        assertTrue(first is ListenableWorker.Result.Retry)
        assertTrue(maximum is ListenableWorker.Result.Failure)
        assertEquals("service_503", maximum.outputData.getString(PrayerWidgetWorker.OUTPUT_ERROR))
    }

    @Test
    fun http400FailsWithoutRetry() = runBlocking {
        val dependencies = FakeDependencies(
            refreshResult = RefreshResult.Failure(
                IOException("bad request"),
                PrayerError.Service(400)
            )
        )

        val result = worker(dependencies).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("service_400", result.outputData.getString(PrayerWidgetWorker.OUTPUT_ERROR))
    }

    @Test
    fun networkFailureRendersStaleCacheAndRetries() = runBlocking {
        val stale = sampleCache(LocalDate.of(2026, 8, 5))
        val dependencies = FakeDependencies(
            refreshResult = RefreshResult.StaleCache(
                cache = stale,
                cause = IOException("offline"),
                error = PrayerError.Network
            )
        )

        val result = worker(dependencies).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(listOf(stale), dependencies.rendered.map { it.cache })
    }

    @Test
    fun successfulRefreshReloadsSnapshotWithTomorrowCacheBeforeRendering() = runBlocking {
        val initialSnapshot = freshSnapshot(cache)
        assertNull(initialSnapshot.tomorrowCache)
        val tomorrow = sampleCache(cache.date.plusDays(1))
        val dependencies = FakeDependencies(
            snapshot = initialSnapshot,
            refreshResult = RefreshResult.Success(cache)
        ).apply {
            snapshotAfterRefresh = initialSnapshot.copy(tomorrowCache = tomorrow)
        }

        val result = worker(dependencies).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(2, dependencies.snapshotLoads)
        assertEquals(0, dependencies.snapshotForCalls)
        assertEquals(tomorrow, dependencies.rendered.single().tomorrowCache)
    }

    @Test
    fun staleCacheRefreshReloadsSnapshotBeforeRendering() = runBlocking {
        val stale = sampleCache(cache.date)
        val tomorrow = sampleCache(stale.date.plusDays(1))
        val initialSnapshot = freshSnapshot(cache)
        val dependencies = FakeDependencies(
            snapshot = initialSnapshot,
            refreshResult = RefreshResult.StaleCache(
                cache = stale,
                cause = IOException("offline"),
                error = PrayerError.Network
            )
        ).apply {
            snapshotAfterRefresh = initialSnapshot.copy(
                cache = stale,
                dataState = PrayerWidgetDataState.Stale,
                tomorrowCache = tomorrow
            )
        }

        val result = worker(dependencies).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(2, dependencies.snapshotLoads)
        assertEquals(0, dependencies.snapshotForCalls)
        assertEquals(stale, dependencies.rendered.single().cache)
        assertEquals(tomorrow, dependencies.rendered.single().tomorrowCache)
    }

    @Test
    fun snapshotReloadFailureFallsBackToRefreshResultSnapshot() = runBlocking {
        val dependencies = FakeDependencies(
            snapshot = freshSnapshot(cache),
            refreshResult = RefreshResult.Success(cache)
        ).apply {
            snapshotReloadFailure = IOException("snapshot reload failed")
        }

        val result = worker(dependencies).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(2, dependencies.snapshotLoads)
        assertEquals(1, dependencies.snapshotForCalls)
        assertEquals(cache, dependencies.rendered.single().cache)
    }

    @Test
    fun transientStorageRetriesOnlyOnce() = runBlocking {
        val dependencies = FakeDependencies(
            refreshResult = RefreshResult.Failure(
                IllegalStateException("database locked"),
                PrayerError.TransientStorage
            )
        )

        val retry = worker(dependencies, runAttemptCount = 0).doWork()
        val failure = worker(dependencies, runAttemptCount = 1).doWork()

        assertTrue(retry is ListenableWorker.Result.Retry)
        assertTrue(failure is ListenableWorker.Result.Failure)
        assertEquals(
            "storage_transient",
            failure.outputData.getString(PrayerWidgetWorker.OUTPUT_ERROR)
        )
    }

    @Test
    fun successfulRefreshRendersAndSchedulesBoundary() = runBlocking {
        val dependencies = FakeDependencies(
            refreshResult = RefreshResult.Success(cache)
        )

        val result = worker(dependencies).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, dependencies.refreshCalls)
        assertEquals(listOf(cache), dependencies.rendered.map { it.cache })
        assertEquals(listOf(cache), dependencies.scheduled.map { it.cache })
    }

    private fun worker(
        dependencies: FakeDependencies,
        fetch: Boolean = true,
        runAttemptCount: Int = 0
    ): PrayerWidgetWorker = TestListenableWorkerBuilder
        .from(context, PrayerWidgetWorker::class.java)
        .setInputData(Data.Builder().putBoolean(PrayerWidgetWorker.INPUT_FETCH, fetch).build())
        .setRunAttemptCount(runAttemptCount)
        .build()
        .also { it.setDependenciesForTesting(dependencies) }

    private class FakeDependencies(
        private val hasWidgets: Boolean = true,
        private val notificationsEnabled: Boolean = false,
        var snapshot: PrayerWidgetSnapshot = freshSnapshot(sampleCache(LocalDate.of(2026, 8, 6))),
        private val refreshResult: RefreshResult = RefreshResult.Success(snapshot.cache!!)
    ) : PrayerWidgetWorkerDependencies {
        var refreshCalls = 0
        var snapshotLoads = 0
        var snapshotForCalls = 0
        var snapshotAfterRefresh: PrayerWidgetSnapshot? = null
        var snapshotReloadFailure: Exception? = null
        var cancelled = false
        var notificationSchedules = 0
        val rendered = mutableListOf<PrayerWidgetSnapshot>()
        val scheduled = mutableListOf<PrayerWidgetSnapshot>()

        override fun hasWidgets(): Boolean = hasWidgets

        override suspend fun notificationsEnabled(): Boolean = notificationsEnabled

        override suspend fun cancelAll() {
            cancelled = true
        }

        override suspend fun loadSnapshot(): PrayerWidgetSnapshot {
            snapshotLoads += 1
            if (snapshotLoads > 1) snapshotReloadFailure?.let { throw it }
            return snapshot
        }

        override suspend fun refresh(): RefreshResult {
            refreshCalls += 1
            snapshot = snapshotAfterRefresh ?: when (val result = refreshResult) {
                is RefreshResult.Success -> snapshot.copy(
                    cache = result.cache,
                    location = result.cache.location,
                    dataState = PrayerWidgetDataState.Fresh
                )
                is RefreshResult.StaleCache -> snapshot.copy(
                    cache = result.cache,
                    location = result.cache.location,
                    dataState = PrayerWidgetDataState.Stale
                )
                is RefreshResult.Failure -> snapshot
            }
            return refreshResult
        }

        override fun snapshotFor(
            cache: CachedPrayerDay?,
            current: PrayerWidgetSnapshot
        ): PrayerWidgetSnapshot {
            snapshotForCalls += 1
            return current.copy(
                cache = cache,
                location = cache?.location ?: current.location,
                dataState = when {
                    cache == null -> PrayerWidgetDataState.Unavailable
                    cache.date == current.cache?.date -> PrayerWidgetDataState.Fresh
                    else -> PrayerWidgetDataState.Stale
                }
            )
        }

        override suspend fun render(snapshot: PrayerWidgetSnapshot) {
            rendered += snapshot
        }

        override suspend fun scheduleBoundary(snapshot: PrayerWidgetSnapshot) {
            scheduled += snapshot
        }

        override suspend fun scheduleNotifications() {
            notificationSchedules += 1
        }
    }

    private companion object {
        fun freshSnapshot(cache: CachedPrayerDay) = PrayerWidgetSnapshot(
            cache = cache,
            location = cache.location,
            now = ZonedDateTime.of(2026, 8, 6, 10, 0, 0, 0, PrayerTimeProvider.DEFAULT_ZONE),
            dataState = PrayerWidgetDataState.Fresh
        )

        fun sampleCache(date: LocalDate) = CachedPrayerDay(
            date = date,
            location = PrayerLocation("Ankara", "Turkey", "ANKARA"),
            timezone = PrayerTimeProvider.DEFAULT_ZONE,
            settings = PrayerCalculationSettings(),
            prayerTimes = PrayerTimes(
                LocalTime.of(4, 12), LocalTime.of(5, 49), LocalTime.of(13, 8),
                LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
            ),
            hijriText = null,
            fetchedAt = Instant.parse("2026-08-06T00:00:00Z")
        )
    }
}
