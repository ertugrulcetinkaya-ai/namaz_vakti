package com.example.namazvakti

import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId

class PrayerTimeProvider(
    private val clock: Clock = Clock.system(DEFAULT_ZONE)
) {
    fun today(zoneId: ZoneId = DEFAULT_ZONE): LocalDate = now(zoneId).toLocalDate()

    fun now(zoneId: ZoneId = DEFAULT_ZONE): ZonedDateTime =
        ZonedDateTime.now(clock).withZoneSameInstant(zoneId)

    companion object {
        val DEFAULT_ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
    }
}
