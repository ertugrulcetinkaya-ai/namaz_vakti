package com.example.namazvakti

import com.example.namazvakti.data.local.CachedPrayerDayCodec
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CachedPrayerDayCodecTest {
    private val codec = CachedPrayerDayCodec()

    @Test
    fun roundTripPreservesStructuredCache() {
        val original = CachedPrayerDay(
            date = LocalDate.of(2026, 8, 6),
            location = PrayerLocation("Ankara", "Turkey", "ANKARA"),
            timezone = PrayerTimeProvider.DEFAULT_ZONE,
            settings = PrayerCalculationSettings(method = 3, school = 0),
            prayerTimes = PrayerTimes(
                LocalTime.of(4, 12, 7), LocalTime.of(5, 49), LocalTime.of(13, 8),
                LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
            ),
            hijriText = "12 Ramazan 1447",
            fetchedAt = Instant.parse("2026-08-06T00:00:00Z")
        )

        val restored = codec.decode(codec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun corruptJsonAndInvalidFieldsReturnNull() {
        assertNull(codec.decode("not-json"))
        assertNull(codec.decode("""{"date":"2026-08-06","timezone":"Not/AZone"}"""))
        assertNull(codec.decode("""{"schemaVersion":99}"""))
    }

    @Test
    fun roundTripKeepsLocationAndApiTimezonesSeparate() {
        val original = sampleCache().copy(
            location = PrayerLocation("Los Angeles", "USA", "LOS ANGELES", ZoneId.of("America/New_York")),
            timezone = ZoneId.of("America/Los_Angeles")
        )

        val restored = codec.decode(codec.encode(original))

        assertEquals(ZoneId.of("America/New_York"), restored?.location?.timezone)
        assertEquals(ZoneId.of("America/Los_Angeles"), restored?.timezone)
    }

    private fun sampleCache() = CachedPrayerDay(
        date = LocalDate.of(2026, 8, 6),
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
