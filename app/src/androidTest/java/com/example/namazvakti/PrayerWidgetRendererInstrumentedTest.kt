package com.example.namazvakti

import android.content.Context
import android.content.ContentUris
import android.content.ContentValues
import android.content.res.Configuration
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DiyanetRamadanCalendar
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.widget.PrayerWidgetSizeClass
import com.example.namazvakti.widget.renderer.PrayerWidgetDataState
import com.example.namazvakti.widget.renderer.PrayerWidgetRenderer
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerWidgetRendererInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val renderer = PrayerWidgetRenderer()
    private val location = PrayerLocation("Istanbul", "Turkey", "İSTANBUL", ZONE)
    private val now = ZonedDateTime.of(2026, 8, 6, 14, 0, 0, 0, ZONE)

    @Test
    fun freshStaleAndUnavailableRenderForEachWidgetSize() {
        for (size in PrayerWidgetSizeClass.values()) {
            val fresh = render(cache(), PrayerWidgetDataState.Fresh, size)
            assertTrue(fresh.root.contentDescription.contains("Sıradaki vakit İkindi"))
            assertEquals(View.VISIBLE, fresh.root.findViewById<View>(R.id.prayer_widget_chronometer).visibility)
            assertEquals(
                if (size == PrayerWidgetSizeClass.SMALL) null else "%s kaldı",
                fresh.root.findViewById<Chronometer>(R.id.prayer_widget_chronometer).format
            )
            assertTrue(bitmapHasVisiblePixels(fresh.bitmap))
            saveBitmap(fresh.bitmap, "fresh_${size.fileName()}")

            val stale = render(cache(), PrayerWidgetDataState.Stale, size)
            assertTrue(stale.root.text(R.id.prayer_widget_city).contains("Eski veri"))
            assertEquals(
                when (size) {
                    PrayerWidgetSizeClass.SMALL -> "Eski veri"
                    PrayerWidgetSizeClass.MEDIUM -> "İSTANBUL • 22 Safer 1448 • Eski veri"
                    PrayerWidgetSizeClass.LARGE -> "İSTANBUL • Eski veri"
                },
                stale.root.text(R.id.prayer_widget_city)
            )
            assertTrue(stale.root.contentDescription.contains("Eski veri gösteriliyor"))
            assertEquals(View.GONE, stale.root.findViewById<View>(R.id.prayer_widget_chronometer).visibility)
            assertTrue(bitmapHasVisiblePixels(stale.bitmap))
            saveBitmap(stale.bitmap, "stale_${size.fileName()}")

            val unavailable = render(null, PrayerWidgetDataState.Unavailable, size)
            assertEquals("Vakitler alınamadı", unavailable.root.text(R.id.prayer_widget_next_prayer))
            assertTrue(unavailable.root.contentDescription.contains("Vakitler alınamadı"))
            assertEquals(View.GONE, unavailable.root.findViewById<View>(R.id.prayer_widget_chronometer).visibility)
            assertEquals(View.GONE, unavailable.root.findViewById<View>(R.id.prayer_widget_prayer_grid).visibility)
            assertTrue(bitmapHasVisiblePixels(unavailable.bitmap))
            saveBitmap(unavailable.bitmap, "unavailable_${size.fileName()}")
        }
    }

    @Test
    fun allLayoutsRenderInLightAndDarkConfigurations() {
        for (nightMode in listOf(false, true)) {
            val themedContext = context.withNightMode(nightMode)
            val themeName = if (nightMode) "dark" else "light"
            for (size in PrayerWidgetSizeClass.values()) {
                val rendered = render(
                    cache = cache(),
                    state = PrayerWidgetDataState.Fresh,
                    size = size,
                    renderContext = themedContext
                )
                saveBitmap(rendered.bitmap, "fresh_${size.fileName()}_$themeName")
            }
            val textColor = themedContext.getColor(R.color.widget_text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (nightMode) darkTextColor = textColor else lightTextColor = textColor
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertNotEquals(lightTextColor, darkTextColor)
        }
    }

    @Test
    fun largeLayoutShowsDiyanetRamadanIftarAndDay() {
        val ramadanDate = LocalDate.of(2026, 2, 20)
        val ramadanNow = ramadanDate.atTime(14, 0).atZone(ZONE)
        val ramadan = cache(ramadanDate, maghrib = "18:30", hijriText = "2 Ramazan 1447")
        val range = DiyanetRamadanCalendar.find(1447)!!.toRange()
        val rendered = render(
            cache = ramadan,
            state = PrayerWidgetDataState.Fresh,
            size = PrayerWidgetSizeClass.LARGE,
            now = ramadanNow,
            ramadanRange = range
        )

        assertTrue(rendered.root.text(R.id.prayer_widget_ramadan).contains("İftar 18:30"))
        assertTrue(rendered.root.text(R.id.prayer_widget_ramadan).contains("Ramazan’ın 2. günü"))
        assertEquals(View.VISIBLE, rendered.root.findViewById<View>(R.id.prayer_widget_ramadan).visibility)
        saveBitmap(rendered.bitmap, "ramadan_large_light")
    }

    @Test
    fun api30DeviceExportsFallbackColorSample() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            val rendered = render(cache(), PrayerWidgetDataState.Fresh, PrayerWidgetSizeClass.LARGE)
            saveBitmap(rendered.bitmap, "api30_fallback_colors")
            assertEquals(0xF012372A.toInt(), context.getColor(R.color.widget_background))
        }
    }

    private fun render(
        cache: CachedPrayerDay?,
        state: PrayerWidgetDataState,
        size: PrayerWidgetSizeClass,
        renderContext: Context = context,
        now: ZonedDateTime = this.now,
        ramadanRange: com.example.namazvakti.domain.model.RamadanRange? = null
    ): Rendered {
        val snapshot = com.example.namazvakti.widget.PrayerWidgetSnapshot(
            cache = cache,
            location = location,
            now = now,
            dataState = state,
            ramadanRange = ramadanRange
        )
        val root = renderer.render(
            renderContext,
            snapshot,
            size,
            elapsedRealtime = SystemClock.elapsedRealtime()
        )
            .apply(renderContext, FrameLayout(renderContext))
        val density = renderContext.resources.displayMetrics.density
        val (widthDp, heightDp) = size.dimensionsDp()
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        return Rendered(root, bitmap)
    }

    private fun Context.withNightMode(nightMode: Boolean): Context {
        val config = Configuration(resources.configuration)
        val nightFlag = if (nightMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightFlag
        return createConfigurationContext(config)
    }

    private fun saveBitmap(bitmap: Bitmap, name: String) {
        val directory = File(context.getExternalFilesDir(null), "widget_refresh_20261105_v2")
        check(directory.mkdirs() || directory.isDirectory) { "Could not create ${directory.absolutePath}" }
        val file = File(directory, "$name.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        exportForAdbPull(file)
        Log.i(TAG, "Widget render saved: ${file.absolutePath}")
    }

    private fun exportForAdbPull(file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_PICTURES}/widget_refresh_20261105_v2/"
        val existingUri = resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
            arrayOf(file.name, relativePath),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(collection, cursor.getLong(0))
            } else null
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val imageUri = existingUri?.also { resolver.update(it, values, null, null) }
            ?: checkNotNull(resolver.insert(collection, values)) { "Could not export ${file.name}" }
        resolver.openOutputStream(imageUri, "wt")!!.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }
        resolver.update(
            imageUri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null
        )
    }

    private fun cache(
        date: LocalDate = LocalDate.of(2026, 8, 6),
        fajr: String = "04:12",
        maghrib: String = "20:21",
        hijriText: String = "22 Safer 1448"
    ) = CachedPrayerDay(
        date = date,
        location = location,
        timezone = ZONE,
        settings = PrayerCalculationSettings(),
        prayerTimes = PrayerTimes(
            fajr = LocalTime.parse(fajr),
            sunrise = LocalTime.of(5, 49),
            dhuhr = LocalTime.of(13, 8),
            asr = LocalTime.of(17, 2),
            maghrib = LocalTime.parse(maghrib),
            isha = LocalTime.of(22, 1)
        ),
        hijriText = hijriText,
        fetchedAt = Instant.parse("2026-08-06T10:00:00Z")
    )

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun bitmapHasVisiblePixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { pixel -> pixel ushr 24 != 0 }
    }

    private fun PrayerWidgetSizeClass.dimensionsDp(): Pair<Int, Int> = when (this) {
        PrayerWidgetSizeClass.SMALL -> 110 to 60
        PrayerWidgetSizeClass.MEDIUM -> 250 to 80
        PrayerWidgetSizeClass.LARGE -> 250 to 140
    }

    private fun PrayerWidgetSizeClass.fileName(): String = name.lowercase()

    private data class Rendered(val root: View, val bitmap: Bitmap)

    private companion object {
        const val TAG = "PrayerWidgetRenderTest"
        val ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
        var lightTextColor: Int? = null
        var darkTextColor: Int? = null
    }
}
