package com.bibin.visioneye.navigation.guidance

import com.bibin.visioneye.navigation.orientation.HeadingUtils
import com.bibin.visioneye.navigation.routing.RoutePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin geographic mathematical calculations for pedestrian navigation.
 *
 * Implements:
 * - Great-circle geodesic distance (Haversine formula)
 * - Initial forward azimuth / bearing calculation
 * - Orthogonal distance to polyline segments (cross-track distance)
 * - Cumulative remaining path distance estimation
 */
object GeoMathUtils {

    const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates great-circle distance between two geographic coordinates using the Haversine formula.
     *
     * @return Distance in meters.
     */
    fun distanceMeters(
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
        val clampedA = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Determines whether a user location (fixLat, fixLon) has passed beyond the maneuver point
     * (maneuverLat, maneuverLon) in the direction of the next path leg toward (nextLegLat, nextLegLon).
     *
     * Uses vector scalar projection:
     * - Outgoing path vector V = nextLeg - maneuver
     * - User vector U = fix - maneuver
     * - Returns true if U · V > 0 (user has advanced past the maneuver vertex along the outgoing corridor).
     */
    fun hasPassedManeuver(
        fixLat: Double,
        fixLon: Double,
        maneuverLat: Double,
        maneuverLon: Double,
        nextLegLat: Double,
        nextLegLon: Double
    ): Boolean {
        val midLatRad = Math.toRadians(maneuverLat)
        val cosMid = max(1e-6, cos(midLatRad))
        val r = EARTH_RADIUS_METERS

        // Outgoing segment vector V in local Cartesian meters
        val vx = Math.toRadians(nextLegLon - maneuverLon) * cosMid * r
        val vy = Math.toRadians(nextLegLat - maneuverLat) * r

        // User vector U from maneuver point in local Cartesian meters
        val ux = Math.toRadians(fixLon - maneuverLon) * cosMid * r
        val uy = Math.toRadians(fixLat - maneuverLat) * r

        val vLenSq = vx * vx + vy * vy
        if (vLenSq == 0.0) return false

        // Scalar projection along outgoing leg
        val dot = ux * vx + uy * vy
        return dot > 0.0
    }

    /**
     * Calculates initial forward bearing from (lat1, lon1) to (lat2, lon2).
     *
     * @return Azimuth in degrees [0.0f, 360.0f), where 0°=North, 90°=East, 180°=South, 270°=West.
     */
    fun bearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        val degrees = Math.toDegrees(theta).toFloat()
        return HeadingUtils.normalizeHeading(degrees)
    }

    /**
     * Calculates the shortest perpendicular distance in meters from a test point (pointLat, pointLon)
     * to a finite line segment between (startLat, startLon) and (endLat, endLon).
     */
    fun distanceToSegmentMeters(
        pointLat: Double,
        pointLon: Double,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Double {
        val midLatRad = Math.toRadians((startLat + endLat) / 2.0)
        val cosMid = cos(midLatRad)

        // Equirectangular local Cartesian projection in meters
        val radPerMeterLat = 1.0 / EARTH_RADIUS_METERS
        val radPerMeterLon = 1.0 / (EARTH_RADIUS_METERS * cosMid)

        val px = Math.toRadians(pointLon - startLon) / radPerMeterLon
        val py = Math.toRadians(pointLat - startLat) / radPerMeterLat

        val sx = 0.0
        val sy = 0.0

        val ex = Math.toRadians(endLon - startLon) / radPerMeterLon
        val ey = Math.toRadians(endLat - startLat) / radPerMeterLat

        val dx = ex - sx
        val dy = ey - sy
        val lenSq = dx * dx + dy * dy

        if (lenSq == 0.0) {
            return distanceMeters(pointLat, pointLon, startLat, startLon)
        }

        // Project point onto line segment, clamped to [0.0, 1.0]
        val t = max(0.0, min(1.0, (px * dx + py * dy) / lenSq))
        val projX = t * dx
        val projY = t * dy

        val distX = px - projX
        val distY = py - projY
        return sqrt(distX * distX + distY * distY)
    }

    /**
     * Finds the minimum distance from (pointLat, pointLon) to any segment in [polyline].
     *
     * @return Distance in meters. Returns Double.MAX_VALUE if polyline is empty.
     */
    fun distanceToPolylineMeters(
        pointLat: Double,
        pointLon: Double,
        polyline: List<RoutePoint>
    ): Double {
        if (polyline.isEmpty()) return Double.MAX_VALUE
        if (polyline.size == 1) {
            return distanceMeters(pointLat, pointLon, polyline[0].latitude, polyline[0].longitude)
        }

        var minDistance = Double.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val p1 = polyline[i]
            val p2 = polyline[i + 1]
            val dist = distanceToSegmentMeters(
                pointLat, pointLon,
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude
            )
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    /**
     * Calculates the remaining distance in meters from (pointLat, pointLon) along [polyline]
     * to the final destination point.
     *
     * @param pointLat Current user latitude.
     * @param pointLon Current user longitude.
     * @param polyline Ordered list of coordinates from origin to destination.
     * @param searchStartIndex Index in polyline to start checking (avoids backtracking).
     */
    fun remainingDistanceAlongPolylineMeters(
        pointLat: Double,
        pointLon: Double,
        polyline: List<RoutePoint>,
        searchStartIndex: Int = 0
    ): Double {
        if (polyline.isEmpty()) return 0.0
        if (polyline.size == 1) {
            return distanceMeters(pointLat, pointLon, polyline[0].latitude, polyline[0].longitude)
        }

        val startIdx = max(0, min(searchStartIndex, polyline.size - 2))

        // Find nearest segment from searchStartIndex onwards
        var nearestSegIdx = startIdx
        var minSegDist = Double.MAX_VALUE
        var nearestT = 0.0

        for (i in startIdx until polyline.size - 1) {
            val p1 = polyline[i]
            val p2 = polyline[i + 1]

            val midLatRad = Math.toRadians((p1.latitude + p2.latitude) / 2.0)
            val cosMid = cos(midLatRad)
            val radPerMeterLat = 1.0 / EARTH_RADIUS_METERS
            val radPerMeterLon = 1.0 / (EARTH_RADIUS_METERS * cosMid)

            val px = Math.toRadians(pointLon - p1.longitude) / radPerMeterLon
            val py = Math.toRadians(pointLat - p1.latitude) / radPerMeterLat
            val ex = Math.toRadians(p2.longitude - p1.longitude) / radPerMeterLon
            val ey = Math.toRadians(p2.latitude - p1.latitude) / radPerMeterLat

            val dx = ex
            val dy = ey
            val lenSq = dx * dx + dy * dy

            val t = if (lenSq > 0.0) max(0.0, min(1.0, (px * dx + py * dy) / lenSq)) else 0.0
            val distX = px - t * dx
            val distY = py - t * dy
            val dist = sqrt(distX * distX + distY * distY)

            if (dist < minSegDist) {
                minSegDist = dist
                nearestSegIdx = i
                nearestT = t
            }
        }

        // Distance remaining on nearest segment
        val p1 = polyline[nearestSegIdx]
        val p2 = polyline[nearestSegIdx + 1]
        val segTotalLen = distanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        var remaining = segTotalLen * (1.0 - nearestT)

        // Add lengths of all subsequent segments
        for (i in nearestSegIdx + 1 until polyline.size - 1) {
            remaining += distanceMeters(
                polyline[i].latitude, polyline[i].longitude,
                polyline[i + 1].latitude, polyline[i + 1].longitude
            )
        }

        return remaining
    }
}
