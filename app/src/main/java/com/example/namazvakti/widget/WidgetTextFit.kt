package com.example.namazvakti.widget

/**
 * Finds the largest uniform text scale that still fits a grid widget in the
 * launcher-reported box. Every text keeps its size relative to the others; only
 * the common multiplier changes.
 */
object WidgetTextFit {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 2f

    /** Launchers often report slightly more space than the visible cell. */
    const val HEIGHT_SAFETY = 0.9f
    const val CELL_GAP_DP = 4f

    data class Geometry(
        val columns: Int,
        val horizontalPaddingDp: Float,
        /** Margins, underlines and breathing room that do not scale. */
        val fixedVerticalDp: Float
    )

    /** All results in dp for texts rendered at base size × [scale]. */
    interface Measurer {
        /** Widest label or time inside one grid cell. */
        fun maxCellTextWidth(scale: Float): Float

        /** Widest full-width line (city, date, countdown row …). */
        fun maxRowWidth(scale: Float): Float

        /** Sum of the line heights of everything stacked vertically. */
        fun contentHeight(scale: Float): Float
    }

    fun fits(scale: Float, widthDp: Float, heightDp: Float, geometry: Geometry, measurer: Measurer): Boolean {
        val contentWidth = widthDp - geometry.horizontalPaddingDp
        val cellWidth = contentWidth / geometry.columns - CELL_GAP_DP
        if (measurer.maxCellTextWidth(scale) > cellWidth) return false
        if (measurer.maxRowWidth(scale) > contentWidth) return false
        return measurer.contentHeight(scale) + geometry.fixedVerticalDp <= heightDp * HEIGHT_SAFETY
    }

    /** Largest scale in [MIN_SCALE, MAX_SCALE] that fits; MIN_SCALE if even that does not. */
    fun bestScale(widthDp: Float, heightDp: Float, geometry: Geometry, measurer: Measurer): Float {
        if (!fits(MIN_SCALE, widthDp, heightDp, geometry, measurer)) return MIN_SCALE
        if (fits(MAX_SCALE, widthDp, heightDp, geometry, measurer)) return MAX_SCALE
        var low = MIN_SCALE
        var high = MAX_SCALE
        repeat(16) {
            val mid = (low + high) / 2f
            if (fits(mid, widthDp, heightDp, geometry, measurer)) low = mid else high = mid
        }
        return low
    }
}
