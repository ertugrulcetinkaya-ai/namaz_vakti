package com.example.namazvakti.ui.main

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.namazvakti.notification.PrayerNotificationScheduler
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NamazVaktiTheme {
                PrayerVaktiApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(applicationContext)
                PrayerNotificationScheduler.scheduleNext(applicationContext)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "precise widget alarm setup failed", exception)
            }
        }
    }

    private companion object {
        const val TAG = "NamazWidget"
    }
}
