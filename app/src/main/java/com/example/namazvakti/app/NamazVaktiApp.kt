package com.example.namazvakti.app

import android.app.Application
import com.example.namazvakti.data.local.PrayerTimesDatabase
import com.example.namazvakti.data.local.PrayerNotificationSettingsStore
import com.example.namazvakti.data.local.PrayerTimeOffsetsStore
import com.example.namazvakti.data.local.PrayerTimesStore
import com.example.namazvakti.data.local.DataStoreDiyanetSourceStateStore
import com.example.namazvakti.data.remote.DiyanetMirrorDataSource
import com.example.namazvakti.data.remote.DiyanetPrayerTimesApi
import com.example.namazvakti.data.remote.FallbackDiyanetDataSource
import com.example.namazvakti.data.remote.createPrayerHttpClient
import com.example.namazvakti.data.remote.PrayerTimesApi
import com.example.namazvakti.data.repository.PrayerTimesRepository
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.policy.PrayerCachePolicy
import com.example.namazvakti.domain.policy.PrayerCacheRetentionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class NamazVaktiApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    val notificationSettingsStore = PrayerNotificationSettingsStore(application)
    val prayerTimeOffsetsStore = PrayerTimeOffsetsStore(application)
    val httpClient: OkHttpClient = createPrayerHttpClient()
    val api = PrayerTimesApi(client = httpClient)
    val diyanetSourceStateStore = DataStoreDiyanetSourceStateStore(application)
    val diyanetApi = FallbackDiyanetDataSource(
        mirror = DiyanetMirrorDataSource(
            diyanetSourceStateStore,
            client = httpClient,
            timeProvider = timeProvider
        ),
        mtopal = DiyanetPrayerTimesApi(client = httpClient, timeProvider = timeProvider),
        stateStore = diyanetSourceStateStore
    )
    val repository = PrayerTimesRepository(api, store, settings, timeProvider, diyanetApi = diyanetApi)
}

fun android.content.Context.appContainer(): AppContainer =
    (applicationContext as NamazVaktiApp).container
