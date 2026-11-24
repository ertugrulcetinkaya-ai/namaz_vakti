package com.example.namazvakti.widget

/**
 * Finds the largest uniform scale for the six-column widget text that still fits
 * the launcher-reported widget box. All four text roles keep their relative sizes;
 * only the common multiplier changes.
 */
object WideWidgetTextFit {
    const val BASE_CITY_SP = 19f
    const val BASE_DATE_SP = 14f
    const val BASE_LABEL_SP = 12f
    const val BASE_TIME_SP = 21f

    const val MIN_SCALE = 1f
    const val MAX_SCALE = 2f

    /** Launchers often report slightly more space than the visible cell. */
    const val HEIGHT_SAFETY = 0.9f
    const val COLUMN_COUNT = 6
    const val HORIZONTAL_PADDING_DP = 16f
    const val CELL_GAP_DP = 4f
    const val FIXED_VERTICAL_DP = 2f /* grid margin */ + 2f /* underline */ + 8f /* breathing room */

    /** Measurements in dp for text rendered at the given sp size. */
    interface Measurer {
        fun cityWidth(sp: Float): Float
        fun cityHeight(sp: Float): Float
        fun dateWidth(sp: Float): Float
        fun dateHeight(sp: Float): Float
        fun maxLabelWidth(sp: Float): Float
        fun labelHeight(sp: Float): Float
        fun maxTimeWidth(sp: Float): Float
        fun timeHeight(sp: Float): Float
    }

    data class Sizes(val citySp: Float, val dateSp: Float, val labelSp: Float, val timeSp: Float)

    fun sizesFor(scale: Float) = Sizes(
        BASE_CITY_SP * scale,
        BASE_DATE_SP * scale,
        BASE_LABEL_SP * scale,
        BASE_TIME_SP * scale
    )

    fun fits(scale: Float, widthDp: Float, heightDp: Float, measurer: Measurer): Boolean {
        val s = sizesFor(scale)
        val contentWidth = widthDp - HORIZONTAL_PADDING_DP
        val cellWidth = contentWidth / COLUMN_COUNT - CELL_GAP_DP
        if (measurer.maxTimeWidth(s.timeSp) > cellWidth) return false
        if (measurer.maxLabelWidth(s.labelSp) > cellWidth) return false
        if (measurer.cityWidth(s.citySp) > contentWidth) return false
        if (measurer.dateWidth(s.dateSp) > contentWidth) return false
        val contentHeight = measurer.cityHeight(s.citySp) +
            measurer.dateHeight(s.dateSp) +
            measurer.labelHeight(s.labelSp) +
            measurer.timeHeight(s.timeSp) +
            FIXED_VERTICAL_DP
        return contentHeight <= heightDp * HEIGHT_SAFETY
    }

    /** Largest scale in [MIN_SCALE, MAX_SCALE] that fits; MIN_SCALE if even that does not. */
    fun bestScale(widthDp: Float, heightDp: Float, measurer: Measurer): Float {
        if (!fits(MIN_SCALE, widthDp, heightDp, measurer)) return MIN_SCALE
        if (fits(MAX_SCALE, widthDp, heightDp, measurer)) return MAX_SCALE
        var low = MIN_SCALE
        var high = MAX_SCALE
        repeat(16) {
            val mid = (low + high) / 2f
            if (fits(mid, widthDp, heightDp, measurer)) low = mid else high = mid
        }
        return low
    }
}
