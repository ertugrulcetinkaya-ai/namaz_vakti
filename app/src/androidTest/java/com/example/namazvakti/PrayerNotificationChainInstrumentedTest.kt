package com.example.namazvakti

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.namazvakti.app.NamazVaktiApp
import com.example.namazvakti.app.appContainer
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.PrayerNotificationType
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.policy.PrayerNotificationEvent
import com.example.namazvakti.domain.policy.PrayerNotificationEventId
import com.example.namazvakti.domain.policy.PrayerNotificationPlanner
import com.example.namazvakti.notification.PrayerNotificationAlarm
import com.example.namazvakti.notification.PrayerNotificationReceiver
import com.example.namazvakti.notification.PrayerNotificationScheduler
import com.example.namazvakti.notification.PrayerNotificationPublisher
import com.example.namazvakti.widget.alarm.PrayerExactAlarmAccess
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerNotificationChainInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container by lazy { context.appContainer() }
    private var originalTimeZone: TimeZone? = null
    private var originalLocation: PrayerLocation? = null
    private var originalSettings: PrayerNotificationSettings? = null

    @Before
    fun setUp() = runBlocking {
        originalTimeZone = TimeZone.getDefault()
        originalLocation = container.store.readLocation()
        originalSettings = container.notificationSettingsStore.read()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        PrayerNotificationPublisher.createChannels(context)
    }

    @After
    fun tearDown() = runBlocking {
        PrayerNotificationAlarm.cancel(context)
        PrayerWidgetScheduler.cancelAll(context)
        originalLocation?.let { container.store.saveLocation(it) }
        originalSettings?.let { container.notificationSettingsStore.save(it) }
        originalTimeZone?.let { TimeZone.setDefault(it) }
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @Test
    fun schedulerUsesLocationZoneInstantWhenDeviceZoneDiffers() = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val location = istanbulLocation()
        val today = container.timeProvider.today(location.timezone)
        val todayCache = cache(today, location)
        val tomorrowCache = cache(today.plusDays(1), location)
        val settings = PrayerNotificationSettings(
            notificationsEnabled = true,
            enabledPrayers = setOf(PrayerNotificationPrayer.DHUHR)
        )
        container.store.saveLocation(location)
        container.store.saveCaches(listOf(todayCache, tomorrowCache))
        container.notificationSettingsStore.save(settings)
        val exact = PrayerExactAlarmAccess.canScheduleExactAlarms(context)
        val planningTime = container.timeProvider.now().withZoneSameInstant(location.timezone)
        val expected = requireNotNull(
            PrayerNotificationPlanner.nextEvent(
                todayCache,
                tomorrowCache,
                settings,
                planningTime,
                exact = exact
            )
        )

        var scheduledIntent: Intent? = null
        PrayerNotificationScheduler.scheduleNext(context, attemptedEvent = null) { alarmContext, event, isExact ->
            PrayerNotificationAlarm.schedule(alarmContext, event, isExact).also {
                scheduledIntent = it
            }
        }
        val actualSchedule = requireNotNull(scheduledIntent)

        assertEquals(expected.triggerAt.toInstant().toEpochMilli(), actualSchedule.getLongExtra(
            PrayerNotificationAlarm.EXTRA_TRIGGER_AT,
            Long.MIN_VALUE
        ))
        assertEquals(expected.prayerTime.toInstant().toEpochMilli(), actualSchedule.getLongExtra(
            PrayerNotificationAlarm.EXTRA_PRAYER_TIME,
            Long.MIN_VALUE
        ))
        assertEquals(exact, actualSchedule.getBooleanExtra(PrayerNotificationAlarm.EXTRA_EXACT, !exact))
        assertEquals(ZoneId.of("Europe/Istanbul"), expected.prayerTime.zone)
    }

    @Test
    fun receiverDropsPreviouslyScheduledAlarmAfterLocationChanges() = runBlocking {
        val oldLocation = istanbulLocation()
        val newLocation = PrayerLocation("Ankara", "Turkey", "ANKARA")
        val tomorrow = container.timeProvider.today(oldLocation.timezone).plusDays(1)
        container.store.saveLocation(oldLocation)
        container.store.saveCaches(
            listOf(cache(tomorrow, oldLocation), cache(tomorrow, newLocation))
        )
        container.notificationSettingsStore.save(
            PrayerNotificationSettings(
                notificationsEnabled = true,
                enabledPrayers = setOf(PrayerNotificationPrayer.DHUHR)
            )
        )
        val oldEvent = notificationEvent(oldLocation, tomorrow, PrayerNotificationPrayer.DHUHR)
        val eventId = oldEvent.id
        container.store.saveLocation(newLocation)

        PrayerNotificationReceiver().handle(context, alarmIntent(oldEvent, exact = true))

        assertFalse(eventId in container.notificationSettingsStore.readShownEvents())
        val notificationId = NOTIFICATION_ID_BASE + oldEvent.prayer.ordinal
        assertTrue(
            "A stale-location event must not publish notification id=$notificationId",
            context.getSystemService(NotificationManager::class.java)
                .activeNotifications.none { it.id == notificationId }
        )
    }

    @Test
    fun matchingReceiverDeliveryPublishesAndReplansThroughScheduler() = runBlocking {
        val location = istanbulLocation()
        val today = container.timeProvider.today(location.timezone)
        val now = container.timeProvider.now().withZoneSameInstant(location.timezone)
        val prayerTimesAndPrayer = prayerTimesAround(now.toLocalTime())
        val todayCache = cache(today, location, prayerTimesAndPrayer.first)
        val tomorrowCache = cache(today.plusDays(1), location)
        val prayer = prayerTimesAndPrayer.second
        val settings = PrayerNotificationSettings(
            notificationsEnabled = true,
            enabledPrayers = setOf(prayer)
        )
        val prayerTime = today.atTime(todayCache.prayerTimes.timeAt(prayer.timeName)).atZone(location.timezone)
        val event = PrayerNotificationEvent(
            prayer = prayer,
            type = PrayerNotificationType.AT_TIME,
            triggerAt = prayerTime,
            prayerTime = prayerTime,
            displayCity = location.displayName,
            locationCity = location.cacheKey,
            locationCountry = location.country,
            leadMinutes = 0
        )
        container.store.saveLocation(location)
        container.store.saveCaches(listOf(todayCache, tomorrowCache))
        container.notificationSettingsStore.save(settings)
        val notificationId = NOTIFICATION_ID_BASE + prayer.ordinal
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
        scheduledAlarmToken()?.cancel()

        PrayerNotificationReceiver().handle(context, alarmIntent(event, exact = true))

        assertTrue(event.id in container.notificationSettingsStore.readShownEvents())
        val notification = context.getSystemService(NotificationManager::class.java)
            .activeNotifications.firstOrNull { it.id == notificationId }
        assertNotNull("Receiver delivery should publish through PrayerNotificationPublisher", notification)
        assertNotNull("Receiver should replan through PrayerNotificationScheduler", scheduledAlarmToken())
    }

    @Test
    fun exactExtraReflectsAllowedAndDeniedAlarmScheduling() {
        val location = istanbulLocation()
        val event = notificationEvent(
            location,
            container.timeProvider.today(location.timezone).plusDays(1),
            PrayerNotificationPrayer.DHUHR
        )
        val permissionAvailable = PrayerExactAlarmAccess.canScheduleExactAlarms(context)
        val permissionStateIntent = PrayerNotificationAlarm.schedule(
            context,
            event,
            exact = permissionAvailable,
            setAlarm = { _, _, _, requestedExact -> requestedExact }
        )
        assertEquals(
            permissionAvailable,
            permissionStateIntent.getBooleanExtra(PrayerNotificationAlarm.EXTRA_EXACT, !permissionAvailable)
        )

        val allowedIntent = PrayerNotificationAlarm.schedule(
            context,
            event,
            exact = true,
            setAlarm = { _, _, _, requestedExact ->
                assertTrue(requestedExact)
                true
            }
        )
        assertTrue(allowedIntent.getBooleanExtra(PrayerNotificationAlarm.EXTRA_EXACT, false))

        val fallbackIntent = PrayerNotificationAlarm.schedule(
            context,
            event,
            exact = true,
            setAlarm = { _, _, _, requestedExact ->
                assertTrue(requestedExact)
                false
            }
        )
        assertFalse(fallbackIntent.getBooleanExtra(PrayerNotificationAlarm.EXTRA_EXACT, true))
        PrayerNotificationAlarm.cancel(context)
    }

    private fun scheduledAlarmToken(): PendingIntent? = PendingIntent.getBroadcast(
        context,
        PrayerNotificationAlarm.REQUEST_CODE,
        Intent(context, PrayerNotificationReceiver::class.java)
            .setAction(PrayerNotificationAlarm.ACTION)
            .setData(Uri.parse("prayer-notification://next")),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )

    private fun istanbulLocation() = PrayerLocation(
        city = "Istanbul",
        country = "Turkey",
        displayCity = "İSTANBUL",
        timezone = ZoneId.of("Europe/Istanbul"),
        latitude = 41.0082,
        longitude = 28.9784,
        diyanetDistrictId = "9541",
        districtName = "İSTANBUL"
    )

    private fun cache(
        date: LocalDate,
        location: PrayerLocation,
        prayerTimes: PrayerTimes = DEFAULT_PRAYER_TIMES
    ) = CachedPrayerDay(
        date = date,
        location = location,
        timezone = location.timezone,
        settings = PrayerCalculationSettings(),
        prayerTimes = prayerTimes,
        hijriText = "10 Rebiülevvel 1448",
        fetchedAt = container.timeProvider.now().toInstant(),
        source = PrayerDataSource.ALADHAN
    )

    private fun notificationEvent(
        location: PrayerLocation,
        date: LocalDate,
        prayer: PrayerNotificationPrayer
    ): PrayerNotificationEvent {
        val prayerTime = date.atTime(DEFAULT_PRAYER_TIMES.timeAt(prayer.timeName)).atZone(location.timezone)
        return PrayerNotificationEvent(
            prayer = prayer,
            type = PrayerNotificationType.AT_TIME,
            triggerAt = prayerTime,
            prayerTime = prayerTime,
            displayCity = location.displayName,
            locationCity = location.cacheKey,
            locationCountry = location.country,
            leadMinutes = 0
        )
    }

    private fun alarmIntent(event: PrayerNotificationEvent, exact: Boolean) =
        Intent(context, PrayerNotificationReceiver::class.java)
            .setAction(PrayerNotificationAlarm.ACTION)
            .putExtra(PrayerNotificationAlarm.EXTRA_PRAYER, event.prayer.name)
            .putExtra(PrayerNotificationAlarm.EXTRA_TYPE, event.type.name)
            .putExtra(PrayerNotificationAlarm.EXTRA_TRIGGER_AT, event.triggerAt.toInstant().toEpochMilli())
            .putExtra(PrayerNotificationAlarm.EXTRA_PRAYER_TIME, event.prayerTime.toInstant().toEpochMilli())
            .putExtra(PrayerNotificationAlarm.EXTRA_PRAYER_DATE, event.prayerTime.toLocalDate().toString())
            .putExtra(PrayerNotificationAlarm.EXTRA_LOCATION_CITY, event.locationCity)
            .putExtra(PrayerNotificationAlarm.EXTRA_LOCATION_COUNTRY, event.locationCountry)
            .putExtra(PrayerNotificationAlarm.EXTRA_LEAD_MINUTES, event.leadMinutes)
            .putExtra(PrayerNotificationAlarm.EXTRA_EXACT, exact)
            .putExtra(PrayerNotificationAlarm.EXTRA_REPLAN_ONLY, false)

    private fun prayerTimesAround(now: LocalTime): Pair<PrayerTimes, PrayerNotificationPrayer> {
        val target = now.withSecond(0).withNano(0)
        return when (target.hour) {
            in 0..3 -> PrayerTimes(
                target,
                target.plusHours(1),
                target.plusHours(7),
                target.plusHours(11),
                target.plusHours(14),
                target.plusHours(16)
            ) to PrayerNotificationPrayer.IMSAK
            in 4..6 -> PrayerTimes(
                target.minusHours(1),
                target,
                target.plusHours(6),
                target.plusHours(10),
                target.plusHours(13),
                target.plusHours(15)
            ) to PrayerNotificationPrayer.SUNRISE
            in 7..14 -> PrayerTimes(
                target.minusHours(6),
                target.minusHours(4),
                target,
                target.plusHours(4),
                target.plusHours(7),
                target.plusHours(9)
            ) to PrayerNotificationPrayer.DHUHR
            in 15..18 -> PrayerTimes(
                target.minusHours(12),
                target.minusHours(10),
                target.minusHours(5),
                target,
                target.plusHours(3),
                target.plusHours(5)
            ) to PrayerNotificationPrayer.ASR
            in 19..20 -> PrayerTimes(
                target.minusHours(14),
                target.minusHours(12),
                target.minusHours(7),
                target.minusHours(3),
                target,
                target.plusHours(2)
            ) to PrayerNotificationPrayer.MAGHRIB
            else -> PrayerTimes(
                target.minusHours(17),
                target.minusHours(15),
                target.minusHours(9),
                target.minusHours(5),
                target.minusHours(2),
                target
            ) to PrayerNotificationPrayer.ISHA
        }
    }

    private val PrayerNotificationPrayer.timeName
        get() = when (this) {
            PrayerNotificationPrayer.IMSAK -> com.example.namazvakti.domain.model.PrayerTimeName.IMSAK
            PrayerNotificationPrayer.SUNRISE -> com.example.namazvakti.domain.model.PrayerTimeName.SUNRISE
            PrayerNotificationPrayer.DHUHR -> com.example.namazvakti.domain.model.PrayerTimeName.DHUHR
            PrayerNotificationPrayer.ASR -> com.example.namazvakti.domain.model.PrayerTimeName.ASR
            PrayerNotificationPrayer.MAGHRIB -> com.example.namazvakti.domain.model.PrayerTimeName.MAGHRIB
            PrayerNotificationPrayer.ISHA -> com.example.namazvakti.domain.model.PrayerTimeName.ISHA
        }

    private companion object {
        const val NOTIFICATION_ID_BASE = 6200
        val DEFAULT_PRAYER_TIMES = PrayerTimes(
            LocalTime.of(4, 30), LocalTime.of(6, 0), LocalTime.of(12, 30),
            LocalTime.of(16, 0), LocalTime.of(19, 0), LocalTime.of(21, 0)
        )
    }
}
