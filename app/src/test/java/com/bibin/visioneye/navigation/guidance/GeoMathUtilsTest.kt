package com.bibin.visioneye.navigation.guidance

import com.bibin.visioneye.navigation.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic unit tests for geographic calculation utilities in [GeoMathUtils].
 */
class GeoMathUtilsTest {

    @Test
    fun `distance between identical points is zero`() {
        val dist = GeoMathUtils.distanceMeters(12.9716, 77.5946, 12.9716, 77.5946)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun `distance calculation matches known geographic separation`() {
        // Points separated by approximately 111.32 km (1 degree latitude)
        val dist = GeoMathUtils.distanceMeters(12.0, 77.0, 13.0, 77.0)
        assertEquals(111195.0, dist, 200.0) // within 0.2% precision
    }

    @Test
    fun `bearing calculations produce correct cardinal headings`() {
        // Due North
        val north = GeoMathUtils.bearingDegrees(12.0, 77.0, 13.0, 77.0)
        assertEquals(0.0f, north, 0.5f)

        // Due East
        val east = GeoMathUtils.bearingDegrees(12.0, 77.0, 12.0, 78.0)
        assertEquals(90.0f, east, 0.5f)

        // Due South
        val south = GeoMathUtils.bearingDegrees(13.0, 77.0, 12.0, 77.0)
        assertEquals(180.0f, south, 0.5f)

        // Due West
        val west = GeoMathUtils.bearingDegrees(12.0, 78.0, 12.0, 77.0)
        assertEquals(270.0f, west, 0.5f)
    }

    @Test
    fun `distanceToSegment computes perpendicular and endpoint clamp distances accurately`() {
        // Segment along latitude 12.0 from lon 77.0 to 77.01 (~1088m)
        val startLat = 12.0
        val startLon = 77.0
        val endLat = 12.0
        val endLon = 77.01

        // Point directly on segment
        val onSegment = GeoMathUtils.distanceToSegmentMeters(12.0, 77.005, startLat, startLon, endLat, endLon)
        assertEquals(0.0, onSegment, 0.5)

        // Point offset North of segment midpoint
        val offsetDist = GeoMathUtils.distanceToSegmentMeters(12.0001, 77.005, startLat, startLon, endLat, endLon)
        val expectedDist = GeoMathUtils.distanceMeters(12.0, 77.005, 12.0001, 77.005)
        assertEquals(expectedDist, offsetDist, 1.0)

        // Point beyond end point clamps to end point
        val beyondDist = GeoMathUtils.distanceToSegmentMeters(12.0, 77.02, startLat, startLon, endLat, endLon)
        val expectedBeyond = GeoMathUtils.distanceMeters(12.0, 77.02, endLat, endLon)
        assertEquals(expectedBeyond, beyondDist, 1.0)
    }

    @Test
    fun `distanceToPolyline evaluates minimum across all segments`() {
        val polyline = listOf(
            RoutePoint(12.0, 77.0),
            RoutePoint(12.0, 77.01),
            RoutePoint(12.01, 77.01)
        )

        // Point near segment 1
        val dist1 = GeoMathUtils.distanceToPolylineMeters(12.0001, 77.005, polyline)
        assertTrue(dist1 < 20.0)

        // Point near segment 2
        val dist2 = GeoMathUtils.distanceToPolylineMeters(12.005, 77.0101, polyline)
        assertTrue(dist2 < 20.0)
    }

    @Test
    fun `remainingDistanceAlongPolyline decreases as user advances`() {
        val polyline = listOf(
            RoutePoint(12.0, 77.0),
            RoutePoint(12.0, 77.01),
            RoutePoint(12.01, 77.01)
        )

        val remStart = GeoMathUtils.remainingDistanceAlongPolylineMeters(12.0, 77.0, polyline)
        val remMid = GeoMathUtils.remainingDistanceAlongPolylineMeters(12.0, 77.005, polyline)
        val remEnd = GeoMathUtils.remainingDistanceAlongPolylineMeters(12.01, 77.01, polyline)

        assertTrue(remStart > remMid)
        assertTrue(remMid > remEnd)
        assertEquals(0.0, remEnd, 1.0)
    }

    @Test
    fun `haversine distance known equator test and southern hemisphere`() {
        // (0°, 0°) to (0°, 1°) along equator is approx 111.195 km
        val equatorDist = GeoMathUtils.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111195.0, equatorDist, 100.0)

        // Southern hemisphere: (-33.8688, 151.2093) Sydney to (-37.8136, 144.9631) Melbourne
        // Known great circle distance is approx 713 km
        val sydneyMelbourne = GeoMathUtils.distanceMeters(-33.8688, 151.2093, -37.8136, 144.9631)
        assertEquals(713000.0, sydneyMelbourne, 5000.0)

        // Small pedestrian distance: 10 meters along meridian (10 / 111195 = 0.00008993 deg)
        val smallPedestrian = GeoMathUtils.distanceMeters(12.0, 77.0, 12.00008993, 77.0)
        assertEquals(10.0, smallPedestrian, 0.2)
    }

