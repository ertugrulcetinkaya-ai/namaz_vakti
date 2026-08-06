package com.example.namazvakti

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import java.time.Duration
import java.util.concurrent.TimeUnit

class PrayerWorkRequestFactory {
    fun immediateRefresh(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<PrayerWidgetWorker>()
        .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to true))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setConstraints(networkConstraints())
        .build()

    fun dailySafetyRefresh(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<PrayerWidgetWorker>(
        DAILY_INTERVAL_HOURS, TimeUnit.HOURS
    )
        .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to true))
        .setConstraints(networkConstraints())
        .build()

    fun boundaryRerender(delay: Duration): OneTimeWorkRequest = OneTimeWorkRequestBuilder<PrayerWidgetWorker>()
        .setInputData(workDataOf(PrayerWidgetWorker.INPUT_FETCH to false))
        .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
        .build()

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val DAILY_INTERVAL_HOURS = 24L
    }
}
