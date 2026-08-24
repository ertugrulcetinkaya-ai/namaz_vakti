package com.example.namazvakti

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerTimesDatabaseInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: PrayerTimesDatabase
    private lateinit var dao: PrayerDayDao

    @Before
    fun createDatabase() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PrayerTimesDatabase::class.java
        ).build()
        dao = database.prayerDayDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun fullMonthRoundTripsWithSourceAndCalculationMetadata() = runBlocking {
        val days = (1..31).map { day -> cache(LocalDate.of(2026, 8, day)).toEntity() }

        dao.upsertAll(days)

        assertEquals(
            31,
            dao.countBetween("2026-08-01", "2026-08-31", "Ankara", "Turkey", 13, 0)
        )
        val restored = requireNotNull(
            dao.find("2026-08-23", "Ankara", "Turkey", 13, 0)
        ).toDomain()
        assertEquals(LocalDate.of(2026, 8, 23), restored.date)
        assertEquals(PrayerDataSource.ALADHAN, restored.source)
        assertEquals(PrayerCalculationSettings(13, 0), restored.settings)
    }

    @Test
    fun latestOnOrBeforeNeverReturnsAFutureDay() = runBlocking {
        dao.upsertAll(
            listOf(
                cache(LocalDate.of(2026, 8, 22)).toEntity(),
                cache(LocalDate.of(2026, 8, 24)).toEntity()
            )
        )

        val latest = dao.latestOnOrBefore(
            "2026-08-23", "Ankara", "Turkey", 13, 0
        )?.toDomain()

        assertEquals(LocalDate.of(2026, 8, 22), latest?.date)
        assertNull(dao.find("2026-08-23", "Ankara", "Turkey", 13, 0))
    }

    @Test
    fun calculationMethodAndSchoolArePartOfTheCacheIdentity() = runBlocking {
        val date = LocalDate.of(2026, 8, 23)
        dao.upsertAll(
            listOf(
                cache(date, PrayerCalculationSettings(13, 0)).toEntity(),
                cache(date, PrayerCalculationSettings(13, 1)).toEntity()
            )
        )

        assertEquals(0, dao.find(date.toString(), "Ankara", "Turkey", 13, 0)?.school)
        assertEquals(1, dao.find(date.toString(), "Ankara", "Turkey", 13, 1)?.school)
    }

    @Test
    fun storeSelectsTodayInsteadOfAFuturePrefetchedDay() = runBlocking {
        val instant = Instant.parse("2026-08-23T08:00:00Z")
        val settings = PrayerCalculationSettings()
        val store = PrayerTimesStore(
            context,
            dao,
            PrayerTimeProvider(Clock.fixed(instant, PrayerTimeProvider.DEFAULT_ZONE)),
            settings
        )
        store.saveCaches(
            listOf(
                cache(LocalDate.of(2026, 8, 23)),
                cache(LocalDate.of(2026, 9, 1))
            )
        )

        assertEquals(LocalDate.of(2026, 8, 23), store.readCache()?.date)
        assertEquals(
            LocalDate.of(2026, 9, 1),
            store.readCache(
                LocalDate.of(2026, 9, 1),
                PrayerLocation("Ankara", "Turkey", "ANKARA"),
                settings
            )?.date
        )
    }

    private fun cache(
        date: LocalDate,
        settings: PrayerCalculationSettings = PrayerCalculationSettings()
    ) = CachedPrayerDay(
        date = date,
        location = PrayerLocation("Ankara", "Turkey", "ANKARA"),
        timezone = PrayerTimeProvider.DEFAULT_ZONE,
        settings = settings,
        prayerTimes = PrayerTimes(
            LocalTime.of(4, 29), LocalTime.of(6, 1), LocalTime.of(12, 56),
            LocalTime.of(16, 41), LocalTime.of(19, 42), LocalTime.of(21, 7)
        ),
        hijriText = "10 Rebiülevvel 1448",
        fetchedAt = Instant.parse("2026-08-23T08:00:00Z"),
        source = PrayerDataSource.ALADHAN
    )
}
