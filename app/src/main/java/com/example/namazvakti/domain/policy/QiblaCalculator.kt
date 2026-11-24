package com.example.namazvakti.domain.policy

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class QiblaResult(
    val bearingDegrees: Double,
    val distanceKm: Double
)

/** Spherical great-circle direction and distance from a point to the Kaaba. */
object QiblaCalculator {
    const val KAABA_LATITUDE = 21.422487
    const val KAABA_LONGITUDE = 39.826206

    private const val EARTH_MEAN_RADIUS_KM = 6_371.0088

    fun calculate(latitude: Double, longitude: Double): QiblaResult {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be finite and between -90 and 90 degrees"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be finite and between -180 and 180 degrees"
        }

        val latitudeRadians = Math.toRadians(latitude)
        val kaabaLatitudeRadians = Math.toRadians(KAABA_LATITUDE)
        val longitudeDifference = Math.toRadians(KAABA_LONGITUDE - longitude)

        val y = sin(longitudeDifference) * cos(kaabaLatitudeRadians)
        val x = cos(latitudeRadians) * sin(kaabaLatitudeRadians) -
            sin(latitudeRadians) * cos(kaabaLatitudeRadians) * cos(longitudeDifference)
        val bearingDegrees = normalizeDegrees(Math.toDegrees(atan2(y, x)))

        val latitudeDifference = kaabaLatitudeRadians - latitudeRadians
        val haversine = (
            sin(latitudeDifference / 2.0) * sin(latitudeDifference / 2.0) +
                cos(latitudeRadians) * cos(kaabaLatitudeRadians) *
                sin(longitudeDifference / 2.0) * sin(longitudeDifference / 2.0)
            ).coerceIn(0.0, 1.0)
        val centralAngle = 2.0 * atan2(sqrt(haversine), sqrt(1.0 - haversine))

        return QiblaResult(
            bearingDegrees = bearingDegrees,
            distanceKm = EARTH_MEAN_RADIUS_KM * centralAngle
        )
    }

    private fun normalizeDegrees(degrees: Double): Double = (degrees % 360.0 + 360.0) % 360.0
}
