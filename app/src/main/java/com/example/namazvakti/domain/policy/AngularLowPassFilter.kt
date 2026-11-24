package com.example.namazvakti.domain.policy

/** Smooths degree measurements along the shortest path around the compass. */
class AngularLowPassFilter(private val smoothingFactor: Double) {
    private var filteredDegrees: Double? = null

    init {
        require(smoothingFactor.isFinite() && smoothingFactor in 0.0..1.0) {
            "Smoothing factor must be between 0 and 1"
        }
    }

    fun add(degrees: Double): Double {
        require(degrees.isFinite()) { "Angle must be finite" }
        val normalized = normalizeDegrees(degrees)
        val previous = filteredDegrees
        val result = if (previous == null) {
            normalized
        } else {
            normalizeDegrees(previous + shortestDifference(previous, normalized) * smoothingFactor)
        }
        filteredDegrees = result
        return result
    }

    fun reset() {
        filteredDegrees = null
    }

    companion object {
        fun shortestDifference(fromDegrees: Double, toDegrees: Double): Double {
            val difference = normalizeDegrees(toDegrees) - normalizeDegrees(fromDegrees)
            return (difference + 540.0) % 360.0 - 180.0
        }

        private fun normalizeDegrees(degrees: Double): Double = (degrees % 360.0 + 360.0) % 360.0
    }
}
