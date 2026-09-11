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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerWidgetWorkerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cache = sampleCache(LocalDate.of(2026, 8, 6))

    @Test
    fun widgetMissingReturnsSuccessWithoutCallingApi() = runBlocking {
        val dependencies = FakeDependencies(hasWidgets = false)

        val result = worker(dependencies, fetch = true).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, dependencies.refreshCalls)
        assertTrue(dependencies.cancelled)
    }

    @Test
    fun freshCacheWithRerenderModeDoesNotCallApi() = runBlocking {
        val dependencies = FakeDependencies(snapshot = freshSnapshot(cache))

        val result = worker(dependencies, fetch = false).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, dependencies.refreshCalls)
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
        var snapshot: PrayerWidgetSnapshot = freshSnapshot(sampleCache(LocalDate.of(2026, 8, 6))),
        private val refreshResult: RefreshResult = RefreshResult.Success(snapshot.cache!!)
    ) : PrayerWidgetWorkerDependencies {
        var refreshCalls = 0
        var cancelled = false
        val rendered = mutableListOf<PrayerWidgetSnapshot>()
        val scheduled = mutableListOf<PrayerWidgetSnapshot>()

        override fun hasWidgets(): Boolean = hasWidgets

        override suspend fun cancelAll() {
            cancelled = true
        }

        override suspend fun loadSnapshot(): PrayerWidgetSnapshot = snapshot

        override suspend fun refresh(): RefreshResult {
            refreshCalls += 1
            return refreshResult
        }

        override fun snapshotFor(
            cache: CachedPrayerDay?,
            current: PrayerWidgetSnapshot
        ): PrayerWidgetSnapshot = current.copy(
            cache = cache,
            location = cache?.location ?: current.location,
            dataState = when {
                cache == null -> PrayerWidgetDataState.Unavailable
                cache.date == current.cache?.date -> PrayerWidgetDataState.Fresh
                else -> PrayerWidgetDataState.Stale
            }
        )

        override suspend fun render(snapshot: PrayerWidgetSnapshot) {
            rendered += snapshot
        }

        override suspend fun scheduleBoundary(snapshot: PrayerWidgetSnapshot) {
            scheduled += snapshot
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
