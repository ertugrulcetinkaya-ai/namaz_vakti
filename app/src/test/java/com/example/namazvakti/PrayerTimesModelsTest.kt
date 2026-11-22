package com.example.namazvakti

import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.policy.PrayerBoundaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import java.time.Clock
import java.time.ZonedDateTime

class PrayerTimesModelsTest {
    @Test
    fun cacheKeyAndFromCacheKeyRoundTripForCentralAndIstanbulDistrict() {
        val province = requireNotNull(DiyanetDistricts.forCity("Istanbul", "Turkey"))
        val district = DiyanetDistricts.districts.first {
            it.provinceId == province.id && it.name == "MALTEPE"
        }
        val centralLocation = PrayerLocation(
            city = "Istanbul",
            country = "Turkey",
            diyanetDistrictId = province.centralDistrictId
        )
        val districtLocation = PrayerLocation(
            city = "Istanbul",
            country = "Turkey",
            diyanetDistrictId = district.id,
            districtName = district.name
        )

        assertEquals("Istanbul", centralLocation.cacheKey)
        assertEquals("Istanbul" to null, PrayerLocation.fromCacheKey(centralLocation.cacheKey))
        assertEquals("Istanbul#${district.id}", districtLocation.cacheKey)
        assertEquals("Istanbul" to district.id, PrayerLocation.fromCacheKey(districtLocation.cacheKey))
    }

    @Test
    fun districtLocationsDoNotMatchTheSameProvinceCache() {
        val province = requireNotNull(DiyanetDistricts.forCity("Istanbul", "Turkey"))
        val maltepe = DiyanetDistricts.districts.first {
            it.provinceId == province.id && it.name == "MALTEPE"
        }
        val kartal = DiyanetDistricts.districts.first {
            it.provinceId == province.id && it.name == "KARTAL"
        }
        val base = sampleCache().copy(
            location = PrayerLocation("Istanbul", "Turkey", "İSTANBUL")
        )
        val maltepeCache = base.copy(
            location = base.location.copy(diyanetDistrictId = maltepe.id, districtName = maltepe.name)
        )
        val kartalLocation = base.location.copy(diyanetDistrictId = kartal.id, districtName = kartal.name)

        assertFalse(maltepeCache.matches(base.date, kartalLocation, base.settings))
    }

    @Test
    fun provinceCenterFallbackRequiresAlAdhanAndASelectedDistrict() {
        val province = requireNotNull(DiyanetDistricts.forCity("Istanbul", "Turkey"))
        val district = DiyanetDistricts.districts.first {
            it.provinceId == province.id && it.name == "MALTEPE"
        }
        val selectedDistrict = PrayerLocation(
            city = "Istanbul",
            country = "Turkey",
            displayCity = "İSTANBUL",
            latitude = district.latitude,
            longitude = district.longitude,
            diyanetDistrictId = district.id,
            districtName = district.name
        )
        val districtWithoutCoordinates = selectedDistrict.copy(
            diyanetDistrictId = "unmapped-district",
            districtName = "UNKNOWN"
        )
        val central = PrayerLocation("Istanbul", "Turkey", "İSTANBUL")
        val cache = sampleCache()

        assertFalse(cache.copy(source = PrayerDataSource.DIYANET, location = selectedDistrict)
            .usesProvinceCenterFallback)
        assertFalse(cache.copy(source = PrayerDataSource.ALADHAN, location = central)
            .usesProvinceCenterFallback)
        assertFalse(cache.copy(source = PrayerDataSource.ALADHAN, location = selectedDistrict)
            .usesProvinceCenterFallback)
        assertTrue(cache.copy(source = PrayerDataSource.ALADHAN, location = districtWithoutCoordinates)
            .usesProvinceCenterFallback)
    }

    @Test
    fun displayNameUsesDistrictForNonCentralLocationAndProvinceForCentral() {
        val province = requireNotNull(DiyanetDistricts.forCity("Istanbul", "Turkey"))
        val maltepe = DiyanetDistricts.districts.first {
            it.provinceId == province.id && it.name == "MALTEPE"
        }
        val central = PrayerLocation("Istanbul", "Turkey", "İSTANBUL")
        val district = PrayerLocation(
            city = "Istanbul",
            country = "Turkey",
            displayCity = "İSTANBUL",
            diyanetDistrictId = maltepe.id,
            districtName = maltepe.name
        )

        assertEquals("İSTANBUL", central.displayName)
        assertEquals("MALTEPE, İSTANBUL", district.displayName)
    }

