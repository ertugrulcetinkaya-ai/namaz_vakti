package com.example.namazvakti

import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId

class PrayerTimeProvider(
    private val clock: Clock = Clock.system(DEFAULT_ZONE)
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun now(): ZonedDateTime = ZonedDateTime.now(clock)

    companion object {
        val DEFAULT_ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
    }
}
