package com.example.namazvakti.widget

import android.content.Context
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.data.local.PrayerPreferences
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DiyanetRamadanCalendar
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.RamadanRange
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.policy.PrayerCachePolicy
import com.example.namazvakti.widget.renderer.PrayerWidgetDataState
import java.time.ZonedDateTime

data class PrayerWidgetSnapshot(
    val cache: CachedPrayerDay?,
    val location: PrayerLocation,
    val now: ZonedDateTime,
    val dataState: PrayerWidgetDataState,
    val tomorrowCache: CachedPrayerDay? = null,
    val ramadanRange: RamadanRange? = null
)

fun PrayerWidgetSnapshot.withCache(
    cache: CachedPrayerDay?,
    settings: PrayerCalculationSettings,
    cachePolicy: PrayerCachePolicy,
    now: ZonedDateTime
): PrayerWidgetSnapshot {
    val nextLocation = cache?.location ?: location
    val nextState = when {
        cache == null -> PrayerWidgetDataState.Unavailable
        cachePolicy.isFresh(cache, nextLocation, settings, now) -> PrayerWidgetDataState.Fresh
        else -> PrayerWidgetDataState.Stale
    }
    return copy(
        cache = cache,
        location = nextLocation,
        now = now,
        dataState = nextState
    )
}

class PrayerWidgetSnapshotSource(
    private val store: PrayerPreferences,
    private val settings: PrayerCalculationSettings,
    private val timeProvider: PrayerTimeProvider,
    private val cachePolicy: PrayerCachePolicy
) {
    suspend fun load(): PrayerWidgetSnapshot {
        val location = store.readLocation()
        val now = timeProvider.now()
        val today = now.withZoneSameInstant(location.timezone).toLocalDate()
        val cache = store.readCache(today, location, settings) ?: store.readCache()
        val dataState = when {
            cache == null -> PrayerWidgetDataState.Unavailable
            cachePolicy.isFresh(cache, location, settings, now) ->
                PrayerWidgetDataState.Fresh
            else -> PrayerWidgetDataState.Stale
        }
        val tomorrowCache = if (cache?.date == today) {
            store.readCache(today.plusDays(1), location, settings)
        } else null
        val ramadanRange = DiyanetRamadanCalendar.entries.values
            .firstOrNull { today in it.firstRamadan..it.firstShawwal.minusDays(1) }
            ?.toRange()
        return PrayerWidgetSnapshot(
            cache = cache,
            location = location,
            now = now,
            dataState = dataState,
            tomorrowCache = tomorrowCache,
            ramadanRange = ramadanRange
        )
    }
}

object PrayerWidgetSnapshotLoader {
    suspend fun load(context: Context): PrayerWidgetSnapshot {
        val appContext = context.applicationContext
        val container = appContext.appContainer()
        return PrayerWidgetSnapshotSource(
            store = container.store,
            settings = container.settings,
            timeProvider = container.timeProvider,
            cachePolicy = container.cachePolicy
        ).load()
    }
}
