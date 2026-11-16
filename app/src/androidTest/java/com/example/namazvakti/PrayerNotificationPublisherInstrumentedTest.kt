package com.example.namazvakti

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.notification.PrayerNotificationPublisher
import com.example.namazvakti.notification.PrayerNotificationVibration
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerNotificationPublisherInstrumentedTest {
    @Test
    fun createsHighImportanceVibratingPrayerChannelsAndRemovesLegacyChannels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                "prayer_time_entered",
                "Vakit girdi",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "prayer_time_reminder",
                "Vakit hatırlatması",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        PrayerNotificationPublisher.createChannels(context)

        val atTime = manager.getNotificationChannel(PrayerNotificationPublisher.CHANNEL_AT_TIME)
        val reminder = manager.getNotificationChannel(PrayerNotificationPublisher.CHANNEL_REMINDER)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, atTime?.importance)
        assertTrue(atTime?.shouldVibrate() == true)
        assertTrue(atTime?.vibrationPattern?.contentEquals(PrayerNotificationVibration.AT_TIME) == true)
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, atTime?.sound)
        assertEquals(AudioAttributes.USAGE_NOTIFICATION_EVENT, atTime?.audioAttributes?.usage)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, reminder?.importance)
        assertTrue(reminder?.shouldVibrate() == true)
        assertTrue(reminder?.vibrationPattern?.contentEquals(PrayerNotificationVibration.REMINDER) == true)
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, reminder?.sound)
        assertEquals(AudioAttributes.USAGE_NOTIFICATION_EVENT, reminder?.audioAttributes?.usage)
        assertEquals(null, manager.getNotificationChannel("prayer_time_entered"))
        assertEquals(null, manager.getNotificationChannel("prayer_time_reminder"))
    }

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
            assertEquals(
                PrayerNotificationPublisher.CHANNEL_AT_TIME,
                activeNotifications.first { it.id == notificationId }.notification.channelId
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
        val events = DiyanetReligiousDays.on(java.time.LocalDate.of(2026, 12, 10))
        val notificationsEnabled = try {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: NoSuchMethodError) {
            manager.areNotificationsEnabled()
        }
        assumeTrue("notifications disabled on device", notificationsEnabled)

        manager.cancel(notificationId)
        try {
            PrayerNotificationPublisher.show(context, events)
            val deadline = SystemClock.elapsedRealtime() + NOTIFICATION_TIMEOUT_MILLIS
            var notification = manager.activeNotifications.firstOrNull { it.id == notificationId }
            while (notification == null && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(NOTIFICATION_POLL_INTERVAL_MILLIS)
                notification = manager.activeNotifications.firstOrNull { it.id == notificationId }
            }
            assertTrue("Timed out waiting for a religious-day notification", notification != null)
            assertTrue(
                notification!!.notification.extras.getCharSequence("android.title").toString()
                    .contains("Bugün Üç Ayların Başlangıcı")
            )
            assertTrue(
                notification.notification.extras.getCharSequence("android.text").toString()
                    .contains("Bu akşam Regaip Kandili")
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
