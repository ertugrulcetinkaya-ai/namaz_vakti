package com.example.namazvakti

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.notification.PrayerNotificationPublisher
import java.time.ZonedDateTime
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerNotificationPublisherInstrumentedTest {
    @Test
    fun postsNotificationWhenPermissionIsGranted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        val prayer = PrayerNotificationPrayer.MAGHRIB
        val notificationId = 6200 + prayer.ordinal
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId)
        try {
            PrayerNotificationPublisher.show(
                context,
                prayer = prayer,
                type = PrayerNotificationType.AT_TIME,
                leadMinutes = 0,
                prayerTime = ZonedDateTime.now().plusMinutes(1),
                displayCity = "İSTANBUL"
            )

            assertTrue(manager.activeNotifications.any { it.id == notificationId })
        } finally {
            manager.cancel(notificationId)
        }
    }
}
