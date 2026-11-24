package com.example.namazvakti

import com.example.namazvakti.domain.policy.AngularLowPassFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class AngularLowPassFilterTest {
    @Test
    fun smoothsAcrossZeroUsingTheShortCompassPath() {
        val filter = AngularLowPassFilter(smoothingFactor = 0.5)

        assertEquals(359.0, filter.add(359.0), 0.0)
        assertEquals(0.0, filter.add(1.0), 0.000001)
    }

    @Test
    fun smoothsInTheReverseDirectionAcrossZero() {
        val filter = AngularLowPassFilter(smoothingFactor = 0.5)

        assertEquals(1.0, filter.add(1.0), 0.0)
        assertEquals(0.0, filter.add(359.0), 0.000001)
    }

    @Test
    fun takesTheShortestPathForLargeAngles() {
        val filter = AngularLowPassFilter(smoothingFactor = 0.5)

        filter.add(10.0)

        assertEquals(0.0, filter.add(350.0), 0.000001)
    }
}
