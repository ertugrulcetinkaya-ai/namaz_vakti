package com.example.namazvakti.widget

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerTimeName
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.policy.PrayerCountdownCalculator
import com.example.namazvakti.widget.renderer.PrayerWidgetDataState
import java.time.Duration
import java.time.ZonedDateTime

data class PrayerWidgetCountdown(
    val prayer: PrayerTimeName,
    val targetAt: ZonedDateTime,
    val chronometerBase: Long
) {
    val timeText: String get() = targetAt.format(PrayerTimes.TIME_FORMATTER)
}

/** Pure elapsed-realtime base calculation used by Android's native Chronometer view. */
object PrayerWidgetChronometerBase {
    fun calculate(
        now: ZonedDateTime,
        target: ZonedDateTime?,
        elapsedRealtime: Long,
        stale: Boolean = false
    ): Long? {
        if (stale || target == null) return null
        val remainingMillis = Duration.between(now.toInstant(), target.toInstant()).toMillis()
        if (remainingMillis <= 0L) return null
        return elapsedRealtime + remainingMillis
    }
}

object PrayerWidgetCountdownCalculator {
    fun calculate(
        cache: CachedPrayerDay?,
        tomorrowCache: CachedPrayerDay?,
        now: ZonedDateTime,
        elapsedRealtime: Long,
        dataState: PrayerWidgetDataState
    ): PrayerWidgetCountdown? {
        if (dataState != PrayerWidgetDataState.Fresh || cache == null) return null
        val localNow = now.withZoneSameInstant(cache.timezone)
        if (cache.date != localNow.toLocalDate()) return null

        val tomorrow = tomorrowCache?.takeIf {
            it.matches(cache.date.plusDays(1), cache.location, cache.settings) &&
                it.timezone == cache.timezone
        }
        val calculated = PrayerCountdownCalculator.calculate(
            today = cache.prayerTimes,
            date = cache.date,
            timezone = cache.timezone,
            tomorrow = tomorrow?.prayerTimes,
            now = localNow
        )
        val prayer = calculated.nextPrayer ?: return null
        val targetAt = calculated.nextPrayerAt ?: return null
        val base = PrayerWidgetChronometerBase.calculate(localNow, targetAt, elapsedRealtime)
            ?: return null
        return PrayerWidgetCountdown(prayer, targetAt, base)
    }
}
