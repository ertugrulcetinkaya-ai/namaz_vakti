package com.example.namazvakti

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.notification.PrayerNotificationPublisher
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
        val notificationsEnabled = try {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: NoSuchMethodError) {
            // AndroidX core is loaded from the R8-minified target APK in this test variant.
            manager.areNotificationsEnabled()
        }
        assumeTrue("notifications disabled on device", notificationsEnabled)

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

            val deadline = SystemClock.elapsedRealtime() + NOTIFICATION_TIMEOUT_MILLIS
            var activeNotifications = manager.activeNotifications
            while (activeNotifications.none { it.id == notificationId } &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(NOTIFICATION_POLL_INTERVAL_MILLIS)
                activeNotifications = manager.activeNotifications
            }
            assertTrue(
                "Timed out waiting for notification id=$notificationId; activeNotifications=" +
                    activeNotifications.joinToString { notification ->
                        "id=${notification.id}, key=${notification.key}, " +
                            "package=${notification.packageName}, " +
                            "title=${notification.notification.extras.getCharSequence("android.title")}, " +
                            "text=${notification.notification.extras.getCharSequence("android.text")}"
                    },
                activeNotifications.any { it.id == notificationId }
            )
        } finally {
            manager.cancel(notificationId)
        }
    }


    @Test
    fun postsReligiousDayNotificationOnItsOwnDefaultImportanceChannel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationId = RELIGIOUS_DAY_NOTIFICATION_ID
        val event = DiyanetReligiousDays.on(java.time.LocalDate.of(2026, 8, 24)).first { it.isNight }
        val notificationsEnabled = try {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: NoSuchMethodError) {
            manager.areNotificationsEnabled()
        }
        assumeTrue("notifications disabled on device", notificationsEnabled)

        manager.cancel(notificationId)
        try {
            PrayerNotificationPublisher.show(context, event)
            val deadline = SystemClock.elapsedRealtime() + NOTIFICATION_TIMEOUT_MILLIS
            var notification = manager.activeNotifications.firstOrNull { it.id == notificationId }
            while (notification == null && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(NOTIFICATION_POLL_INTERVAL_MILLIS)
                notification = manager.activeNotifications.firstOrNull { it.id == notificationId }
            }
            assertTrue("Timed out waiting for a religious-day notification", notification != null)
            assertTrue(
                notification!!.notification.extras.getCharSequence("android.title").toString()
                    .contains("Bu akşam Mevlid Kandili")
            )
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel("religious_days")?.importance)
        } finally {
            manager.cancel(notificationId)
        }
    }

    private companion object {
        const val RELIGIOUS_DAY_NOTIFICATION_ID = 6304
        const val NOTIFICATION_TIMEOUT_MILLIS = 3_000L
        const val NOTIFICATION_POLL_INTERVAL_MILLIS = 100L
    }
}
