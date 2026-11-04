package com.example.namazvakti.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.ui.main.MainActivity
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object PrayerNotificationPublisher {
    private const val CHANNEL_AT_TIME = "prayer_time_entered"
    private const val CHANNEL_REMINDER = "prayer_time_reminder"
    private const val NOTIFICATION_ID_BASE = 6200
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun show(
        context: Context,
        prayer: PrayerNotificationPrayer,
        type: PrayerNotificationType,
        leadMinutes: Int,
        prayerTime: ZonedDateTime,
        displayCity: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        createChannels(context)
        val channelId = if (type == PrayerNotificationType.AT_TIME) CHANNEL_AT_TIME else CHANNEL_REMINDER
        val title = notificationTitle(context, prayer, type, leadMinutes)
        val content = context.getString(
            R.string.notification_content,
            prayerTime.format(timeFormatter),
            displayCity.uppercase(Locale.forLanguageTag("tr-TR"))
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_prayer)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_BASE + prayer.ordinal,
            notification
        )
    }

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AT_TIME,
                context.getString(R.string.notification_channel_at_time_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_at_time_description)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.notification_channel_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_reminder_description)
            }
        )
    }

    private fun notificationTitle(
        context: Context,
        prayer: PrayerNotificationPrayer,
        type: PrayerNotificationType,
        leadMinutes: Int
    ): String {
        if (type == PrayerNotificationType.AT_TIME) {
            return if (prayer == PrayerNotificationPrayer.SUNRISE) {
                context.getString(R.string.notification_sunrise_at_time)
            } else {
                context.getString(R.string.notification_at_time_title, prayer.displayName(context))
            }
        }
        return if (prayer == PrayerNotificationPrayer.SUNRISE) {
            context.getString(R.string.notification_sunrise_reminder, leadMinutes)
        } else {
            context.getString(
                R.string.notification_reminder_title,
                prayer.reminderName(context),
                leadMinutes
            )
        }
    }

    private fun PrayerNotificationPrayer.displayName(context: Context): String = when (this) {
        PrayerNotificationPrayer.IMSAK -> context.getString(R.string.prayer_imsak)
        PrayerNotificationPrayer.SUNRISE -> context.getString(R.string.prayer_sunrise)
        PrayerNotificationPrayer.DHUHR -> context.getString(R.string.prayer_dhuhr)
        PrayerNotificationPrayer.ASR -> context.getString(R.string.prayer_asr)
        PrayerNotificationPrayer.MAGHRIB -> context.getString(R.string.prayer_maghrib)
        PrayerNotificationPrayer.ISHA -> context.getString(R.string.prayer_isha)
    }

    private fun PrayerNotificationPrayer.reminderName(context: Context): String = when (this) {
        PrayerNotificationPrayer.IMSAK -> context.getString(R.string.prayer_imsak_reminder)
        PrayerNotificationPrayer.SUNRISE -> context.getString(R.string.prayer_sunrise)
        PrayerNotificationPrayer.DHUHR -> context.getString(R.string.prayer_dhuhr_reminder)
        PrayerNotificationPrayer.ASR -> context.getString(R.string.prayer_asr_reminder)
        PrayerNotificationPrayer.MAGHRIB -> context.getString(R.string.prayer_maghrib_reminder)
        PrayerNotificationPrayer.ISHA -> context.getString(R.string.prayer_isha_reminder)
    }
}
