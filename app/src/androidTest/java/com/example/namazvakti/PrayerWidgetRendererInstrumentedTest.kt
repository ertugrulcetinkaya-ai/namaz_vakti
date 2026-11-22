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
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.DiyanetRamadanCalendar
import com.example.namazvakti.domain.model.DiyanetReligiousDay
import com.example.namazvakti.domain.model.DiyanetReligiousDays
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.widget.PrayerWidgetSizeClass
import com.example.namazvakti.widget.renderer.PrayerWidgetDataState
import com.example.namazvakti.widget.renderer.PrayerWidgetRenderer
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            assertEquals(
                if (size == PrayerWidgetSizeClass.MEDIUM) View.GONE else View.VISIBLE,
                fresh.root.findViewById<View>(R.id.prayer_widget_chronometer).visibility
            )
            assertEquals(
                if (size == PrayerWidgetSizeClass.LARGE) "%s kaldı" else null,
                fresh.root.findViewById<Chronometer>(R.id.prayer_widget_chronometer).format
            )
            if (size == PrayerWidgetSizeClass.MEDIUM) {
                assertEquals(View.GONE, fresh.root.findViewById<View>(R.id.prayer_widget_next_prayer).visibility)
                assertEquals("İSTANBUL • 23 Safer 1448", fresh.root.text(R.id.prayer_widget_city))
                assertEquals(View.VISIBLE, fresh.root.findViewById<View>(R.id.prayer_widget_prayer_grid).visibility)
            }
            if (size == PrayerWidgetSizeClass.LARGE) {
                assertEquals("İSTANBUL • 23 Safer 1448", fresh.root.text(R.id.prayer_widget_city))
                assertEquals("İkindi 17:02", fresh.root.text(R.id.prayer_widget_next_prayer))
                assertEquals(View.GONE, fresh.root.findViewById<View>(R.id.prayer_widget_date).visibility)
            }
            if (size == PrayerWidgetSizeClass.SMALL) {
                assertEquals("İSTANBUL", fresh.root.text(R.id.prayer_widget_city))
                assertEquals("İkindi 17:02", fresh.root.text(R.id.prayer_widget_next_prayer))
            }
            if (size != PrayerWidgetSizeClass.SMALL) {
                val activePrayer = fresh.root.findViewById<TextView>(R.id.prayer_widget_item_3)
                assertTrue(activePrayer.text.startsWith("►Öğle"))
                assertEquals(context.getColor(R.color.widget_active_text), activePrayer.currentTextColor)
            }
            assertTrue(bitmapHasVisiblePixels(fresh.bitmap))
            saveBitmap(fresh.bitmap, "fresh_${size.fileName()}")

            val stale = render(cache(), PrayerWidgetDataState.Stale, size)
            assertTrue(
                when (size) {
                    PrayerWidgetSizeClass.SMALL -> stale.root.text(R.id.prayer_widget_chronometer).contains("Eski veri")
                    PrayerWidgetSizeClass.MEDIUM -> stale.root.text(R.id.prayer_widget_city).contains("Eski veri")
                    PrayerWidgetSizeClass.LARGE -> stale.root.text(R.id.prayer_widget_ramadan).contains("Eski veri")
                }
            )
            assertEquals(
                when (size) {
                    PrayerWidgetSizeClass.SMALL -> "İSTANBUL"
                    PrayerWidgetSizeClass.MEDIUM -> "İSTANBUL • 23 Safer 1448 • Eski veri"
                    PrayerWidgetSizeClass.LARGE -> "İSTANBUL • 23 Safer 1448"
                },
                stale.root.text(R.id.prayer_widget_city)
            )
            assertTrue(stale.root.contentDescription.contains("Eski veri gösteriliyor"))
            if (size == PrayerWidgetSizeClass.LARGE) {
                assertEquals("Eski veri", stale.root.text(R.id.prayer_widget_ramadan))
            }
            assertEquals(
                if (size == PrayerWidgetSizeClass.SMALL) View.VISIBLE else View.GONE,
                stale.root.findViewById<View>(R.id.prayer_widget_chronometer).visibility
            )
            if (size == PrayerWidgetSizeClass.SMALL) {
                assertEquals("Eski veri", stale.root.text(R.id.prayer_widget_chronometer))
            }
            assertTrue(bitmapHasVisiblePixels(stale.bitmap))
            saveBitmap(stale.bitmap, "stale_${size.fileName()}")

            val unavailable = render(null, PrayerWidgetDataState.Unavailable, size)
            assertEquals(
                if (size == PrayerWidgetSizeClass.SMALL) "Vakit yok" else "Vakitler alınamadı",
                unavailable.root.text(R.id.prayer_widget_next_prayer)
            )
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
    fun wideLayoutsFitTheLongestActivePrayerAtMeasuredWidths() {
        val sunriseNow = LocalDate.of(2026, 8, 6).atTime(6, 0).atZone(ZONE)
        for (size in listOf(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.LARGE)) {
            val rendered = render(
                cache = cache(),
                state = PrayerWidgetDataState.Fresh,
                size = size,
                now = sunriseNow
            )
            val sunrise = rendered.root.findViewById<TextView>(R.id.prayer_widget_item_2)
            assertTrue(sunrise.text.startsWith("►Güneş"))
            assertEquals(context.getColor(R.color.widget_active_text), sunrise.currentTextColor)
        }
    }

    @Test
    fun prayerLabelMeasurementShowsShortLabelsAreOnlyNeededBelow320Dp() {
        for (size in listOf(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.LARGE)) {
            for (widthDp in listOf(280, 320, 360, 380, 515)) {
                val heightDp = if (size == PrayerWidgetSizeClass.MEDIUM) 126 else 278
                val dimensionsDp = widthDp to heightDp
                val rendered = render(
                    cache = cache(),
                    state = PrayerWidgetDataState.Fresh,
                    size = size,
                    dimensionsDp = dimensionsDp
                )
                val items = (1..6).map { rendered.root.findViewById<TextView>(prayerItemId(it)) }
                if (widthDp == 280) {
                    val fullLabels = listOf("İmsak", "Güneş", "►Öğle", "İkindi", "Akşam", "Yatsı")
                    val times = listOf("04:12", "05:49", "13:08", "17:02", "20:21", "22:01")
                    fullLabels.zip(times).forEachIndexed { index, (label, time) ->
                        items[index].text = "$label $time"
                    }
                    measureAndLayout(rendered.root, context, dimensionsDp)
                    assertTrue(
                        "At least one full prayer label should clip at 280dp for $size",
                        items.any { it.layout.getEllipsisCount(0) > 0 }
                    )
                }
                if (widthDp == 515) {
                    val expectedTextSize = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, 22f, context.resources.displayMetrics
                    )
                    items.forEach { assertEquals("Prayer label size at 515dp", expectedTextSize, it.textSize, 1f) }
                }
            }
        }
    }

    @Test
    fun mediumAndLargeLayoutsRenderWithoutEllipsisAtLauncherWidthsAndLargeKeepsCountdown() {
        val widthsDp = listOf(280, 320, 360, 380, 515)
        val fullLabels = listOf(
            R.string.prayer_imsak,
            R.string.prayer_sunrise,
            R.string.prayer_dhuhr,
            R.string.prayer_asr,
            R.string.prayer_maghrib,
            R.string.prayer_isha
        ).map { context.getString(it) }
        val shortLabels = listOf(
            R.string.prayer_imsak_short,
            R.string.prayer_sunrise_short,
            R.string.prayer_dhuhr_short,
            R.string.prayer_asr_short,
            R.string.prayer_maghrib_short,
            R.string.prayer_isha_short
        ).map { context.getString(it) }
        val times = listOf("04:12", "05:49", "13:08", "17:02", "20:21", "22:01")

        for (size in listOf(PrayerWidgetSizeClass.MEDIUM, PrayerWidgetSizeClass.LARGE)) {
            for (widthDp in widthsDp) {
                val heightDp = if (size == PrayerWidgetSizeClass.MEDIUM) 126 else 278
                val rendered = render(
                    cache = cache(),
                    state = PrayerWidgetDataState.Fresh,
                    size = size,
                    dimensionsDp = widthDp to heightDp
                )
                val labels = if (widthDp < 320) shortLabels else fullLabels
                val expectedTexts = labels.zip(times).mapIndexed { index, (label, time) ->
                    "${if (index == 2) "►" else ""}$label $time"
                }
                assertEquals(
                    expectedTexts,
                    (1..6).map { index -> rendered.root.findViewById<TextView>(prayerItemId(index)).text.toString() }
                )
                assertEquals(
                    context.getColor(R.color.widget_active_text),
                    rendered.root.findViewById<TextView>(prayerItemId(3)).currentTextColor
                )
                assertTrue(bitmapHasVisiblePixels(rendered.bitmap))
                if (size == PrayerWidgetSizeClass.LARGE) {
                    assertEquals(
                        View.VISIBLE,
                        rendered.root.findViewById<View>(R.id.prayer_widget_chronometer).visibility
                    )
                }
            }
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
    fun largeWidgetRendersTodaysReligiousDayInBottomInformationRow() {
        val eventDate = LocalDate.of(2026, 8, 24)
        val event = DiyanetReligiousDays.on(eventDate).first { it.isNight }
        val rendered = render(
            cache = cache(date = eventDate),
            state = PrayerWidgetDataState.Fresh,
            size = PrayerWidgetSizeClass.LARGE,
            now = eventDate.atTime(14, 0).atZone(ZONE),
            religiousDays = listOf(event)
        )

        assertEquals("Bu akşam Mevlid Kandili", rendered.root.text(R.id.prayer_widget_ramadan))
        assertEquals(View.VISIBLE, rendered.root.findViewById<View>(R.id.prayer_widget_ramadan).visibility)
        saveBitmap(rendered.bitmap, "religious_day_large_light")
    }

    @Test
    fun largeWidgetPrioritizesNightWhenMultipleObservancesShareDate() {
        val eventDate = LocalDate.of(2026, 12, 10)
        val events = DiyanetReligiousDays.on(eventDate)
        val rendered = render(
            cache = cache(date = eventDate),
            state = PrayerWidgetDataState.Fresh,
            size = PrayerWidgetSizeClass.LARGE,
            now = eventDate.atTime(14, 0).atZone(ZONE),
            religiousDays = events
        )

        val text = rendered.root.text(R.id.prayer_widget_ramadan)
        assertTrue(text.startsWith("Bu akşam Regaip Kandili"))
        assertTrue(text.split(" · ").size <= events.size)
        saveBitmap(rendered.bitmap, "religious_day_same_date_large_light")
    }

    @Test
    fun provinceCenterFallbackNoteAppearsOnlyInLargeNonCompactLayout() {
        val province = requireNotNull(DiyanetDistricts.forCity("Istanbul", "Turkey"))
        val district = DiyanetDistricts.districts.first {
            it.provinceId == province.id && it.name == "MALTEPE"
        }
        val fallbackCache = cache().copy(
            location = location.copy(diyanetDistrictId = district.id, districtName = district.name),
            source = PrayerDataSource.ALADHAN
        )

        val small = render(fallbackCache, PrayerWidgetDataState.Fresh, PrayerWidgetSizeClass.SMALL)
        val medium = render(fallbackCache, PrayerWidgetDataState.Fresh, PrayerWidgetSizeClass.MEDIUM)
        val large = render(
            fallbackCache,
            PrayerWidgetDataState.Fresh,
            PrayerWidgetSizeClass.LARGE,
            dimensionsDp = 515 to 278
        )
        val large380 = render(
            fallbackCache,
            PrayerWidgetDataState.Fresh,
            PrayerWidgetSizeClass.LARGE,
            dimensionsDp = 380 to 278
        )
        val compactLarge = render(
            fallbackCache,
            PrayerWidgetDataState.Fresh,
            PrayerWidgetSizeClass.LARGE,
            dimensionsDp = 300 to 278
        )

        assertFalse(small.root.text(R.id.prayer_widget_city).contains("İl merkezi"))
        assertFalse(medium.root.text(R.id.prayer_widget_city).contains("İl merkezi"))
        assertTrue(large.root.text(R.id.prayer_widget_city).contains("• İl merkezi"))
        assertTrue(large380.root.text(R.id.prayer_widget_city).contains("• İl merkezi"))
        assertFalse(compactLarge.root.text(R.id.prayer_widget_city).contains("İl merkezi"))
        assertEquals(View.GONE, large.root.findViewById<View>(R.id.prayer_widget_ramadan).visibility)
    }

    @Test
    fun mediumShowsDistrictNameWhenItFitsAndSmallFallsBackButTalkBackKeepsIt() {
        val district = DiyanetDistricts.districts.single {
            it.provinceName == "İSTANBUL" && it.name == "KARTAL"
        }
        val selectedLocation = location.copy(
            diyanetDistrictId = district.id,
            districtName = district.name
        )
        val districtCache = cache().copy(location = selectedLocation)

        val medium = render(
            districtCache,
            PrayerWidgetDataState.Fresh,
            PrayerWidgetSizeClass.MEDIUM,
            dimensionsDp = 380 to 126
        )
        val small = render(
            districtCache,
            PrayerWidgetDataState.Fresh,
            PrayerWidgetSizeClass.SMALL,
            dimensionsDp = 159 to 81
        )

        assertTrue(medium.root.text(R.id.prayer_widget_city).contains(selectedLocation.displayName))
        assertEquals("İSTANBUL", small.root.text(R.id.prayer_widget_city))
        assertTrue(medium.root.contentDescription.contains(selectedLocation.displayName))
        assertTrue(small.root.contentDescription.contains(selectedLocation.displayName))
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
        ramadanRange: com.example.namazvakti.domain.model.RamadanRange? = null,
        religiousDays: List<DiyanetReligiousDay> = emptyList(),
        dimensionsDp: Pair<Int, Int> = size.dimensionsDp()
    ): Rendered {
        val snapshot = com.example.namazvakti.widget.PrayerWidgetSnapshot(
            cache = cache,
            location = cache?.location ?: location,
            now = now,
            dataState = state,
            ramadanRange = ramadanRange,
            religiousDays = religiousDays
        )
        val root = renderer.render(
            renderContext,
            snapshot,
            size,
            elapsedRealtime = SystemClock.elapsedRealtime(),
            widthDp = dimensionsDp.first
        )
            .apply(renderContext, FrameLayout(renderContext))
        val chronometer = root.findViewById<Chronometer>(R.id.prayer_widget_chronometer)
        if (size == PrayerWidgetSizeClass.SMALL && state == PrayerWidgetDataState.Fresh) {
            chronometer.text = "5:08:14"
        } else if (size == PrayerWidgetSizeClass.LARGE) {
            chronometer.text = "5:08:14 kaldı"
        }
        measureAndLayout(root, renderContext, dimensionsDp)
        val width = (dimensionsDp.first * renderContext.resources.displayMetrics.density).toInt()
        val height = (dimensionsDp.second * renderContext.resources.displayMetrics.density).toInt()
        assertNoTextClipping(root)
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

    private fun measureAndLayout(root: View, renderContext: Context, dimensionsDp: Pair<Int, Int>) {
        val density = renderContext.resources.displayMetrics.density
        val width = (dimensionsDp.first * density).toInt()
        val height = (dimensionsDp.second * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
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

    private fun prayerItemId(index: Int): Int = when (index) {
        1 -> R.id.prayer_widget_item_1
        2 -> R.id.prayer_widget_item_2
        3 -> R.id.prayer_widget_item_3
        4 -> R.id.prayer_widget_item_4
        5 -> R.id.prayer_widget_item_5
        6 -> R.id.prayer_widget_item_6
        else -> error("Unsupported prayer item index: $index")
    }

    private fun bitmapHasVisiblePixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { pixel -> pixel ushr 24 != 0 }
    }

    private fun PrayerWidgetSizeClass.dimensionsDp(): Pair<Int, Int> = when (this) {
        PrayerWidgetSizeClass.SMALL -> 159 to 81
        PrayerWidgetSizeClass.MEDIUM -> 515 to 126
        PrayerWidgetSizeClass.LARGE -> 515 to 278
    }

    private fun assertNoTextClipping(root: View) {
        val stack = ArrayDeque<Pair<View, ViewGroup?>>()
        stack.add(root to null)
        while (stack.isNotEmpty()) {
            val (view, parent) = stack.removeLast()
            if (view.visibility != View.VISIBLE) continue
            if (parent != null) {
                assertTrue("${view.javaClass.simpleName} exceeds parent width", view.left >= 0 && view.right <= parent.width)
                assertTrue("${view.javaClass.simpleName} exceeds parent height", view.top >= 0 && view.bottom <= parent.height)
            }
            if (view is TextView) {
                val metrics = view.resources.displayMetrics
                val sizeSp = view.textSize / (metrics.density * view.resources.configuration.fontScale)
                // TextView rounds the requested 16sp to device pixels (e.g. 15.9sp at 600dpi).
                assertTrue("${view.text} uses only ${sizeSp}sp", sizeSp >= 15.5f)
                assertTrue("${view.text} exceeds its assigned height", view.measuredHeight <= view.height)
                val layout = view.layout ?: error("TextView layout missing for ${view.text}")
                if (view.text.isNotEmpty()) {
                    assertTrue("${view.text} exceeds its line limit", layout.lineCount <= view.maxLines)
                    for (line in 0 until layout.lineCount) {
                        assertEquals("${view.text} is ellipsized", 0, layout.getEllipsisCount(line))
                        assertTrue("${view.text} is cut off at the right edge", layout.getLineRight(line) <= layout.width)
                    }
                    assertEquals(view.text.length, layout.getLineEnd(layout.lineCount - 1))
                    assertTrue(
                        "${view.text} is cut off at the bottom edge",
                        layout.getLineBottom(layout.lineCount - 1) <= layout.height
                    )
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) stack.add(view.getChildAt(index) to view)
            }
        }
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
