package com.example.namazvakti

import com.example.namazvakti.domain.model.PrayerLocationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerLocationConfigTest {
    @Test
    fun everyProvinceHasUniqueCoordinatesWithinTurkeyBounds() {
        val cities = PrayerLocationConfig.cityOptions
        val coordinates = cities.mapNotNull { option ->
            val latitude = option.latitude ?: return@mapNotNull null
            val longitude = option.longitude ?: return@mapNotNull null
            latitude to longitude
        }

        assertEquals(81, cities.size)
        assertEquals(81, coordinates.size)
        assertTrue(cities.all { it.latitude != null && it.latitude in 35.8..42.2 })
        assertTrue(cities.all { it.longitude != null && it.longitude in 25.6..44.9 })
        assertEquals(coordinates.size, coordinates.toSet().size)
    }

    @Test
    fun selectedProvinceCoordinatesReferToItsAdministrativeCenter() {
        val hatay = PrayerLocationConfig.optionForCityAndCountry("Hatay", "Turkey")
        val kocaeli = PrayerLocationConfig.optionForCityAndCountry("Kocaeli", "Turkey")
        val sakarya = PrayerLocationConfig.optionForCityAndCountry("Sakarya", "Turkey")

        assertEquals(36.2025, hatay.latitude!!, 0.0)
        assertEquals(36.1606, hatay.longitude!!, 0.0)
        assertEquals(40.7653, kocaeli.latitude!!, 0.0)
        assertEquals(29.9408, kocaeli.longitude!!, 0.0)
        assertEquals(40.7830, sakarya.latitude!!, 0.0)
        assertEquals(30.4000, sakarya.longitude!!, 0.0)
    }

    @Test
    fun unknownCityHasNoCoordinatesForApiFallback() {
        val option = PrayerLocationConfig.optionForCityAndCountry("Baku", "Azerbaijan")

        assertNull(option.latitude)
        assertNull(option.longitude)
    }
}
