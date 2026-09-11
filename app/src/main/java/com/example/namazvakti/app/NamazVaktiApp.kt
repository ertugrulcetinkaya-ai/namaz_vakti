package com.example.namazvakti.app

import android.app.Application
import com.example.namazvakti.data.local.PrayerTimesDatabase
import com.example.namazvakti.data.local.PrayerTimesStore
import com.example.namazvakti.data.remote.PrayerTimesApi
import com.example.namazvakti.data.repository.PrayerTimesRepository
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.policy.PrayerCachePolicy
import com.example.namazvakti.domain.policy.PrayerCacheRetentionPolicy

class NamazVaktiApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val settings = PrayerCalculationSettings()
    val timeProvider = PrayerTimeProvider()
    val cachePolicy = PrayerCachePolicy()
    val cacheRetentionPolicy = PrayerCacheRetentionPolicy()
    val database = PrayerTimesDatabase.create(application)
    val store = PrayerTimesStore(
        application,
        database.prayerDayDao(),
        timeProvider,
        settings,
        cacheRetentionPolicy
    )
    val api = PrayerTimesApi()
    val repository = PrayerTimesRepository(api, store, settings, timeProvider)
}

fun android.content.Context.appContainer(): AppContainer =
    (applicationContext as NamazVaktiApp).container
