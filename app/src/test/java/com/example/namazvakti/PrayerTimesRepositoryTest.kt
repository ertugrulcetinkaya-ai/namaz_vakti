package com.example.namazvakti

import com.example.namazvakti.data.local.PrayerPreferences
import com.example.namazvakti.data.remote.PrayerTimesApiDayResult
import com.example.namazvakti.data.remote.PrayerTimesApiResult
import com.example.namazvakti.data.remote.PrayerTimesCalendarResult
import com.example.namazvakti.data.remote.PrayerTimesRemoteDataSource
import com.example.namazvakti.data.repository.PrayerTimesRepository
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DefaultPrayerEventLogger
import com.example.namazvakti.domain.model.MonthDataSource
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerEventLogger
import com.example.namazvakti.domain.model.PrayerStorageException
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.RefreshOrigin
import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.domain.model.StorageFailureKind
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    fun fallbackCacheReadFailureIsLoggedWithoutExposingLocationData() = runTest {
        val today = LocalDate.of(2026, 8, 6)
        val logger = RecordingLogger()
        val storageFailure = PrayerStorageException(
            StorageFailureKind.UNKNOWN,
            "cache read failed"
        )
        val store = FakeStore(
            location,
            settings,
            today,
            readCacheFailure = storageFailure
        )
        val repository = repository(
            api = FakeApi { _, _ -> throw IOException("offline") },
            store = store,
            eventLogger = logger
        )

        val result = repository.refreshAndCache()

        assertTrue(result is RefreshResult.Failure)
        assertEquals(
            listOf(
                "event=fallback_cache_read_failure " +
                    "primary_error=network fallback_error=storage"
            ),
            logger.events
        )
        assertTrue(logger.events.none { it.contains("Ankara") || it.contains("Turkey") })
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

    @Test
    fun ensureMonthUsesCompleteCacheWithoutCallingNetwork() = runTest {
        val month = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 8, 6)
        val initial = (1..month.lengthOfMonth()).map { sampleCache(month.atDay(it), location) }
        val store = FakeStore(location, settings, today, initial)
        val api = FakeApi { _, _ -> error("covered month must not use network") }

        val result = repository(api, store).ensureMonth(month)

        assertEquals(MonthDataSource.CACHE, result.source)
        assertEquals(31, result.days.size)
        assertTrue(api.requestedMonths.isEmpty())
    }

    @Test
    fun ensureMonthFetchesAndCachesMissingMonth() = runTest {
        val month = YearMonth.of(2026, 8)
        val store = FakeStore(location, settings, LocalDate.of(2026, 8, 6))
        val api = FakeApi { year, value -> calendar(YearMonth.of(year, value)) }

        val result = repository(api, store).ensureMonth(month)

        assertEquals(MonthDataSource.NETWORK, result.source)
        assertEquals(31, result.days.size)
        assertEquals(listOf(month), api.requestedMonths)
    }

    @Test
    fun ensureMonthReturnsAvailableRowsWhenNetworkFails() = runTest {
        val month = YearMonth.of(2026, 8)
        val first = sampleCache(month.atDay(1), location)
        val store = FakeStore(location, settings, LocalDate.of(2026, 8, 6), listOf(first))
        val api = FakeApi { _, _ -> throw IOException("offline") }

        val result = repository(api, store).ensureMonth(month)

        assertEquals(MonthDataSource.PARTIAL, result.source)
        assertEquals(listOf(first), result.days)
        assertTrue(result.error is IOException)
    }

    @Test
    fun concurrentEnsureMonthCallsShareOneNetworkRequest() = runTest {
        val month = YearMonth.of(2026, 8)
        val store = FakeStore(location, settings, LocalDate.of(2026, 8, 6))
        var requests = 0
        val api = object : PrayerTimesRemoteDataSource {
            override suspend fun fetchMonth(
                city: String,
                country: String,
                year: Int,
                month: Int,
                settings: PrayerCalculationSettings,
                latitude: Double?,
                longitude: Double?,
                timezone: String
            ): PrayerTimesCalendarResult {
                requests++
                delay(10)
                return calendar(YearMonth.of(year, month))
            }
        }
        val repository = repository(api, store)
        val first = async { repository.ensureMonth(month) }
        val second = async { repository.ensureMonth(month) }

        first.await()
        second.await()

        assertEquals(1, requests)
    }

    @Test
    fun official1447RangeOverridesAlAdhanOneDayEarlyStart() = runTest {
        val today = LocalDate.of(2026, 2, 18)
        val store = FakeStore(
            location,
            settings,
            today,
            listOf(sampleCache(today, location).copy(hijriDay = 1, hijriMonth = 9, hijriYear = 1447))
        )
        val api = FakeApi { _, _ -> error("official calendar must win") }

        val range = repository(api, store, Instant.parse("2026-02-18T10:00:00Z")).ramadanRange()

        assertEquals(LocalDate.of(2026, 2, 19), range?.startDate)
        assertEquals(false, range?.estimated)
        assertTrue(api.hijriMonthsRequested.isEmpty())
    }

    @Test
    fun hijriMonthAfterRamadanSelectsNextHijriYear() = runTest {
        val today = LocalDate.of(2026, 11, 4)
        val store = FakeStore(
            location,
            settings,
            today,
            listOf(sampleCache(today, location).copy(hijriDay = 23, hijriMonth = 10, hijriYear = 1447))
        )

        val range = repository(
            FakeApi { _, _ -> error("official 1448 dates are available") },
            store,
            Instant.parse("2026-11-04T10:00:00Z")
        ).ramadanRange()

        assertEquals(1448, range?.hijriYear)
        assertEquals(LocalDate.of(2027, 2, 8), range?.startDate)
    }

    @Test
    fun unlistedHijriYearUsesAlAdhanAndMarksRangeEstimated() = runTest {
        val today = LocalDate.of(2036, 5, 1)
        val store = FakeStore(
            location,
            settings,
            today,
            listOf(sampleCache(today, location).copy(hijriDay = 20, hijriMonth = 8, hijriYear = 1458))
        )
        val api = FakeApi(
            calendarOutcome = { year, month ->
                val response = calendar(YearMonth.of(year, month))
                response.copy(
                    days = response.days.map { day ->
                        val ramadanDay = java.time.temporal.ChronoUnit.DAYS.between(
                            LocalDate.of(2036, 10, 21), day.date
                        ).toInt() + 1
                        if (ramadanDay in 1..30) {
                            day.copy(
                                result = day.result.copy(
                                    hijriDay = ramadanDay,
                                    hijriMonth = 9,
                                    hijriYear = 1458
                                )
                            )
                        } else {
                            day
                        }
                    }
                )
            },
            hijriOutcome = { month, _ ->
                when (month) {
                    9 -> (0..29).map { LocalDate.of(2036, 10, 21).plusDays(it.toLong()) }
                    10 -> (0..28).map { LocalDate.of(2036, 11, 20).plusDays(it.toLong()) }
                    else -> emptyList()
                }
            }
        )

        val range = repository(api, store, Instant.parse("2036-05-01T10:00:00Z")).ramadanRange()

        assertEquals(1458, range?.hijriYear)
        assertEquals(LocalDate.of(2036, 10, 21), range?.startDate)
        assertEquals(LocalDate.of(2036, 11, 19), range?.endDate)
        assertEquals(LocalDate.of(2036, 11, 20), range?.eidDate)
        assertEquals(true, range?.estimated)
        assertEquals(listOf(9, 10), api.hijriMonthsRequested.map { it.first })
        assertEquals(listOf(YearMonth.of(2036, 10), YearMonth.of(2036, 11)), api.requestedMonths)
    }

    @Test
    fun unlistedHijriYearFailsWhenRoomHijriDatesConflictWithAlAdhanRange() = runTest {
        val today = LocalDate.of(2036, 5, 1)
        val store = FakeStore(
            location,
            settings,
            today,
            listOf(sampleCache(today, location).copy(hijriDay = 20, hijriMonth = 8, hijriYear = 1458))
        )
        val api = FakeApi(
            calendarOutcome = { year, month ->
                val response = calendar(YearMonth.of(year, month))
                response.copy(
                    days = response.days.map { day ->
                        val ramadanDay = java.time.temporal.ChronoUnit.DAYS.between(
                            LocalDate.of(2036, 10, 21), day.date
                        ).toInt() + 1
                        if (ramadanDay in 1..30) {
                            day.copy(
                                result = day.result.copy(
                                    hijriDay = if (ramadanDay == 1) 2 else ramadanDay,
                                    hijriMonth = 9,
                                    hijriYear = 1458
                                )
                            )
                        } else {
                            day
                        }
                    }
                )
            },
            hijriOutcome = { month, _ ->
                when (month) {
                    9 -> (0..29).map { LocalDate.of(2036, 10, 21).plusDays(it.toLong()) }
                    10 -> (0..28).map { LocalDate.of(2036, 11, 20).plusDays(it.toLong()) }
                    else -> emptyList()
                }
            }
        )

        try {
            repository(api, store, Instant.parse("2036-05-01T10:00:00Z")).ramadanRange()
            fail("a Hijri calendar mismatch should be reported")
        } catch (error: PrayerTimesApiException) {
            assertTrue(error.message.orEmpty().contains("does not match"))
        }
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
        private val hijriOutcome: (Int, Int) -> List<LocalDate> = { _, _ -> emptyList() },
        private val calendarOutcome: (Int, Int) -> PrayerTimesCalendarResult
    ) : PrayerTimesRemoteDataSource {
        val requestedMonths = mutableListOf<YearMonth>()
        val hijriMonthsRequested = mutableListOf<Pair<Int, Int>>()

        override suspend fun fetchMonth(
            city: String,
            country: String,
            year: Int,
            month: Int,
            settings: PrayerCalculationSettings,
            latitude: Double?,
            longitude: Double?,
            timezone: String
        ): PrayerTimesCalendarResult {
            requestedMonths += YearMonth.of(year, month)
            return calendarOutcome(year, month)
        }

        override suspend fun fetchHijriMonth(month: Int, hijriYear: Int): List<LocalDate> {
            hijriMonthsRequested += month to hijriYear
            return hijriOutcome(month, hijriYear)
        }
    }

    private class FakeStore(
        private var location: PrayerLocation,
        private val settings: PrayerCalculationSettings,
        private val today: LocalDate,
        initial: List<CachedPrayerDay> = emptyList(),
        private val readCacheFailure: Throwable? = null
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
