package com.example.namazvakti.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTextFitTest {
    /** Linear model: a "00:00" time is 63dp wide and the stack 80dp tall at scale 1. */
    private val measurer = object : WidgetTextFit.Measurer {
        override fun maxCellTextWidth(scale: Float) = 63f * scale
        override fun maxRowWidth(scale: Float) = 200f * scale
        override fun contentHeight(scale: Float) = 80f * scale
    }
    private val wide = WidgetTextFit.Geometry(columns = 6, horizontalPaddingDp = 16f, fixedVerticalDp = 12f)
    private val large = WidgetTextFit.Geometry(columns = 3, horizontalPaddingDp = 36f, fixedVerticalDp = 20f)

    @Test
    fun tallWideWidget_isWidthLimited() {
        val scale = WidgetTextFit.bestScale(420f, 400f, wide, measurer)
        val cell = (420f - 16f) / 6 - WidgetTextFit.CELL_GAP_DP
        assertTrue(63f * scale <= cell)
        assertTrue(cell - 63f * scale < 0.5f)
    }

    @Test
    fun largeWidget_isHeightLimited() {
        val scale = WidgetTextFit.bestScale(400f, 150f, large, measurer)
        val budget = 150f * WidgetTextFit.HEIGHT_SAFETY - 20f
        assertTrue(80f * scale <= budget)
        assertTrue(budget - 80f * scale < 0.5f)
        assertTrue(scale > 1f)
    }

    @Test
    fun tooSmallBox_keepsBaseSizes() {
        assertEquals(1f, WidgetTextFit.bestScale(300f, 60f, wide, measurer))
    }

    @Test
    fun hugeBox_isCapped() {
        assertEquals(WidgetTextFit.MAX_SCALE, WidgetTextFit.bestScale(3000f, 3000f, large, measurer))
    }
}
