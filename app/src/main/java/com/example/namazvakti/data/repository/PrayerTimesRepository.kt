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
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.PrayerEventLogger
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.domain.model.RefreshOrigin
import com.example.namazvakti.domain.model.classifyPrayerError
import com.example.namazvakti.domain.model.code
import com.example.namazvakti.domain.port.PrayerRefreshRepository
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.CancellationException

class PrayerTimesRepository(
    private val api: PrayerTimesRemoteDataSource = PrayerTimesApi(),
    private val store: PrayerPreferences,
    private val settings: PrayerCalculationSettings = PrayerCalculationSettings(),
    private val timeProvider: PrayerTimeProvider = PrayerTimeProvider(),
    private val eventLogger: PrayerEventLogger = DefaultPrayerEventLogger
) : PrayerRefreshRepository {
    override suspend fun refreshAndCache(): RefreshResult {
        val location = try {
            store.readLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            return RefreshResult.Failure(t, PrayerError.Storage)
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
        val todayCache = readCacheOrNull(today, location, settings)
        if (todayCache != null) return RefreshResult.Success(todayCache, RefreshOrigin.LOCAL_CACHE)
        val olderCache = readCacheOrNull()
        return if (olderCache != null) {
            RefreshResult.StaleCache(olderCache, cause, error)
        } else {
            RefreshResult.Failure(cause, error)
        }
    }

    suspend fun selectedLocation(): PrayerLocation = store.readLocation()

    suspend fun cachedWidget(): CachedPrayerDay? {
        val location = store.readLocation()
        val today = timeProvider.today(location.timezone)
        return readCacheOrNull(today, location, settings) ?: readCacheOrNull()
    }

    private suspend fun readCacheOrNull(
        date: java.time.LocalDate,
        location: PrayerLocation,
        settings: PrayerCalculationSettings
    ): CachedPrayerDay? = try {
        store.readCache(date, location, settings)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun readCacheOrNull(): CachedPrayerDay? = try {
        store.readCache()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val PREFETCH_DAYS = 7
    }
}
