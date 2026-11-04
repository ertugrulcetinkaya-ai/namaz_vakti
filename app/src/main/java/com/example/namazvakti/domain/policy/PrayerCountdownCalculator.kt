package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.PrayerTimes
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class PrayerCountdown(
    val activePrayer: PrayerTimeName,
    val nextPrayer: PrayerTimeName?,
    val nextPrayerAt: ZonedDateTime?,
    val remaining: Duration?
)

object PrayerCountdownCalculator {
    fun calculate(
        today: PrayerTimes,
        date: LocalDate,
        timezone: ZoneId,
        tomorrow: PrayerTimes?,
        now: ZonedDateTime
    ): PrayerCountdown {
        val localNow = now.withZoneSameInstant(timezone)
        val localTime = localNow.toLocalTime()
        val activePrayer = today.currentPrayerName(localTime)
        val nextPrayerToday = today.nextPrayerName(localTime)
        val nextPrayer = nextPrayerToday ?: tomorrow?.let { PrayerTimeName.IMSAK }
        val nextPrayerDate = if (nextPrayerToday == null) date.plusDays(1) else date
        val nextPrayerTime = nextPrayer?.let { prayer ->
            val prayerTimes = if (nextPrayerDate == date) today else tomorrow
            prayerTimes?.timeAt(prayer)?.let { time ->
                ZonedDateTime.of(nextPrayerDate, time, timezone)
            }
        }

        return PrayerCountdown(
            activePrayer = activePrayer,
            nextPrayer = nextPrayerTime?.let { nextPrayer },
            nextPrayerAt = nextPrayerTime,
            remaining = nextPrayerTime?.let { Duration.between(localNow.toInstant(), it.toInstant()) }
        )
    }
}
