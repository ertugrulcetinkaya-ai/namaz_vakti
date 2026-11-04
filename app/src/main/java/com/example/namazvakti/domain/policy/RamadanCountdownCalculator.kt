package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.RamadanRange
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class RamadanCountdownKind { IFTAR, SAHUR }

data class RamadanCountdown(
    val kind: RamadanCountdownKind,
    val dayNumber: Int,
    val targetAt: ZonedDateTime,
    val remaining: Duration
)

object RamadanCountdownCalculator {
    fun calculate(
        date: LocalDate,
        range: RamadanRange,
        today: PrayerTimes,
        tomorrow: PrayerTimes?,
        timezone: ZoneId,
        now: ZonedDateTime
    ): RamadanCountdown? {
        val dayNumber = range.dayNumber(date) ?: return null
        val localNow = now.withZoneSameInstant(timezone)
        if (localNow.toLocalDate() != date) return null

        val localTime = localNow.toLocalTime()
        val target = when {
            localTime < today.fajr -> {
                RamadanCountdownKind.SAHUR to date.atTime(today.fajr).atZone(timezone)
            }
            localTime < today.maghrib -> {
                RamadanCountdownKind.IFTAR to date.atTime(today.maghrib).atZone(timezone)
            }
            else -> {
                val tomorrowDate = date.plusDays(1)
                if (range.dayNumber(tomorrowDate) == null || tomorrow == null) return null
                RamadanCountdownKind.SAHUR to tomorrowDate.atTime(tomorrow.fajr).atZone(timezone)
            }
        }

        return RamadanCountdown(
            kind = target.first,
            dayNumber = dayNumber,
            targetAt = target.second,
            remaining = Duration.between(localNow.toInstant(), target.second.toInstant())
                .coerceAtLeast(Duration.ZERO)
        )
    }
}
