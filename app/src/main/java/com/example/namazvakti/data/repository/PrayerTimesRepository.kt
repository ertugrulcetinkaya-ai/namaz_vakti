package com.example.namazvakti.data.repository

import com.example.namazvakti.data.local.PrayerPreferences
import com.example.namazvakti.data.remote.PrayerTimesApi
import com.example.namazvakti.data.remote.PrayerTimesApiDayResult
import com.example.namazvakti.data.remote.PrayerTimesApiResult
import com.example.namazvakti.data.remote.PrayerTimesCalendarResult
import com.example.namazvakti.data.remote.PrayerTimesRemoteDataSource
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DefaultPrayerEventLogger
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.DiyanetRamadanCalendar
import com.example.namazvakti.domain.model.MonthDataSource
import com.example.namazvakti.domain.model.MonthResult
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerEventLogger
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.example.namazvakti.domain.model.RamadanRange
import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.domain.model.RefreshOrigin
import com.example.namazvakti.domain.model.classifyPrayerStorageError
import com.example.namazvakti.domain.model.classifyPrayerError
import com.example.namazvakti.domain.model.code
import com.example.namazvakti.domain.port.PrayerRefreshRepository
import java.time.ZoneId
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PrayerTimesRepository(
    private val api: PrayerTimesRemoteDataSource = PrayerTimesApi(),
    private val store: PrayerPreferences,
    private val settings: PrayerCalculationSettings = PrayerCalculationSettings(),
    private val timeProvider: PrayerTimeProvider = PrayerTimeProvider(),
    private val eventLogger: PrayerEventLogger = DefaultPrayerEventLogger
) : PrayerRefreshRepository {
    private val monthMutexes = ConcurrentHashMap<MonthRequestKey, Mutex>()

    override suspend fun refreshAndCache(): RefreshResult {
        val location = try {
            store.readLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            return RefreshResult.Failure(t, classifyPrayerStorageError(t))
        }
        val today = timeProvider.today(location.timezone)
        return try {
            val currentMonth = YearMonth.from(today)
            val currentDays = fetchAndCacheMonth(location, currentMonth)
            prefetchNextMonthIfNeeded(location, today, currentMonth)
            val todayCache = currentDays.firstOrNull { it.date == today }
                ?: store.readCache(today, location, settings)
                ?: throw PrayerTimesApiException("Calendar response has no data for $today")
            RefreshResult.Success(todayCache, RefreshOrigin.NETWORK)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            failedRefresh(t, location, today)
        }
    }

    private suspend fun fetchAndCacheMonth(
        location: PrayerLocation,
        month: YearMonth
    ): List<CachedPrayerDay> {
        val response = api.fetchMonth(
            city = location.city,
            country = location.country,
            year = month.year,
            month = month.monthValue,
            settings = settings,
            latitude = location.latitude,
            longitude = location.longitude,
            timezone = location.timezone.id
        )
        val expectedDates = (1..month.lengthOfMonth()).map(month::atDay)
        if (response.days.map(PrayerTimesApiDayResult::date) != expectedDates) {
            throw PrayerTimesApiException("Incomplete or unexpected calendar for $month")
        }
        val fetchedAt = timeProvider.now(location.timezone).toInstant()
        val caches = response.days.map { day ->
            val timezone = day.result.timezone
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: location.timezone
            CachedPrayerDay(
                date = day.date,
                location = location.copy(timezone = timezone),
                timezone = timezone,
                settings = settings,
                prayerTimes = day.result.prayerTimes,
                hijriText = day.result.hijriText,
                fetchedAt = fetchedAt,
                source = PrayerDataSource.ALADHAN,
                hijriDay = day.result.hijriDay,
                hijriMonth = day.result.hijriMonth,
                hijriYear = day.result.hijriYear
            )
        }
        store.saveCaches(caches)
        return caches
    }

    private suspend fun prefetchNextMonthIfNeeded(
        location: PrayerLocation,
        today: java.time.LocalDate,
        currentMonth: YearMonth
    ) {
        if (today.dayOfMonth <= currentMonth.lengthOfMonth() - PREFETCH_DAYS) return
        val nextMonth = currentMonth.plusMonths(1)
        val covered = store.hasCoverage(
            nextMonth.atDay(1), nextMonth.atEndOfMonth(), location, settings
        )
        if (covered) return
        try {
            fetchAndCacheMonth(location, nextMonth)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = classifyPrayerError(e)
            eventLogger.warning(
                "prefetch_failure month=$nextMonth error=${error.code}",
                e
            )
            // The current month is already safely cached; a later daily refresh retries prefetching.
        }
    }

    private suspend fun failedRefresh(
        cause: Exception,
        location: PrayerLocation,
        today: java.time.LocalDate
    ): RefreshResult {
        val error = classifyPrayerError(cause)
        val todayRead = readCache(today, location, settings)
        if (todayRead.cache != null) {
            return RefreshResult.Success(todayRead.cache, RefreshOrigin.LOCAL_CACHE)
        }
        val olderRead = readCache()
        val fallbackFailure = todayRead.failure ?: olderRead.failure
        if (fallbackFailure != null) {
            val fallbackError = classifyPrayerStorageError(fallbackFailure)
            eventLogger.warning(
                "event=fallback_cache_read_failure primary_error=${error.code} " +
                    "fallback_error=${fallbackError.code}",
                fallbackFailure
            )
        }
        return if (olderRead.cache != null) {
            RefreshResult.StaleCache(olderRead.cache, cause, error)
        } else {
            RefreshResult.Failure(cause, error)
        }
    }

    suspend fun selectedLocation(): PrayerLocation = store.readLocation()

    override suspend fun ensureMonth(yearMonth: YearMonth): MonthResult {
        val location = store.readLocation()
        val key = MonthRequestKey(yearMonth, location.city, location.country, settings)
        val mutex = monthMutexes.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            val start = yearMonth.atDay(1)
            val end = yearMonth.atEndOfMonth()
            val cached = store.readCaches(start, end, location, settings)
            if (store.hasCoverage(start, end, location, settings)) {
                return@withLock MonthResult(yearMonth, cached, MonthDataSource.CACHE)
            }
            try {
                MonthResult(yearMonth, fetchAndCacheMonth(location, yearMonth), MonthDataSource.NETWORK)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val partial = store.readCaches(start, end, location, settings)
                MonthResult(yearMonth, partial, MonthDataSource.PARTIAL, e)
            }
        }
    }

    override suspend fun ramadanRange(): RamadanRange? {
        val location = store.readLocation()
        val today = timeProvider.today(location.timezone)
        val current = store.readCache(today, location, settings)
            ?: store.readCache()?.takeIf { it.date == today }
        val targetHijriYear = when {
            current?.hijriMonth != null && current.hijriYear != null ->
                current.hijriYear + if (current.hijriMonth <= 9) 0 else 1
            else -> DiyanetRamadanCalendar.entries.values
                .sortedBy { it.firstRamadan }
                .firstOrNull { it.firstShawwal.isAfter(today) }
                ?.hijriYear ?: today.year - 578
        }

        DiyanetRamadanCalendar.find(targetHijriYear)?.let { return it.toRange() }

        return try {
            val firstRamadan = api.fetchHijriMonth(9, targetHijriYear).minOrNull() ?: return null
            val firstShawwal = api.fetchHijriMonth(10, targetHijriYear).minOrNull() ?: return null
            val range = RamadanRange(
                hijriYear = targetHijriYear,
                startDate = firstRamadan,
                endDate = firstShawwal.minusDays(1),
                eidDate = firstShawwal,
                estimated = true
            )
            val firstMonth = YearMonth.from(range.startDate)
            val lastMonth = YearMonth.from(range.endDate)
            val monthRows = mutableListOf<CachedPrayerDay>()
            var month = firstMonth
            while (!month.isAfter(lastMonth)) {
                monthRows += ensureMonth(month).days
                month = month.plusMonths(1)
            }
            val inconsistent = monthRows.any { day ->
                val expectedDay = range.dayNumber(day.date) ?: return@any false
                day.hijriDay?.let { it != expectedDay } == true ||
                    day.hijriMonth?.let { it != 9 } == true ||
                    day.hijriYear?.let { it != targetHijriYear } == true
            }
            if (inconsistent) {
                throw PrayerTimesApiException(
                    "AlAdhan Hijri range does not match the prayer calendar for $targetHijriYear"
                )
            }
            range
        } catch (e: CancellationException) {
            throw e
        } catch (e: PrayerTimesApiException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun cachedWidget(): CachedPrayerDay? {
        val location = store.readLocation()
        val today = timeProvider.today(location.timezone)
        return readCache(today, location, settings).cache ?: readCache().cache
    }

    private data class CacheRead(
        val cache: CachedPrayerDay?,
        val failure: Throwable? = null
    )

    private suspend fun readCache(
        date: java.time.LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): CacheRead = try {
        CacheRead(store.readCache(date, location, settings))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CacheRead(cache = null, failure = e)
    }

    private suspend fun readCache(): CacheRead = try {
        CacheRead(store.readCache())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CacheRead(cache = null, failure = e)
    }

    private companion object {
        const val PREFETCH_DAYS = 7
    }

    private data class MonthRequestKey(
        val yearMonth: YearMonth,
        val city: String,
        val country: String,
        val settings: PrayerCalculationSettings
    )
}
