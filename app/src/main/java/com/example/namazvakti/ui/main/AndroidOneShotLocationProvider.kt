package com.example.namazvakti.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidOneShotLocationProvider(context: Context) : OneShotLocationProvider {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override suspend fun currentLocation(): OneShotLocationResult {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return OneShotLocationResult.PermissionDenied
        }

        val manager = locationManager ?: return OneShotLocationResult.ServicesDisabled
        return try {
            val enabledProviders = PROVIDER_PRIORITY.filter { provider ->
                runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            }
            if (enabledProviders.isEmpty()) return OneShotLocationResult.ServicesDisabled

            val location = withTimeoutOrNull(TIMEOUT_MS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    enabledProviders.firstNotNullOfOrNull { provider ->
                        awaitCurrentLocation(manager, provider)
                    }
                } else {
                    enabledProviders.firstNotNullOfOrNull { provider ->
                        freshLastKnownLocation(manager, provider)
                    } ?: enabledProviders.firstNotNullOfOrNull { provider ->
                        awaitSingleUpdate(manager, provider)
                    }
                }
            } ?: return OneShotLocationResult.TimedOut

            OneShotLocationResult.Found(Coordinates(location.latitude, location.longitude))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            OneShotLocationResult.PermissionDenied
        } catch (_: IllegalArgumentException) {
            OneShotLocationResult.TimedOut
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                manager.getCurrentLocation(provider, cancellationSignal, appContext.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (_: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    @SuppressLint("MissingPermission")
    private fun freshLastKnownLocation(manager: LocationManager, provider: String): Location? {
        val location = manager.getLastKnownLocation(provider) ?: return null
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return location.takeIf { ageNanos in 0..MAX_LAST_KNOWN_AGE_NANOS }
    }

    @Suppress("DEPRECATION")
    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleUpdate(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location)
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val MAX_LAST_KNOWN_AGE_NANOS = 120_000_000_000L
        val PROVIDER_PRIORITY = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    }
}
