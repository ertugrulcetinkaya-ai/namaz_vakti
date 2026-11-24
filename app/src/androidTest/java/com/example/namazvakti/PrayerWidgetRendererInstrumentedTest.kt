package com.example.namazvakti

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import com.example.namazvakti.domain.model.displayHijriText
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.PrayerTimeOffsets
import com.example.namazvakti.domain.model.withOffsets
import com.example.namazvakti.widget.PrayerWidgetSizeClass
import com.example.namazvakti.widget.PrayerWidgetSnapshot
import com.example.namazvakti.widget.renderer.PrayerWidgetDataState
import com.example.namazvakti.widget.renderer.PrayerWidgetRenderer
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.ArrayDeque
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun rendersPrayerTimesAfterApplyingMinuteOffsets() {
        val adjustedCache = cache().withOffsets(
            PrayerTimeOffsets(imsakMinutes = 5, dhuhrMinutes = -3)
        )
        val rendered = render(
            cache = adjustedCache,
            state = PrayerWidgetDataState.Fresh,
            size = PrayerWidgetSizeClass.MEDIUM,
            dimensionsDp = 515 to 126
        )

        assertEquals("04:17", rendered.root.text(timeId(1)))
        assertEquals("13:05", rendered.root.text(activeTimeId(3)))
    }

    @Test
    fun responsiveLayoutsFitAllPrayerRowsAndHighlightOnlyTheCurrentPrayer() {
        for (widthDp in listOf(280, 320, 360, 380, 515)) {
            val rendered = render(
                cache = cache(),
                state = PrayerWidgetDataState.Fresh,
                size = PrayerWidgetSizeClass.MEDIUM,
                dimensionsDp = widthDp to 126
            )
            val grid = rendered.root.findViewById<ViewGroup>(R.id.prayer_widget_prayer_grid)
            assertEquals("Two rows below 400dp, six columns above", if (widthDp < 400) 2 else 6, grid.childCount)
            if (widthDp >= 400) assertEquals(6, grid.childCount)
            else assertEquals(3, (grid.getChildAt(0) as ViewGroup).childCount)

            val activeIndex = 3
            for (index in 1..6) {
                val underline = rendered.root.findViewById<View>(underlineId(index))
                assertEquals(
                    "Only the current prayer has an underline at ${widthDp}dp",
                    if (index == activeIndex) View.VISIBLE else View.GONE,
                    underline.visibility
                )
                if (widthDp < 320) {
                    val item = rendered.root.findViewById<TextView>(compactItemId(index))
                    assertFalse(item.text.toString().contains('►'))
                } else {
                    val label = rendered.root.findViewById<TextView>(
                        if (index == activeIndex) activeLabelId(index) else labelId(index)
                    )
                    val time = rendered.root.findViewById<TextView>(
                        if (index == activeIndex) activeTimeId(index) else timeId(index)
                    )
                    assertTrue(label.text.isNotBlank())
                    assertEquals("Prayer label is visible at ${widthDp}dp", View.VISIBLE, label.visibility)
                    assertTrue(time.text.matches(Regex("\\d{2}:\\d{2}")))
                    assertEquals("Prayer time is visible at ${widthDp}dp", View.VISIBLE, time.visibility)
                    assertEquals("tnum", time.fontFeatureSettings)
                }
            }
            assertTrue(rendered.root.contentDescription.toString().contains("Şu an: Öğle vakti"))
            assertNoTextClipping(rendered.root)
        }
    }

    @Test
    fun widgetPaletteAndAccessibilityStayTheSameInLightAndDarkMode() {
        val light = context.withNightMode(false)
        val dark = context.withNightMode(true)
        for (size in PrayerWidgetSizeClass.values()) {
            val lightWidget = render(cache(), PrayerWidgetDataState.Fresh, size, light)
            val darkWidget = render(cache(), PrayerWidgetDataState.Fresh, size, dark)
            assertEquals(light.getColor(R.color.widget_background), dark.getColor(R.color.widget_background))
            assertEquals(light.getColor(R.color.widget_text), dark.getColor(R.color.widget_text))
            assertEquals(0xFF0F3B33.toInt(), light.getColor(R.color.widget_background))
            assertTrue(lightWidget.root.contentDescription.toString().contains("Şu an: Öğle vakti"))
            assertTrue(darkWidget.root.contentDescription.toString().contains("Şu an: Öğle vakti"))
        }
        val contrast = contrastRatio(context.getColor(R.color.widget_secondary_text), context.getColor(R.color.widget_background))
        assertTrue("Secondary text contrast was $contrast", contrast >= 4.5)
    }

    @Test
    fun smallUsesCountdownCardAndStaleSuffixUsesSecondaryColor() {
        val fresh = render(cache(), PrayerWidgetDataState.Fresh, PrayerWidgetSizeClass.SMALL)
        assertEquals("İkindi'ye kalan", fresh.root.text(R.id.prayer_widget_next_prayer))
        assertEquals("İSTANBUL · 17:02", fresh.root.text(R.id.prayer_widget_city))
        assertEquals("5:07", fresh.root.text(R.id.prayer_widget_chronometer))
        assertEquals(View.VISIBLE, fresh.root.findViewById<View>(R.id.prayer_widget_next_prayer).visibility)
        assertEquals(View.VISIBLE, fresh.root.findViewById<View>(R.id.prayer_widget_chronometer).visibility)
        assertEquals(View.VISIBLE, fresh.root.findViewById<View>(R.id.prayer_widget_city).visibility)
        assertEquals(context.getColor(R.color.widget_active_text), fresh.root.findViewById<TextView>(R.id.prayer_widget_chronometer).currentTextColor)

        val stale = render(cache(), PrayerWidgetDataState.Stale, PrayerWidgetSizeClass.MEDIUM)
        val city = stale.root.findViewById<TextView>(R.id.prayer_widget_city)
        assertTrue(city.text.toString().endsWith("• Eski"))
        assertTrue(stale.root.contentDescription.toString().contains("Eski veri gösteriliyor"))
    }

    @Test
    fun largeLayoutKeepsHijriDateProvinceNoteAndReligiousDay() {
        val fallbackLocation = location.copy(diyanetDistrictId = "unmapped-district", districtName = "UNKNOWN")
        val fallbackCache = cache().copy(location = fallbackLocation, source = PrayerDataSource.ALADHAN)
        val eventDate = LocalDate.of(2026, 8, 24)
        val event = DiyanetReligiousDays.on(eventDate).first { it.isNight }
        val rendered = render(
            cache = fallbackCache.copy(date = eventDate),
            state = PrayerWidgetDataState.Fresh,
            size = PrayerWidgetSizeClass.LARGE,
            now = eventDate.atTime(14, 0).atZone(ZONE),
            religiousDays = listOf(event)
        )
        assertEquals("Unknown, İstanbul", rendered.root.text(R.id.prayer_widget_city))
        assertEquals(cache(eventDate).displayHijriText(), rendered.root.text(R.id.prayer_widget_date))
        assertEquals("İl merkezi", rendered.root.text(R.id.prayer_widget_province_center))
        assertEquals("Bu akşam Mevlid Kandili", rendered.root.text(R.id.prayer_widget_ramadan))
        assertEquals(View.VISIBLE, rendered.root.findViewById<View>(R.id.prayer_widget_ramadan).visibility)
        assertNoTextClipping(rendered.root)
    }

    @Test
    fun exportsWidgetScreenshotsForTheApi30AndApi35PullRequest() {
        val api = Build.VERSION.SDK_INT
        for ((size, dimensions) in listOf(
            PrayerWidgetSizeClass.SMALL to (159 to 81),
            PrayerWidgetSizeClass.MEDIUM to (515 to 126),
            PrayerWidgetSizeClass.LARGE to (515 to 278)
        )) {
            val bitmap = renderAttachedWidget(size, dimensions)
            saveBitmap(bitmap, "widget_${size.fileName()}_api$api")
        }
    }

    private fun render(
        cache: CachedPrayerDay?,
        state: PrayerWidgetDataState,
        size: PrayerWidgetSizeClass,
        renderContext: Context = context,
        now: ZonedDateTime = this.now,
        religiousDays: List<com.example.namazvakti.domain.model.DiyanetReligiousDay> = emptyList(),
        dimensionsDp: Pair<Int, Int> = size.dimensionsDp()
    ): Rendered {
        val snapshot = PrayerWidgetSnapshot(
            cache = cache,
            location = cache?.location ?: location,
            now = now,
            dataState = state,
            religiousDays = religiousDays
        )
        val root = renderer.render(
            renderContext,
            snapshot,
            size,
            elapsedRealtime = SystemClock.elapsedRealtime(),
            widthDp = dimensionsDp.first
        ).apply(renderContext, FrameLayout(renderContext))
        val chronometer = root.findViewById<Chronometer>(R.id.prayer_widget_chronometer)
        if (size == PrayerWidgetSizeClass.SMALL && state == PrayerWidgetDataState.Fresh) {
            chronometer.text = "5:07"
        } else if (size == PrayerWidgetSizeClass.LARGE && state == PrayerWidgetDataState.Fresh) {
            chronometer.text = "5:07 kaldı"
        }
        measureAndLayout(root, renderContext, dimensionsDp)
        val bitmapWidth = (dimensionsDp.first * renderContext.resources.displayMetrics.density).toInt()
        val bitmapHeight = (dimensionsDp.second * renderContext.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        return Rendered(root, bitmap)
    }

    private fun renderAttachedWidget(
        size: PrayerWidgetSizeClass,
        dimensionsDp: Pair<Int, Int>
    ): Bitmap {
        val screenshotLocation = if (size == PrayerWidgetSizeClass.LARGE) {
            PrayerLocation(
                "Ankara",
                "Turkey",
                "ANKARA",
                ZONE,
                diyanetDistrictId = "screenshot-unmapped-district",
                districtName = "Çankaya"
            )
        } else {
            PrayerLocation("Ankara", "Turkey", "ANKARA", ZONE)
        }
        val screenshotDate = LocalDate.of(2026, 8, 24)
        val screenshotCache = when (size) {
            PrayerWidgetSizeClass.SMALL -> cache().copy(
                location = screenshotLocation,
                prayerTimes = cache().prayerTimes.copy(
                    fajr = LocalTime.of(6, 8),
                    sunrise = LocalTime.of(7, 30)
                )
            )
            PrayerWidgetSizeClass.LARGE -> cache(screenshotDate).copy(location = screenshotLocation)
            PrayerWidgetSizeClass.MEDIUM -> cache().copy(location = screenshotLocation)
        }
        val screenshotNow = when (size) {
            PrayerWidgetSizeClass.SMALL -> LocalDate.of(2026, 8, 6)
                .atTime(6, 2, 53)
                .atZone(ZONE)
            PrayerWidgetSizeClass.LARGE -> screenshotDate.atTime(16, 56, 53).atZone(ZONE)
            PrayerWidgetSizeClass.MEDIUM -> now
        }
        val snapshot = PrayerWidgetSnapshot(
            cache = screenshotCache,
            location = screenshotLocation,
            now = screenshotNow,
            dataState = PrayerWidgetDataState.Fresh,
            religiousDays = if (size == PrayerWidgetSizeClass.LARGE) {
                DiyanetReligiousDays.on(screenshotDate)
            } else {
                emptyList()
            }
        )
        var screenshot: Bitmap? = null
        ActivityScenario.launch(com.example.namazvakti.ui.main.MainActivity::class.java).use { scenario ->
            val density = context.resources.displayMetrics.density
            val width = (dimensionsDp.first * density).toInt()
            val height = (dimensionsDp.second * density).toInt()
            var widgetView: View? = null
            scenario.onActivity { activity ->
                val remoteViews = renderer.render(
                    activity,
                    snapshot,
                    size,
                    elapsedRealtime = SystemClock.elapsedRealtime(),
                    widthDp = dimensionsDp.first
                )
                val host = remoteViews.apply(activity, FrameLayout(activity))
                widgetView = host
                (activity.window.decorView as ViewGroup).addView(
                    host,
                    FrameLayout.LayoutParams(width, height)
                )
                host.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                host.layout(0, 0, width, height)
                if (size == PrayerWidgetSizeClass.SMALL) {
                    host.findViewById<Chronometer>(R.id.prayer_widget_chronometer).text = "5:07"
                } else if (size == PrayerWidgetSizeClass.LARGE) {
                    host.findViewById<Chronometer>(R.id.prayer_widget_chronometer).text = "5:07 kaldı"
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val host = checkNotNull(widgetView)
                if (size != PrayerWidgetSizeClass.MEDIUM) {
                    host.findViewById<Chronometer>(R.id.prayer_widget_chronometer).apply {
                        stop()
                        text = if (size == PrayerWidgetSizeClass.SMALL) "5:07" else "5:07 kaldı"
                    }
                }
                screenshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    host.draw(Canvas(bitmap))
                }
                (activity.window.decorView as ViewGroup).removeView(host)
            }
        }
        return checkNotNull(screenshot)
    }

    private fun cache(date: LocalDate = LocalDate.of(2026, 8, 6)) = CachedPrayerDay(
        date = date,
        location = location,
        timezone = ZONE,
        settings = PrayerCalculationSettings(),
        prayerTimes = PrayerTimes(
            fajr = LocalTime.parse("04:12"),
            sunrise = LocalTime.parse("05:49"),
            dhuhr = LocalTime.parse("13:08"),
            asr = LocalTime.parse("17:02"),
            maghrib = LocalTime.parse("20:21"),
            isha = LocalTime.parse("22:01")
        ),
        hijriText = "22 Safer 1448",
        fetchedAt = Instant.parse("2026-08-06T10:00:00Z")
    )

    private fun Context.withNightMode(nightMode: Boolean): Context {
        val configuration = Configuration(resources.configuration)
        val flag = if (nightMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or flag
        return createConfigurationContext(configuration)
    }

    private fun measureAndLayout(root: View, renderContext: Context, dimensions: Pair<Int, Int>) {
        val density = renderContext.resources.displayMetrics.density
        val width = (dimensions.first * density).toInt()
        val height = (dimensions.second * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
    }

    private fun assertNoTextClipping(root: View) {
        val stack = ArrayDeque<View>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val view = stack.removeLast()
            if (view.visibility != View.VISIBLE) continue
            if (view is TextView && view.text.isNotEmpty()) {
                val layout = view.layout ?: error("TextView layout missing: ${view.text}")
                assertTrue("${view.text} is ellipsized", (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
                assertEquals(view.text.length, layout.getLineEnd(layout.lineCount - 1))
                assertTrue("${view.text} extends beyond its width", layout.getLineRight(0) <= layout.width)
                assertTrue("${view.text} extends beyond its height", layout.getLineBottom(layout.lineCount - 1) <= layout.height)
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) stack.add(view.getChildAt(i))
        }
    }

    private fun saveBitmap(bitmap: Bitmap, name: String) {
        val directory = File(context.getExternalFilesDir(null), "widget_zumrut")
        check(directory.mkdirs() || directory.isDirectory)
        val file = File(directory, "$name.png")
        FileOutputStream(file).use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val chunkSize = 2_800
        val chunks = encoded.chunked(chunkSize)
        chunks.forEachIndexed { index, chunk ->
            Log.i(TAG, "WIDGET_PNG:$name:${index + 1}/${chunks.size}:$chunk")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val relativePath = "${Environment.DIRECTORY_PICTURES}/widget_zumrut/"
            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
                arrayOf(file.name, relativePath),
                null
            )?.use { cursor -> if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = existing?.also { resolver.update(it, values, null, null) }
                ?: checkNotNull(resolver.insert(collection, values))
            resolver.openOutputStream(uri, "wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    }

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun underlineId(i: Int) = intArrayOf(
        R.id.prayer_widget_underline_1, R.id.prayer_widget_underline_2, R.id.prayer_widget_underline_3,
        R.id.prayer_widget_underline_4, R.id.prayer_widget_underline_5, R.id.prayer_widget_underline_6
    )[i - 1]
    private fun compactItemId(i: Int) = intArrayOf(
        R.id.prayer_widget_item_1, R.id.prayer_widget_item_2, R.id.prayer_widget_item_3,
        R.id.prayer_widget_item_4, R.id.prayer_widget_item_5, R.id.prayer_widget_item_6
    )[i - 1]
    private fun labelId(i: Int) = intArrayOf(
        R.id.prayer_widget_label_1, R.id.prayer_widget_label_2, R.id.prayer_widget_label_3,
        R.id.prayer_widget_label_4, R.id.prayer_widget_label_5, R.id.prayer_widget_label_6
    )[i - 1]
    private fun activeLabelId(i: Int) = intArrayOf(
        R.id.prayer_widget_active_label_1, R.id.prayer_widget_active_label_2, R.id.prayer_widget_active_label_3,
        R.id.prayer_widget_active_label_4, R.id.prayer_widget_active_label_5, R.id.prayer_widget_active_label_6
    )[i - 1]
    private fun timeId(i: Int) = intArrayOf(
        R.id.prayer_widget_time_1, R.id.prayer_widget_time_2, R.id.prayer_widget_time_3,
        R.id.prayer_widget_time_4, R.id.prayer_widget_time_5, R.id.prayer_widget_time_6
    )[i - 1]
    private fun activeTimeId(i: Int) = intArrayOf(
        R.id.prayer_widget_active_time_1, R.id.prayer_widget_active_time_2, R.id.prayer_widget_active_time_3,
        R.id.prayer_widget_active_time_4, R.id.prayer_widget_active_time_5, R.id.prayer_widget_active_time_6
    )[i - 1]

    private fun PrayerWidgetSizeClass.dimensionsDp(): Pair<Int, Int> = when (this) {
        PrayerWidgetSizeClass.SMALL -> 159 to 81
        PrayerWidgetSizeClass.MEDIUM -> 515 to 126
        PrayerWidgetSizeClass.LARGE -> 515 to 278
    }

    private fun PrayerWidgetSizeClass.fileName() = name.lowercase(Locale.ROOT)

    private fun contrastRatio(foreground: Int, background: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(value: Int): Double {
                val normalized = value / 255.0
                return if (normalized <= 0.04045) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
            val r = channel((color shr 16) and 0xFF)
            val g = channel((color shr 8) and 0xFF)
            val b = channel(color and 0xFF)
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
        val values = listOf(luminance(foreground), luminance(background)).sortedDescending()
        return (values[0] + 0.05) / (values[1] + 0.05)
    }

    private data class Rendered(val root: View, val bitmap: Bitmap)

    private companion object {
        const val TAG = "ZumrutWidgetScreenshot"
        val ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
    }
}
