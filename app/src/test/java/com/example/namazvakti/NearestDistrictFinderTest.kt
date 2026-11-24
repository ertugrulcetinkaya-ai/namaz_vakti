package com.example.namazvakti

import com.example.namazvakti.domain.policy.NearestDistrictFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearestDistrictFinderTest {
    @Test
    fun kizilayFindsNearestCoordinateBackedAnkaraDistrictInDiyanetTable() {
        val result = NearestDistrictFinder.find(39.9208, 32.8541)

        assertNotNull(result)
        assertEquals("ANKARA", result!!.district.provinceName)
        assertEquals("ELMADAĞ", result.district.name)
        assertEquals(31.3, result.distanceKm, 0.1)
    }

    @Test
    fun kartalCoastFindsKartal() {
        val result = NearestDistrictFinder.find(40.888, 29.185)

        assertNotNull(result)
        assertEquals("KARTAL", result!!.district.name)
        assertEquals(3.0, result.distanceKm, 1.0)
    }

    @Test
    fun aegeanSeaHasNoDistrictWithinSixtyKilometers() {
        assertNull(NearestDistrictFinder.find(38.5, 25.5))
    }

    @Test
    fun invalidCoordinatesHaveNoMatch() {
        assertNull(NearestDistrictFinder.find(Double.NaN, 29.0))
        assertNull(NearestDistrictFinder.find(91.0, 29.0))
    }
}
