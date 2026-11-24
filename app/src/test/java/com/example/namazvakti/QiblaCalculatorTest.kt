package com.example.namazvakti

import com.example.namazvakti.domain.policy.QiblaCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaCalculatorTest {
    @Test
    fun calculatesBearingsAgainstIndependentGreatCircleReferences() {
        // Reference checks use the same coordinates and great-circle formula documented at
        // https://qiblaweb.com/en/qibla-direction/tr/ankara and the published Van bearing at
        // https://muflihinpro.com/en/qibla/turkiye/van/; the expected coordinates below are
        // independently evaluated with the spherical initial-bearing equation.
        val cases = listOf(
            Triple("Ankara", 39.93, 32.86) to 160.17,
            Triple("Istanbul", 41.0082, 28.9784) to 151.62,
            Triple("Van", 38.4942, 43.38) to 191.16
        )

        cases.forEach { (place, expectedBearing) ->
            val result = QiblaCalculator.calculate(place.second, place.third)
            assertEquals(place.first, expectedBearing, result.bearingDegrees, 0.5)
            assertTrue("${place.first} bearing must be normalized", result.bearingDegrees in 0.0..<360.0)
        }
    }

    @Test
    fun calculatesHaversineDistanceToKaaba() {
        val cases = listOf(
            Triple(39.93, 32.86, 2_161.2),
            Triple(41.0082, 28.9784, 2_405.1),
            Triple(38.4942, 43.38, 1_928.5)
        )

        cases.forEach { (latitude, longitude, expectedDistanceKm) ->
            assertEquals(
                expectedDistanceKm,
                QiblaCalculator.calculate(latitude, longitude).distanceKm,
                3.0
            )
        }
    }

    @Test
    fun locationAtKaabaHasZeroDistanceAndNormalizedBearing() {
        val result = QiblaCalculator.calculate(
            QiblaCalculator.KAABA_LATITUDE,
            QiblaCalculator.KAABA_LONGITUDE
        )

        assertEquals(0.0, result.distanceKm, 0.000001)
        assertTrue(result.bearingDegrees in 0.0..<360.0)
    }
}
