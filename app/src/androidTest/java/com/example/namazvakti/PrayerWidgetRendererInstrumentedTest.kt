package com.example.namazvakti

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class PrayerWidgetRendererInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val renderer = PrayerWidgetRenderer()
    private val location = PrayerLocation("Istanbul", "Turkey", "İSTANBUL")
    private val now = ZonedDateTime.of(2026, 8, 6, 14, 0, 0, 0, ZONE)

    @Test
    fun freshCacheRendersCityPrayerTimesAndActivePrayer() {
        val root = render(cache(), isStale = false)

        assertEquals("İSTANBUL • 22 Safer 1448", root.text(R.id.prayer_widget_city))
        assertEquals("▶ Öğle 13:08", root.text(R.id.prayer_widget_item_3))
        assertFalse(root.text(R.id.prayer_widget_city).contains("Eski veri"))
        assertTrue(root.contentDescription.contains("Vakitler güncellendi"))
    }

    @Test
    fun staleCacheIsVisibleInTitleAndAccessibilityDescription() {
        val root = render(cache(), isStale = true)

        assertTrue(root.text(R.id.prayer_widget_city).endsWith("Eski veri"))
        assertTrue(root.contentDescription.contains("Eski veri gösteriliyor"))
    }

    @Test
    fun missingCacheRendersUnavailableState() {
        val root = render(cache = null, isStale = false)

        assertEquals("İSTANBUL", root.text(R.id.prayer_widget_city))
        assertEquals("Vakitler alınamadı", root.text(R.id.prayer_widget_item_1))
        assertEquals("", root.text(R.id.prayer_widget_item_2))
        assertTrue(root.contentDescription.contains("Vakitler alınamadı"))
    }

    @Test
    fun remoteViewsInflatesMeasuresAndDrawsToBitmap() {
        val root = render(cache(), isStale = false)
        val width = 900
        val height = 320
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
        assertTrue(bitmapHasVisiblePixels(bitmap))
    }

    private fun render(cache: CachedPrayerDay?, isStale: Boolean): View =
        renderer.render(context, cache, location, now, isStale)
            .apply(context, FrameLayout(context))

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun cache() = CachedPrayerDay(
        date = LocalDate.of(2026, 8, 6),
        location = location,
        timezone = ZONE,
        settings = PrayerCalculationSettings(),
        prayerTimes = PrayerTimes(
            fajr = LocalTime.of(4, 12),
            sunrise = LocalTime.of(5, 49),
            dhuhr = LocalTime.of(13, 8),
            asr = LocalTime.of(17, 2),
            maghrib = LocalTime.of(20, 21),
            isha = LocalTime.of(22, 1)
        ),
        hijriText = "22 Safer 1448",
        fetchedAt = Instant.parse("2026-08-06T10:00:00Z")
    )

    private fun bitmapHasVisiblePixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { pixel -> pixel ushr 24 != 0 }
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
    }
}
