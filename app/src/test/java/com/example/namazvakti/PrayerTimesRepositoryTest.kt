package com.example.namazvakti

import com.example.namazvakti.app.*
import com.example.namazvakti.data.local.*
import com.example.namazvakti.data.remote.*
import com.example.namazvakti.data.repository.*
import com.example.namazvakti.domain.model.*
import com.example.namazvakti.domain.policy.*
import com.example.namazvakti.domain.port.*
import com.example.namazvakti.ui.main.*
import com.example.namazvakti.widget.*
import com.example.namazvakti.widget.alarm.*
import com.example.namazvakti.widget.renderer.*
import com.example.namazvakti.widget.worker.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrayerTimesRepositoryTest {
    private val location = PrayerLocation("Ankara", "Turkey", "ANKARA")
    private val settings = PrayerCalculationSettings()

    @Test
    fun successfulRefreshCachesTheCompleteMonthAndUsesApiTimezone() = runTest {
        val instant = Instant.parse("2026-08-06T10:00:00Z")
        val store = FakeStore(location, settings, LocalDate.of(2026, 8, 6))
        val api = FakeApi { year, month ->
            calendar(YearMonth.of(year, month), "America/Los_Angeles")
        }
        val repository = repository(api, store, instant)

        val result = repository.refreshAndCache() as RefreshResult.Success

        assertEquals(LocalDate.of(2026, 8, 6), result.cache.date)
        assertEquals(ZoneId.of("America/Los_Angeles"), result.cache.timezone)
        assertEquals(instant, result.cache.fetchedAt)
        assertEquals(RefreshOrigin.NETWORK, result.origin)
        assertEquals(31, store.days.size)
        assertEquals(listOf(YearMonth.of(2026, 8)), api.requestedMonths)
    }

    @Test
    fun networkFailureUsesTodaysCachedDayAsFreshOfflineData() = runTest {
        val today = LocalDate.of(2026, 8, 6)
        val existing = sampleCache(today, location)
        val store = FakeStore(location, settings, today, listOf(existing))
        val failure = IllegalStateException("offline")
        val repository = repository(FakeApi { _, _ -> throw failure }, store)

        val result = repository.refreshAndCache() as RefreshResult.Success

        assertSame(existing, result.cache)
        assertEquals(RefreshOrigin.LOCAL_CACHE, result.origin)
        assertEquals(1, store.days.size)
    }

    @Test
    fun networkFailureReturnsOlderCacheAsStaleWithoutOverwritingIt() = runTest {
        val today = LocalDate.of(2026, 8, 6)
        val existing = sampleCache(today.minusDays(1), location)
        val store = FakeStore(location, settings, today, listOf(existing))
        val failure = IllegalStateException("offline")
        val repository = repository(FakeApi { _, _ -> throw failure }, store)

        val result = repository.refreshAndCache()

        assertTrue(result is RefreshResult.StaleCache)
        result as RefreshResult.StaleCache
        assertSame(existing, result.cache)
        assertSame(failure, result.cause)
        assertEquals(1, store.days.size)
    }

    @Test
    fun lastSevenDaysOfMonthPrefetchesTheFollowingMonth() = runTest {
        val today = LocalDate.of(2026, 8, 25)
        val store = FakeStore(location, settings, today)
        val api = FakeApi { year, month -> calendar(YearMonth.of(year, month)) }
        val repository = repository(api, store, Instant.parse("2026-08-25T10:00:00Z"))

        repository.refreshAndCache()

        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
            api.requestedMonths
        )
        assertEquals(61, store.days.size)
    }

    @Test
    fun prefetchFailureIsLoggedWithoutFailingCurrentRefresh() = runTest {
        val today = LocalDate.of(2026, 8, 25)
        val store = FakeStore(location, settings, today)
        val logger = RecordingLogger()
        val api = FakeApi { year, month ->
            if (YearMonth.of(year, month) == YearMonth.of(2026, 9)) {
                throw IOException("prefetch offline")
            }
            calendar(YearMonth.of(year, month))
        }
        val repository = repository(
            api = api,
            store = store,
            instant = Instant.parse("2026-08-25T10:00:00Z"),
            eventLogger = logger
        )

        val result = repository.refreshAndCache()

        assertTrue(result is RefreshResult.Success)
        assertEquals(
            listOf("prefetch_failure month=2026-09 error=network"),
            logger.events
        )
    }

    @Test
    fun existingNextMonthCoverageAvoidsASecondPrefetch() = runTest {
        val today = LocalDate.of(2026, 8, 31)
        val september = YearMonth.of(2026, 9)
        val existingSeptember = calendar(september).days.map {
            sampleCache(it.date, location)
        }
        val store = FakeStore(location, settings, today, existingSeptember)
        val api = FakeApi { year, month -> calendar(YearMonth.of(year, month)) }
        val repository = repository(api, store, Instant.parse("2026-08-31T10:00:00Z"))

        repository.refreshAndCache()

        assertEquals(listOf(YearMonth.of(2026, 8)), api.requestedMonths)
        assertEquals(61, store.days.size)
    }

    @Test
    fun incompleteCalendarIsRejectedBeforeAnyRowsAreWritten() = runTest {
        val today = LocalDate.of(2026, 8, 6)
        val store = FakeStore(location, settings, today)
        val incomplete = PrayerTimesCalendarResult(calendar(YearMonth.of(2026, 8)).days.dropLast(1))
        val repository = repository(FakeApi { _, _ -> incomplete }, store)

        val result = repository.refreshAndCache()

        assertTrue(result is RefreshResult.Failure)
        assertTrue(store.days.isEmpty())
    }

    @Test
    fun failedRefreshPropagatesCancellationFromCacheRead() = runTest {
        val today = LocalDate.of(2026, 8, 6)
        val cancellation = CancellationException("refresh cancelled")
        val store = FakeStore(location, settings, today, readCacheFailure = cancellation)
        val repository = repository(FakeApi { _, _ -> throw IllegalStateException("offline") }, store)

        var thrown: CancellationException? = null
        try {
            repository.refreshAndCache()
            fail("refreshAndCache should propagate cancellation")
        } catch (error: CancellationException) {
            thrown = error
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun cachedWidgetPropagatesCancellationFromCacheRead() = runTest {
        val today = LocalDate.of(2026, 8, 6)
        val cancellation = CancellationException("widget read cancelled")
        val store = FakeStore(location, settings, today, readCacheFailure = cancellation)
        val repository = repository(FakeApi { _, _ -> error("API should not be called") }, store)

        var thrown: CancellationException? = null
        try {
            repository.cachedWidget()
            fail("cachedWidget should propagate cancellation")
        } catch (error: CancellationException) {
            thrown = error
        }

        assertSame(cancellation, thrown)
    }

    private fun repository(
        api: PrayerTimesRemoteDataSource,
        store: FakeStore,
        instant: Instant = Instant.parse("2026-08-06T10:00:00Z"),
        eventLogger: PrayerEventLogger = DefaultPrayerEventLogger
    ) = PrayerTimesRepository(
        api,
        store,
        settings,
        PrayerTimeProvider(Clock.fixed(instant, PrayerTimeProvider.DEFAULT_ZONE)),
        eventLogger
    )

    private class RecordingLogger : PrayerEventLogger {
        val events = mutableListOf<String>()

        override fun warning(event: String, cause: Throwable) {
            events += event
        }
    }

    private class FakeApi(
        private val calendarOutcome: (Int, Int) -> PrayerTimesCalendarResult
    ) : PrayerTimesRemoteDataSource {
        val requestedMonths = mutableListOf<YearMonth>()

        override suspend fun fetchMonth(
            city: String,
            country: String,
            year: Int,
            month: Int,
            settings: PrayerCalculationSettings
        ): PrayerTimesCalendarResult {
            requestedMonths += YearMonth.of(year, month)
            return calendarOutcome(year, month)
        }
    }

    private class FakeStore(
        private var location: PrayerLocation,
        private val settings: PrayerCalculationSettings,
        private val today: LocalDate,
        initial: List<CachedPrayerDay> = emptyList(),
        private val readCacheFailure: CancellationException? = null
    ) : PrayerPreferences {
        val days = initial.associateByTo(mutableMapOf(), CachedPrayerDay::date)

        override suspend fun readCache(): CachedPrayerDay? {
            readCacheFailure?.let { throw it }
            return days.values
                .filter { !it.date.isAfter(today) }
                .maxByOrNull(CachedPrayerDay::date)
        }

        override suspend fun readCache(
            date: LocalDate,
            location: PrayerLocation,
            settings: PrayerCalculationSettings
        ): CachedPrayerDay? {
            readCacheFailure?.let { throw it }
            return days[date]?.takeIf {
                it.location.city == location.city &&
                    it.location.country == location.country &&
                    it.settings == settings
            }
        }

        override suspend fun saveCache(cache: CachedPrayerDay) {
            days[cache.date] = cache
        }

        override suspend fun saveCaches(caches: List<CachedPrayerDay>) {
            caches.forEach { days[it.date] = it }
        }

        override suspend fun hasCoverage(
            startDate: LocalDate,
            endDate: LocalDate,
            location: PrayerLocation,
            settings: PrayerCalculationSettings
        ): Boolean = generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .all { date -> readCache(date, location, settings) != null }

        override suspend fun readLocation() = location
        override suspend fun saveLocation(location: PrayerLocation) {
            this.location = location
        }

        override suspend fun clearCache() {
            days.clear()
        }
    }

    private fun calendar(
        month: YearMonth,
        timezone: String = PrayerTimeProvider.DEFAULT_ZONE.id
    ) = PrayerTimesCalendarResult(
        (1..month.lengthOfMonth()).map { day ->
            PrayerTimesApiDayResult(
                month.atDay(day),
                PrayerTimesApiResult(sampleTimes(), "$day Safer 1448", timezone)
            )
        }
    )

    private fun sampleTimes() = PrayerTimes(
        LocalTime.of(4, 12), LocalTime.of(5, 49), LocalTime.of(13, 8),
        LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
    )

    private fun sampleCache(date: LocalDate, location: PrayerLocation) = CachedPrayerDay(
        date, location, PrayerTimeProvider.DEFAULT_ZONE, settings,
        sampleTimes(), null, Instant.parse("2026-08-01T00:00:00Z")
    )
}
