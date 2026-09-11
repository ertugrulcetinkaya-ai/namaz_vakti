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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerWidgetSnapshotTest {
    @Test
    fun loadsOneConsistentSnapshotWithOneLocationAndCacheRead() =
        kotlinx.coroutines.test.runTest {
            val location = PrayerLocation("Ankara", "Turkey", "ANKARA")
            val cache = CachedPrayerDay(
                date = LocalDate.of(2026, 8, 6),
                location = location,
                timezone = PrayerTimeProvider.DEFAULT_ZONE,
                settings = PrayerCalculationSettings(),
                prayerTimes = PrayerTimes(
                    LocalTime.of(4, 12), LocalTime.of(5, 49), LocalTime.of(13, 8),
                    LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
                ),
                hijriText = null,
                fetchedAt = Instant.parse("2026-08-06T00:00:00Z")
            )
            val store = CountingStore(location, cache)
            val source = PrayerWidgetSnapshotSource(
                store = store,
                settings = PrayerCalculationSettings(),
                timeProvider = PrayerTimeProvider(
                    Clock.fixed(Instant.parse("2026-08-06T10:00:00Z"), ZoneId.of("UTC"))
                ),
                cachePolicy = PrayerCachePolicy()
            )

            val snapshot = source.load()

            assertEquals(1, store.readLocationCalls)
            assertEquals(1, store.readCacheCalls)
            assertEquals(cache, snapshot.cache)
            assertEquals(PrayerWidgetDataState.Fresh, snapshot.dataState)
        }

    private class CountingStore(
        private val location: PrayerLocation,
        private val cache: CachedPrayerDay
    ) : PrayerPreferences {
        var readLocationCalls = 0
        var readCacheCalls = 0

        override suspend fun readCache(): CachedPrayerDay? {
            readCacheCalls += 1
            return cache
        }

        override suspend fun saveCache(cache: CachedPrayerDay) = Unit

        override suspend fun readLocation(): PrayerLocation {
            readLocationCalls += 1
            return location
        }

        override suspend fun saveLocation(location: PrayerLocation) = Unit
        override suspend fun clearCache() = Unit
    }
}
