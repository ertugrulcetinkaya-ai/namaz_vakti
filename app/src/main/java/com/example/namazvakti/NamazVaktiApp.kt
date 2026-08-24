package com.example.namazvakti

import android.app.Application

class NamazVaktiApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val settings = PrayerCalculationSettings()
    val timeProvider = PrayerTimeProvider()
    val cachePolicy = PrayerCachePolicy()
    val database = PrayerTimesDatabase.create(application)
    val store = PrayerTimesStore(application, database.prayerDayDao(), timeProvider, settings)
    val api = PrayerTimesApi()
    val repository = PrayerTimesRepository(api, store, settings, timeProvider)
}

fun android.content.Context.appContainer(): AppContainer =
    (applicationContext as NamazVaktiApp).container
