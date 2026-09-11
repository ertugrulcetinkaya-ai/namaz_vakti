package com.example.namazvakti

import com.example.namazvakti.app.*
import com.example.namazvakti.data.local.*
import com.example.namazvakti.data.remote.*
import com.example.namazvakti.data.repository.*
import com.example.namazvakti.domain.model.*
import com.example.namazvakti.domain.policy.*
import com.example.namazvakti.domain.port.*
import com.example.namazvakti.ui.main.*
import com.example.namazvakti.widget.*
import com.example.namazvakti.widget.alarm.*
import com.example.namazvakti.widget.renderer.*
import com.example.namazvakti.widget.worker.*
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerWidgetProviderManifestInstrumentedTest {
    @Test
    fun widgetProviderIsNotExported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, PrayerWidgetProvider::class.java),
            0
        )

        assertFalse(receiverInfo.exported)
    }

    @Test
    fun applicationBackupIsDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }
}