    @Test
    fun `bearing calculations produce correct diagonal headings`() {
        // NE (~45°)
        val ne = GeoMathUtils.bearingDegrees(0.0, 0.0, 1.0, 1.0)
        assertEquals(45.0f, ne, 1.0f)

        // SE (~135°)
        val se = GeoMathUtils.bearingDegrees(1.0, 0.0, 0.0, 1.0)
        assertEquals(135.0f, se, 1.0f)

        // SW (~225°)
        val sw = GeoMathUtils.bearingDegrees(1.0, 1.0, 0.0, 0.0)
        assertEquals(225.0f, sw, 1.0f)

        // NW (~315°)
        val nw = GeoMathUtils.bearingDegrees(0.0, 1.0, 1.0, 0.0)
        assertEquals(315.0f, nw, 1.0f)
    }

    @Test
    fun `hasPassedManeuver correctly differentiates approaching vs traversed turn`() {
        // Maneuver at (12.0, 77.001) where turn goes North to (12.001, 77.001)
        val maneuverLat = 12.0
        val maneuverLon = 77.001
        val nextLegLat = 12.001
        val nextLegLon = 77.001

        // Approaching from West (on incoming street) -> NOT passed
        val approaching = GeoMathUtils.hasPassedManeuver(
            fixLat = 12.0,
            fixLon = 77.00094,
            maneuverLat = maneuverLat,
            maneuverLon = maneuverLon,
            nextLegLat = nextLegLat,
            nextLegLon = nextLegLon
        )
        assertFalse(approaching)

        // Stopped 2m before corner (still on incoming street) -> NOT passed
        val stoppedBefore = GeoMathUtils.hasPassedManeuver(
            fixLat = 12.0,
            fixLon = 77.00098,
            maneuverLat = maneuverLat,
            maneuverLon = maneuverLon,
            nextLegLat = nextLegLat,
            nextLegLon = nextLegLon
        )
        assertFalse(stoppedBefore)

        // Traversed past corner onto North leg -> PASSED
        val passed = GeoMathUtils.hasPassedManeuver(
            fixLat = 12.00005,
            fixLon = 77.001,
            maneuverLat = maneuverLat,
            maneuverLon = maneuverLon,
            nextLegLat = nextLegLat,
            nextLegLon = nextLegLon
        )
        assertTrue(passed)
    }

    @Test
    fun `remainingDistanceAlongPolyline with 4-point synthetic route matches mathematical expectations`() {
        // Route A -> B -> C -> D
        // A = (12.0, 77.0)
        // B = (12.0, 77.001)  (~108.9m East)
        // C = (12.001, 77.001) (~111.2m North)
        // D = (12.001, 77.002) (~108.9m East)
        val pA = RoutePoint(12.0, 77.0)
        val pB = RoutePoint(12.0, 77.001)
        val pC = RoutePoint(12.001, 77.001)
        val pD = RoutePoint(12.001, 77.002)
        val polyline = listOf(pA, pB, pC, pD)

        val lenAB = GeoMathUtils.distanceMeters(pA.latitude, pA.longitude, pB.latitude, pB.longitude)
        val lenBC = GeoMathUtils.distanceMeters(pB.latitude, pB.longitude, pC.latitude, pC.longitude)
        val lenCD = GeoMathUtils.distanceMeters(pC.latitude, pC.longitude, pD.latitude, pD.longitude)
        val totalLen = lenAB + lenBC + lenCD

        // 1. At Start (0%): should equal total length
        val remStart = GeoMathUtils.remainingDistanceAlongPolylineMeters(pA.latitude, pA.longitude, polyline)
        assertEquals(totalLen, remStart, 0.5)

        // 2. At Point B (after completing segment AB): should equal lenBC + lenCD
        val remB = GeoMathUtils.remainingDistanceAlongPolylineMeters(pB.latitude, pB.longitude, polyline)
        assertEquals(lenBC + lenCD, remB, 0.5)

        // 3. Halfway between B and C: should equal 0.5 * lenBC + lenCD
        val midBC = RoutePoint(12.0005, 77.001)
        val remMidBC = GeoMathUtils.remainingDistanceAlongPolylineMeters(midBC.latitude, midBC.longitude, polyline)
        assertEquals(0.5 * lenBC + lenCD, remMidBC, 1.0)

        // 4. At Point C: should equal lenCD
        val remC = GeoMathUtils.remainingDistanceAlongPolylineMeters(pC.latitude, pC.longitude, polyline)
        assertEquals(lenCD, remC, 0.5)

        // 5. At Destination D: should be 0.0m
        val remD = GeoMathUtils.remainingDistanceAlongPolylineMeters(pD.latitude, pD.longitude, polyline)
        assertEquals(0.0, remD, 0.5)

        // 6. Beyond Destination D: clamps to 0.0m
        val remBeyond = GeoMathUtils.remainingDistanceAlongPolylineMeters(12.001, 77.003, polyline)
        assertEquals(0.0, remBeyond, 0.5)
    }
}
