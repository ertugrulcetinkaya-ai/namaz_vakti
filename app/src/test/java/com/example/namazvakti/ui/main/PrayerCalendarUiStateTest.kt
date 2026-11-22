package com.example.namazvakti.ui.main

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerCalendarUiStateTest {
    @Test
    fun imsakiyeHeaderUsesSelectedDistrictDisplayName() {
        val district = DiyanetDistricts.districts.single {
            it.provinceName == "İSTANBUL" && it.name == "KARTAL"
        }
        val location = PrayerLocation(
            city = "Istanbul",
            country = "Turkey",
            displayCity = "İSTANBUL",
            diyanetDistrictId = district.id,
            districtName = district.name
        )

        assertEquals(location.displayName, ImsakiyeUiState(location = location).headerLocationName)
    }

    @Test
    fun monthlyNoticeIsShownWhenAtLeastOneDayUsesProvinceCenterFallback() {
        val centralLocation = PrayerLocation("Istanbul", "Turkey", "İSTANBUL")
        val districtWithoutCoordinates = centralLocation.copy(
            diyanetDistrictId = "unmapped-district",
            districtName = "UNKNOWN"
        )
        val month = YearMonth.of(2026, 11)
        val officialDay = cache(LocalDate.of(2026, 11, 1), centralLocation, PrayerDataSource.DIYANET)
        val fallbackDay = cache(
            LocalDate.of(2026, 11, 2), districtWithoutCoordinates, PrayerDataSource.ALADHAN
        )

        assertFalse(MonthlyUiState(month, days = listOf(officialDay)).hasProvinceCenterFallback)
        assertTrue(MonthlyUiState(month, days = listOf(officialDay, fallbackDay)).hasProvinceCenterFallback)
    }

    private fun cache(
        date: LocalDate,
        location: PrayerLocation,
        source: PrayerDataSource
    ) = CachedPrayerDay(
        date = date,
        location = location,
        timezone = PrayerTimeProvider.DEFAULT_ZONE,
        settings = PrayerCalculationSettings(),
        prayerTimes = PrayerTimes(
            LocalTime.of(4, 12),
            LocalTime.of(5, 49),
            LocalTime.of(13, 8),
            LocalTime.of(17, 2),
            LocalTime.of(20, 21),
            LocalTime.of(22, 1)
        ),
        hijriText = null,
        fetchedAt = Instant.parse("2026-11-01T00:00:00Z"),
        source = source
    )
}
