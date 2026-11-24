package com.example.namazvakti.domain.policy

import com.example.namazvakti.domain.model.DiyanetDistricts
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

object NearestDistrictFinder {
    const val MAX_DISTANCE_KM = 60.0
    private const val EARTH_RADIUS_KM = 6_371.0

    data class Match(
        val district: DiyanetDistricts.District,
        val distanceKm: Double
    )

    fun find(latitude: Double, longitude: Double): Match? {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) {
            return null
        }

        val nearest = DiyanetDistricts.districts.asSequence()
            .mapNotNull { district ->
                val districtLatitude = district.latitude ?: return@mapNotNull null
                val districtLongitude = district.longitude ?: return@mapNotNull null
                if (!districtLatitude.isFinite() || districtLatitude !in -90.0..90.0 ||
                    !districtLongitude.isFinite() || districtLongitude !in -180.0..180.0
                ) {
                    return@mapNotNull null
                }
                Match(
                    district = district,
                    distanceKm = distanceKm(latitude, longitude, districtLatitude, districtLongitude)
                )
            }
            .minByOrNull(Match::distanceKm)
            ?: return null

        return nearest.takeIf { it.distanceKm <= MAX_DISTANCE_KM }
    }

    private fun distanceKm(
        latitude: Double,
        longitude: Double,
        otherLatitude: Double,
        otherLongitude: Double
    ): Double {
        val latitudeRadians = Math.toRadians(latitude)
        val otherLatitudeRadians = Math.toRadians(otherLatitude)
        val latitudeDelta = Math.toRadians(otherLatitude - latitude)
        val longitudeDelta = Math.toRadians(otherLongitude - longitude)
        val haversine = sinSquared(latitudeDelta / 2.0) +
            cos(latitudeRadians) * cos(otherLatitudeRadians) * sinSquared(longitudeDelta / 2.0)
        val centralAngle = 2.0 * atan2(sqrt(haversine.coerceIn(0.0, 1.0)), sqrt((1.0 - haversine).coerceAtLeast(0.0)))
        return EARTH_RADIUS_KM * centralAngle
    }

    private fun sinSquared(value: Double): Double {
        val sine = kotlin.math.sin(value)
        return sine * sine
    }
}
