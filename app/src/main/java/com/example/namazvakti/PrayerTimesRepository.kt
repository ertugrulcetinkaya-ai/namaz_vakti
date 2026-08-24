package com.example.namazvakti

import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.CancellationException

interface PrayerRefreshRepository {
    suspend fun refreshAndCache(): RefreshResult
}

class PrayerTimesRepository(
    private val api: PrayerTimesRemoteDataSource = PrayerTimesApi(),
    private val store: PrayerPreferences,
    private val settings: PrayerCalculationSettings = PrayerCalculationSettings(),
    private val timeProvider: PrayerTimeProvider = PrayerTimeProvider()
) : PrayerRefreshRepository {
    override suspend fun refreshAndCache(): RefreshResult {
        val location = try {
            store.readLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            return RefreshResult.Failure(t)
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
            location.city, location.country, month.year, month.monthValue, settings
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
                source = PrayerDataSource.ALADHAN
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
        } catch (_: Exception) {
            // The current month is already safely cached; a later daily refresh retries prefetching.
        }
    }

    private suspend fun failedRefresh(
        cause: Exception,
        location: PrayerLocation,
        today: java.time.LocalDate
    ): RefreshResult {
        val todayCache = runCatching { store.readCache(today, location, settings) }.getOrNull()
        if (todayCache != null) return RefreshResult.Success(todayCache, RefreshOrigin.LOCAL_CACHE)
        val olderCache = runCatching { store.readCache() }.getOrNull()
        return if (olderCache != null) {
            RefreshResult.StaleCache(olderCache, cause)
        } else {
            RefreshResult.Failure(cause)
        }
    }

    suspend fun selectedLocation(): PrayerLocation = store.readLocation()

    suspend fun cachedWidget(): CachedPrayerDay? {
        val location = store.readLocation()
        val today = timeProvider.today(location.timezone)
        return runCatching { store.readCache(today, location, settings) }.getOrNull()
            ?: runCatching { store.readCache() }.getOrNull()
    }

    private companion object {
        const val PREFETCH_DAYS = 7
    }
}

enum class RefreshOrigin { NETWORK, LOCAL_CACHE }

sealed interface RefreshResult {
    data class Success(
        val cache: CachedPrayerDay,
        val origin: RefreshOrigin = RefreshOrigin.NETWORK
    ) : RefreshResult
    data class StaleCache(val cache: CachedPrayerDay, val cause: Throwable) : RefreshResult
    data class Failure(val cause: Throwable) : RefreshResult
}
