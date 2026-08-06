package com.example.namazvakti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

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
    }
}