    @Test
    fun defaultsToShafiSchoolForAsrCalculation() {
        assertEquals(
            PrayerCalculationSettings.SCHOOL_SHAFI,
            PrayerCalculationSettings().school
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun prayerTimesRejectDuplicateBoundaries() {
        sampleTimes().copy(asr = LocalTime.of(13, 8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculationSettingsRejectNonPositiveMethod() {
        PrayerCalculationSettings(method = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculationSettingsRejectUnsupportedSchool() {
        PrayerCalculationSettings(school = 2)
    }

    @Test
    fun prayerBoundariesHandleMidnightAndExactPrayerTimes() {
        val times = PrayerTimes(
            fajr = LocalTime.of(4, 12),
            sunrise = LocalTime.of(5, 49),
            dhuhr = LocalTime.of(13, 8),
            asr = LocalTime.of(17, 2),
            maghrib = LocalTime.of(20, 21),
            isha = LocalTime.of(22, 1)
        )

        assertNull(times.nextPrayerBoundary(LocalTime.of(23, 30)))
        assertEquals("Güneş", times.nextPrayerBoundary(LocalTime.of(4, 12))?.name)
        assertEquals("Öğle", times.nextPrayerBoundary(LocalTime.of(5, 49))?.name)
    }

    @Test
    fun nextBoundaryBeforeFajrIsFajr() {
        val times = sampleTimes()

        val boundary = times.nextPrayerBoundary(LocalTime.of(3, 0))

        assertEquals("İmsak", boundary?.name)
        assertEquals(LocalTime.of(4, 12), boundary?.time)
    }

    @Test
    fun schedulerBoundaryTargetsTodayCorrectly() {
        val cache = sampleCache()
        val zone = PrayerTimeProvider.DEFAULT_ZONE

        assertEquals(
            LocalDate.of(2026, 8, 6).atTime(4, 12),
            PrayerBoundaryCalculator.calculateNextBoundary(
                cache, ZonedDateTime.of(2026, 8, 6, 3, 0, 0, 0, zone)
            ).toLocalDateTime()
        )
        assertEquals(
            LocalDate.of(2026, 8, 6).atTime(5, 49),
            PrayerBoundaryCalculator.calculateNextBoundary(
                cache, ZonedDateTime.of(2026, 8, 6, 4, 12, 0, 0, zone)
            ).toLocalDateTime()
        )
    }

    @Test
    fun schedulerUsesMidnightWhenTomorrowCacheExists() {
        val cache = sampleCache()

        assertEquals(
            LocalDate.of(2026, 8, 7).atTime(0, 0, 30),
            PrayerBoundaryCalculator.calculateNextBoundary(
                cache,
                ZonedDateTime.of(2026, 8, 6, 22, 30, 0, 0, PrayerTimeProvider.DEFAULT_ZONE)
            ).toLocalDateTime()
        )
    }

    @Test
    fun schedulerSchedulesMidnightPlusThirtySecondsWhenTomorrowCacheIsMissing() {
        assertEquals(
            LocalDate.of(2026, 8, 7).atTime(0, 0, 30),
            PrayerBoundaryCalculator.calculateNextBoundary(
                sampleCache(),
                ZonedDateTime.of(
                    2026, 8, 6, 22, 30, 0, 0, PrayerTimeProvider.DEFAULT_ZONE
                )
            ).toLocalDateTime()
        )
    }

    @Test
    fun clockProviderMakesDateDeterministic() {
        val provider = PrayerTimeProvider(
            Clock.fixed(Instant.parse("2026-08-06T18:00:00Z"), ZoneId.of("Europe/Istanbul"))
        )

        assertEquals(LocalDate.of(2026, 8, 6), provider.today())
        assertEquals(21, provider.now().hour)
    }

    private fun sampleTimes() = PrayerTimes(
        LocalTime.of(4, 12), LocalTime.of(5, 49), LocalTime.of(13, 8),
        LocalTime.of(17, 2), LocalTime.of(20, 21), LocalTime.of(22, 1)
    )

    private fun sampleCache(
        date: LocalDate = LocalDate.of(2026, 8, 6),
        times: PrayerTimes = sampleTimes()
    ) = CachedPrayerDay(
        date = date,
        location = PrayerLocation("Ankara", "Turkey", "ANKARA"),
        timezone = PrayerTimeProvider.DEFAULT_ZONE,
        settings = PrayerCalculationSettings(),
        prayerTimes = times,
        hijriText = null,
        fetchedAt = Instant.parse("2026-08-06T00:00:00Z")
    )
}
