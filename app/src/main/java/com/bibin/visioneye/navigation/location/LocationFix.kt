package com.bibin.visioneye.navigation.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Immutable representation of a validated geographic location fix.
 *
 * Designed for outdoor pedestrian navigation and wayfinding assistance.
 * All coordinates and accuracy metrics are strictly validated upon instantiation.
 *
 * @property latitude Geographic latitude in degrees, valid within [-90.0, 90.0].
 * @property longitude Geographic longitude in degrees, valid within [-180.0, 180.0].
 * @property accuracyMeters Estimated 1-sigma horizontal accuracy radius in meters, non-negative (>= 0.0f).
 * @property bearingDegrees Horizontal direction of travel in degrees [0.0 .. 360.0), or null if unavailable.
 * @property speedMetersPerSecond Instantaneous speed over ground in meters per second, or null if unavailable.
 * @property timestampMillis Epoch timestamp in milliseconds when this fix was generated.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val bearingDegrees: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    init {
        require(isValidLatitude(latitude)) {
            "Latitude must be between -90.0 and 90.0 degrees, but was $latitude"
        }
        require(isValidLongitude(longitude)) {
            "Longitude must be between -180.0 and 180.0 degrees, but was $longitude"
        }
        require(isValidAccuracy(accuracyMeters)) {
            "Accuracy must not be negative, but was $accuracyMeters"
        }
    }

    /**
     * Calculates the great-circle distance to [other] in meters using the Haversine formula.
     */
    fun distanceTo(other: LocationFix): Double {
        return haversineDistanceMeters(
            lat1 = this.latitude,
            lon1 = this.longitude,
            lat2 = other.latitude,
            lon2 = other.longitude
        )
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0

        fun isValidLatitude(lat: Double): Boolean = !lat.isNaN() && lat in -90.0..90.0
        fun isValidLongitude(lon: Double): Boolean = !lon.isNaN() && lon in -180.0..180.0
        fun isValidAccuracy(accuracy: Float): Boolean = !accuracy.isNaN() && accuracy >= 0.0f

        /**
         * Computes the great-circle distance between two coordinate pairs in meters using the Haversine formula.
         *
         * @param lat1 Latitude of the start point in decimal degrees.
         * @param lon1 Longitude of the start point in decimal degrees.
         * @param lat2 Latitude of the destination point in decimal degrees.
         * @param lon2 Longitude of the destination point in decimal degrees.
         * @return Great-circle distance in meters.
         */
        fun haversineDistanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)

            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(rLat1) * cos(rLat2) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }
    }
}
