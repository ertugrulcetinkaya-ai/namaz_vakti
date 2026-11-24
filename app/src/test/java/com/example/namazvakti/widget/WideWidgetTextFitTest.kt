package com.example.namazvakti.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WideWidgetTextFitTest {
    /** Simple linear font model: width = chars * 0.6em, height = 1.2em (sp == dp). */
    private val measurer = object : WideWidgetTextFit.Measurer {
        override fun cityWidth(sp: Float) = 6 * 0.6f * sp
        override fun cityHeight(sp: Float) = 1.2f * sp
        override fun dateWidth(sp: Float) = 22 * 0.6f * sp
        override fun dateHeight(sp: Float) = 1.2f * sp
        override fun maxLabelWidth(sp: Float) = 6 * 0.7f * sp
        override fun labelHeight(sp: Float) = 1.2f * sp
        override fun maxTimeWidth(sp: Float) = 5 * 0.6f * sp
        override fun timeHeight(sp: Float) = 1.2f * sp
    }

    @Test
    fun tallWideWidget_growsUntilTimesFillColumns() {
        val scale = WideWidgetTextFit.bestScale(widthDp = 420f, heightDp = 200f, measurer = measurer)
        assertTrue("scale=$scale", scale > 1f)
        val cell = (420f - 16f) / 6 - 4f
        val timeWidth = measurer.maxTimeWidth(WideWidgetTextFit.BASE_TIME_SP * scale)
        assertTrue(timeWidth <= cell)
        assertTrue(cell - timeWidth < 1f)
    }

    @Test
    fun shortWidget_isHeightLimited() {
        val scale = WideWidgetTextFit.bestScale(widthDp = 600f, heightDp = 130f, measurer = measurer)
        val sizes = WideWidgetTextFit.sizesFor(scale)
        val height = 1.2f * (sizes.citySp + sizes.dateSp + sizes.labelSp + sizes.timeSp) +
            WideWidgetTextFit.FIXED_VERTICAL_DP
        assertTrue(height <= 130f * WideWidgetTextFit.HEIGHT_SAFETY)
        assertTrue(130f * WideWidgetTextFit.HEIGHT_SAFETY - height < 1f)
        assertTrue(scale > 1f)
    }

    @Test
    fun tooSmallBox_keepsBaseSizes() {
        assertEquals(1f, WideWidgetTextFit.bestScale(widthDp = 300f, heightDp = 40f, measurer = measurer))
    }

    @Test
    fun hugeBox_isCapped() {
        assertEquals(
            WideWidgetTextFit.MAX_SCALE,
            WideWidgetTextFit.bestScale(widthDp = 2000f, heightDp = 1000f, measurer = measurer)
        )
    }
}
