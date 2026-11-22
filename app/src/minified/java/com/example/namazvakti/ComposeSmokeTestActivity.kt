package com.example.namazvakti

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.namazvakti.ui.main.NamazVaktiTheme
import com.example.namazvakti.ui.main.PrayerVaktiApp
import com.example.namazvakti.ui.main.PrayerViewModel

/** Test-only Compose host included in the minified instrumentation target. */
class ComposeSmokeTestActivity : ComponentActivity() {
    fun show(viewModel: PrayerViewModel) {
        setContent {
            NamazVaktiTheme { PrayerVaktiApp(viewModel) }
        }
    }
}
