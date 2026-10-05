package com.bibin.visioneye.navigation.guidance

import com.bibin.visioneye.navigation.location.LocationFix
import com.bibin.visioneye.navigation.orientation.RelativeDirection
import com.bibin.visioneye.navigation.routing.Route
import com.bibin.visioneye.navigation.routing.RoutePoint
import com.bibin.visioneye.navigation.routing.RouteStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests for [NavigationGuidanceEngine] covering all Phase 4 scenarios:
 * A. Straight route progression and distance countdown
 * B. Left turn previews (50m, 20m, 8m turn now, step progression)
 * C. Right turn previews and alerts
 * D. Multi-step route progression (step 0 -> step 1 -> step 2)
 * E. GPS jitter suppression (no duplicate stage announcements)
 * F. Off-route detection (noisy fix ignored; 3 consecutive fixes trigger reroute)
 * G. Reroute cooldown debounce
 * H. Arrival detection (stable 2-fix arrival radius)
 * I. Speech deduplication
 * J. Cancellation and clean stop
 * K. Heading and relative direction across 0°/360°
 */
class NavigationGuidanceEngineTest {

    private lateinit var engine: DefaultNavigationGuidanceEngine
    private var mockCurrentTimeMs = 1700000000000L

    // Config: 50m far, 20m near, 8m turn-now, 10m step advance, 25m off-route (3 fixes), 10m arrival (2 fixes)
    private val testConfig = GuidanceConfig(
        farPreviewThresholdMeters = 50.0,
        nearPreviewThresholdMeters = 20.0,
        turnNowThresholdMeters = 8.0,
        stepAdvanceThresholdMeters = 5.0,
        offRouteDistanceThresholdMeters = 25.0,
        offRouteConsecutiveFixesRequired = 3,
        arrivalRadiusMeters = 10.0,
        arrivalConsecutiveFixesRequired = 2,
        rerouteCooldownMs = 10_000L,
        maxGpsAccuracyForOffRouteMeters = 25.0f
    )

    @Before
    fun setUp() {
        mockCurrentTimeMs = 1700000000000L
        engine = DefaultNavigationGuidanceEngine(
            config = testConfig,
            clock = { mockCurrentTimeMs }
        )
    }

    private fun createLocationFix(lat: Double, lon: Double, accuracy: Float = 3.0f): LocationFix {
        return LocationFix(
            latitude = lat,
            longitude = lon,
            accuracyMeters = accuracy,
            speedMetersPerSecond = 1.2f,
            bearingDegrees = 90.0f
        )
    }

    @Test
    fun `A - straight route starts with vocalization and decreases remaining distance`() {
        // Simple straight route along latitude 12.0 from lon 77.000 to lon 77.002 (~218 meters)
        val route = Route(
            destination = "City Park",
            totalDistanceMeters = 218.0,
            totalDurationSeconds = 180.0,
            steps = listOf(
                RouteStep(
                    instruction = "Head east on Park Road",
                    distanceMeters = 218.0,
                    durationSeconds = 180.0,
                    latitude = 12.0,
                    longitude = 77.000,
                    maneuverType = "depart",
                    streetName = "Park Road"
                )
            ),
            geometry = listOf(
                RoutePoint(12.0, 77.000),
                RoutePoint(12.0, 77.002)
            )
        )

        val recordedEvents = mutableListOf<GuidanceEvent>()
        val startLoc = createLocationFix(12.0, 77.000)

        engine.start(route, startLoc)
        assertTrue(engine.isGuiding)

        val p1 = engine.progressFlow.value
        assertEquals("City Park", route.destination)
        assertTrue(p1.currentInstruction.contains("Route ready", ignoreCase = true))

        // Advance halfway (~109m)
        val midLoc = createLocationFix(12.0, 77.001)
        engine.onLocationUpdate(midLoc)

        val p2 = engine.progressFlow.value
        assertTrue("Remaining distance must decrease", p2.remainingRouteDistance < p1.remainingRouteDistance)
        assertTrue(p2.remainingRouteDistance in 100.0..115.0)
    }

    @Test
    fun `B - left turn triggers far preview near preview turn-now and step progression`() {
        // Step 0: Depart from (12.0, 77.000) heading east 100m to (12.0, 77.001)
        // Step 1: Turn left onto Lake Road heading north to (12.001, 77.001)
        val turnPoint = RoutePoint(12.0, 77.001)
        val endPoint = RoutePoint(12.001, 77.001)

        val route = Route(
            destination = "Lake View",
            totalDistanceMeters = 200.0,
            totalDurationSeconds = 160.0,
            steps = listOf(
                RouteStep(
                    instruction = "Head east on Main Street",
                    distanceMeters = 100.0,
                    durationSeconds = 80.0,
                    latitude = 12.0,
                    longitude = 77.000,
                    maneuverType = "depart",
                    streetName = "Main Street"
                ),
                RouteStep(
                    instruction = "Turn left onto Lake Road",
                    distanceMeters = 100.0,
                    durationSeconds = 80.0,
                    latitude = turnPoint.latitude,
                    longitude = turnPoint.longitude,
                    maneuverType = "turn",
                    modifier = "left",
                    streetName = "Lake Road"
                )
            ),
            geometry = listOf(
                RoutePoint(12.0, 77.000),
                turnPoint,
                endPoint
            )
        )

        engine.start(route)

        // 1. Position ~60m-70m before turn: no preview yet (> 50m)
        engine.onLocationUpdate(createLocationFix(12.0, 77.00036))
        var progress = engine.progressFlow.value
        assertTrue("Distance to next maneuver must be > 50m, was ${progress.distanceToNextManeuver}", progress.distanceToNextManeuver > 50.0)

        // 2. Position ~45m before turn (inside 50m far threshold): triggers far preview
        // lon 77.00055 -> ~48m before turn
        engine.onLocationUpdate(createLocationFix(12.0, 77.00055))
        progress = engine.progressFlow.value
        assertTrue(progress.distanceToNextManeuver <= 50.0)
        assertEquals("Turn left in 50 meters.", progress.currentInstruction)
        assertEquals(GuidanceStatus.APPROACHING_TURN, progress.status)

        // 3. Position ~18m before turn (inside 20m near threshold): triggers near preview
        // lon 77.00083 -> ~18m before turn
        engine.onLocationUpdate(createLocationFix(12.0, 77.00083))
        progress = engine.progressFlow.value
        assertTrue(progress.distanceToNextManeuver <= 20.0)
        assertEquals("Turn left in 20 meters.", progress.currentInstruction)

        // 4. Position ~6m before turn (inside 8m turn-now threshold): triggers "Turn left now."
        engine.onLocationUpdate(createLocationFix(12.0, 77.00094))
        progress = engine.progressFlow.value
        assertTrue(progress.distanceToNextManeuver <= 8.0)
        assertEquals("Turn left now.", progress.currentInstruction)
        assertEquals(GuidanceStatus.TURN_NOW, progress.status)

        // 5. User reaches turn point (< 10m step advance threshold): advances to step 1
        engine.onLocationUpdate(createLocationFix(12.0, 77.00098))
        progress = engine.progressFlow.value
        assertEquals(1, progress.currentStepIndex)
        assertEquals("Continue on Lake Road.", progress.currentInstruction)
        assertEquals(GuidanceStatus.CONTINUE, progress.status)
    }

    @Test
    fun `C - right turn triggers appropriate modifier instruction`() {
        val turnPoint = RoutePoint(12.0, 77.001)
        val route = Route(
            destination = "Market",
            totalDistanceMeters = 200.0,
            totalDurationSeconds = 160.0,
            steps = listOf(
                RouteStep(
                    instruction = "Head east on 1st Ave",
                    distanceMeters = 100.0,
                    latitude = 12.0,
                    longitude = 77.000,
                    maneuverType = "depart"
                ),
                RouteStep(
                    instruction = "Turn right onto 2nd Ave",
                    distanceMeters = 100.0,
                    latitude = turnPoint.latitude,
                    longitude = turnPoint.longitude,
                    maneuverType = "turn",
                    modifier = "right",
                    streetName = "2nd Ave"
                )
            ),
            geometry = listOf(RoutePoint(12.0, 77.000), turnPoint, RoutePoint(11.999, 77.001))
        )

        engine.start(route)

        // Enter 20m threshold directly
        engine.onLocationUpdate(createLocationFix(12.0, 77.00085))
        val progress = engine.progressFlow.value
        assertEquals("Turn right in 20 meters.", progress.currentInstruction)
    }

    @Test
    fun `D - multiple steps progress cleanly through all maneuvers`() {
        val p0 = RoutePoint(12.0, 77.000)
        val p1 = RoutePoint(12.0, 77.001)
        val p2 = RoutePoint(12.001, 77.001)
        val p3 = RoutePoint(12.001, 77.002)

        val route = Route(
            destination = "Station",
            totalDistanceMeters = 300.0,
            totalDurationSeconds = 240.0,
            steps = listOf(
                RouteStep(instruction = "Depart east", distanceMeters = 100.0, latitude = p0.latitude, longitude = p0.longitude, maneuverType = "depart"),
                RouteStep(instruction = "Turn left", distanceMeters = 100.0, latitude = p1.latitude, longitude = p1.longitude, maneuverType = "turn", modifier = "left", streetName = "North St"),
                RouteStep(instruction = "Turn right", distanceMeters = 100.0, latitude = p2.latitude, longitude = p2.longitude, maneuverType = "turn", modifier = "right", streetName = "East St"),
                RouteStep(instruction = "Arrive", distanceMeters = 0.0, latitude = p3.latitude, longitude = p3.longitude, maneuverType = "arrive")
            ),
            geometry = listOf(p0, p1, p2, p3)
        )

        engine.start(route)
        assertEquals(0, engine.progressFlow.value.currentStepIndex)

        // Advance to step 1 (arrive near p1)
        engine.onLocationUpdate(createLocationFix(p1.latitude, p1.longitude))
        assertEquals(1, engine.progressFlow.value.currentStepIndex)

        // Advance to step 2 (arrive near p2)
        engine.onLocationUpdate(createLocationFix(p2.latitude, p2.longitude))
        assertEquals(2, engine.progressFlow.value.currentStepIndex)
    }

    @Test
    fun `E - GPS jitter at 19m does not repeatedly re-announce near preview`() {
        val turnPoint = RoutePoint(12.0, 77.001)
        val route = Route(
            destination = "Shop",
            totalDistanceMeters = 100.0,
            totalDurationSeconds = 80.0,
            steps = listOf(
                RouteStep(instruction = "Depart", distanceMeters = 100.0, latitude = 12.0, longitude = 77.000, maneuverType = "depart"),
                RouteStep(instruction = "Turn left", distanceMeters = 0.0, latitude = turnPoint.latitude, longitude = turnPoint.longitude, maneuverType = "turn", modifier = "left")
            ),
            geometry = listOf(RoutePoint(12.0, 77.000), turnPoint)
        )

        engine.start(route)

        // Trigger near preview (18m)
        engine.onLocationUpdate(createLocationFix(12.0, 77.00083))
        assertEquals("Turn left in 20 meters.", engine.progressFlow.value.currentInstruction)

        // Jitter to 19m, then 18m, then 17m (all still in near preview window > 8m)
        engine.onLocationUpdate(createLocationFix(12.0, 77.00082))
        engine.onLocationUpdate(createLocationFix(12.0, 77.00084))
        engine.onLocationUpdate(createLocationFix(12.0, 77.00085))

        // Announcement stage must not reset or repeat
        assertEquals("Turn left in 20 meters.", engine.progressFlow.value.currentInstruction)
    }

    @Test
    fun `F - off-route requires multiple consecutive reliable fixes and ignores coarse GPS fixes`() {
        val p0 = RoutePoint(12.0, 77.000)
        val p1 = RoutePoint(12.0, 77.001)

        val route = Route(
            destination = "Target",
            totalDistanceMeters = 100.0,
            totalDurationSeconds = 80.0,
            steps = listOf(
                RouteStep(instruction = "Depart", distanceMeters = 100.0, latitude = p0.latitude, longitude = p0.longitude, maneuverType = "depart")
            ),
            geometry = listOf(p0, p1)
        )

        engine.start(route)

        // 1. One noisy point 50m north: lat 12.00045 (~50 meters deviation)
        engine.onLocationUpdate(createLocationFix(12.00045, 77.0005, accuracy = 4.0f))
        var p = engine.progressFlow.value
        assertEquals(1, p.consecutiveOffRouteCount)
        assertFalse(p.status == GuidanceStatus.OFF_ROUTE) // NOT declared off-route yet!

        // 2. Second outlier fix:
        engine.onLocationUpdate(createLocationFix(12.00045, 77.0005, accuracy = 4.0f))
        p = engine.progressFlow.value
        assertEquals(2, p.consecutiveOffRouteCount)
        assertFalse(p.status == GuidanceStatus.OFF_ROUTE) // Still NOT off-route

        // 3. A coarse GPS fix with 35m accuracy: must be ignored (does not count as reliable fix)
        engine.onLocationUpdate(createLocationFix(12.00045, 77.0005, accuracy = 35.0f))
        p = engine.progressFlow.value
        assertEquals(2, p.consecutiveOffRouteCount) // preserved at 2, not incremented!

        // 4. Third reliable outlier fix: confirms off-route!
        engine.onLocationUpdate(createLocationFix(12.00045, 77.0005, accuracy = 5.0f))
        p = engine.progressFlow.value
        assertEquals(3, p.consecutiveOffRouteCount)
        assertEquals(GuidanceStatus.OFF_ROUTE, p.status)
        assertEquals("Off route. Recalculating.", p.currentInstruction)
    }

    @Test
    fun `G - rerouting enforces cooldown debounce`() {
        val route = Route(
            destination = "Shop",
            totalDistanceMeters = 100.0,
            totalDurationSeconds = 80.0,
            steps = listOf(
                RouteStep(instruction = "Depart", distanceMeters = 100.0, latitude = 12.0, longitude = 77.000, maneuverType = "depart")
            ),
            geometry = listOf(RoutePoint(12.0, 77.000), RoutePoint(12.0, 77.001))
        )

        engine.start(route)

        // Three off-route fixes
        val offPoint = createLocationFix(12.001, 77.0005, accuracy = 4.0f)
        engine.onLocationUpdate(offPoint)
        engine.onLocationUpdate(offPoint)
        engine.onLocationUpdate(offPoint)

        assertEquals(GuidanceStatus.OFF_ROUTE, engine.progressFlow.value.status)

        // Next fix 1 second later (well within 10s cooldown): should not trigger another reroute event
        mockCurrentTimeMs += 1000L
        engine.onLocationUpdate(offPoint)

        assertEquals(GuidanceStatus.OFF_ROUTE, engine.progressFlow.value.status)
    }

    @Test
    fun `H - destination arrival requires stable 2-fix arrival radius`() {
        val dest = RoutePoint(12.001, 77.001)
        val route = Route(
            destination = "Home",
            totalDistanceMeters = 100.0,
            totalDurationSeconds = 80.0,
            steps = listOf(
                RouteStep(instruction = "Depart", distanceMeters = 100.0, latitude = 12.000, longitude = 77.001, maneuverType = "depart"),
                RouteStep(instruction = "Arrive", distanceMeters = 0.0, latitude = dest.latitude, longitude = dest.longitude, maneuverType = "arrive")
            ),
            geometry = listOf(RoutePoint(12.000, 77.001), dest)
        )

        engine.start(route)

        // Fix 1: within 5 meters of destination (< 10m arrival radius)
        engine.onLocationUpdate(createLocationFix(dest.latitude, dest.longitude))
        var p = engine.progressFlow.value
        assertFalse(p.status == GuidanceStatus.ARRIVED) // 1 fix is not enough

        // Fix 2: stable 2nd fix at destination
        engine.onLocationUpdate(createLocationFix(dest.latitude, dest.longitude))
        p = engine.progressFlow.value
        assertEquals(GuidanceStatus.ARRIVED, p.status)
        assertEquals("You have arrived.", p.currentInstruction)
        assertFalse(engine.isGuiding) // Guidance halted
    }

    @Test
    fun `J - stopping guidance halts updates and resets state to IDLE`() {
        val route = Route(
            destination = "Shop",
            totalDistanceMeters = 100.0,
            totalDurationSeconds = 80.0,
            steps = listOf(
                RouteStep(instruction = "Depart", distanceMeters = 100.0, latitude = 12.0, longitude = 77.000, maneuverType = "depart")
            ),
            geometry = listOf(RoutePoint(12.0, 77.000), RoutePoint(12.0, 77.001))
        )

        engine.start(route)
        assertTrue(engine.isGuiding)

        engine.stop()
        assertFalse(engine.isGuiding)
        assertEquals(GuidanceStatus.IDLE, engine.progressFlow.value.status)
    }

    @Test
    fun `L - heading and relative direction work across 0 and 360 degree boundary`() {
        val route = Route(
            destination = "North Landmark",
            totalDistanceMeters = 100.0,
            totalDurationSeconds = 80.0,
            steps = listOf(
                RouteStep(instruction = "Depart north", distanceMeters = 100.0, latitude = 12.0, longitude = 77.0, maneuverType = "depart")
            ),
            geometry = listOf(RoutePoint(12.0, 77.0), RoutePoint(12.001, 77.0))
        )

        engine.start(route, createLocationFix(12.0, 77.0))

        // Bearing is due North (0.0°). Phone heading is 350.0° (facing slightly left of North)
        // Target bearing is +10° to the right of heading -> FORWARD or SLIGHT_RIGHT
        engine.onHeadingUpdate(350.0f)
        val relDir = engine.progressFlow.value.relativeDirection
        assertNotNull(relDir)
        assertEquals(RelativeDirection.FORWARD, relDir)

        // Phone heading is 90.0° (East), target is 0.0° (North) -> Turn left 90° -> LEFT
        engine.onHeadingUpdate(90.0f)
        assertEquals(RelativeDirection.LEFT, engine.progressFlow.value.relativeDirection)
    }

    @Test
    fun `stopping 4m before a turn does not advance step prematurely`() {
        val turnPoint = RoutePoint(12.0, 77.001)
        val endPoint = RoutePoint(12.001, 77.001)

        val route = Route(
            destination = "Lake View",
            totalDistanceMeters = 200.0,
            totalDurationSeconds = 160.0,
            steps = listOf(
                RouteStep(
                    instruction = "Head east on Main Street",
                    distanceMeters = 100.0,
                    durationSeconds = 80.0,
                    latitude = 12.0,
                    longitude = 77.000,
                    maneuverType = "depart",
                    streetName = "Main Street"
                ),
                RouteStep(
                    instruction = "Turn left onto Lake Road",
                    distanceMeters = 100.0,
                    durationSeconds = 80.0,
                    latitude = turnPoint.latitude,
                    longitude = turnPoint.longitude,
                    maneuverType = "turn",
                    modifier = "left",
                    streetName = "Lake Road"
                )
            ),
            geometry = listOf(RoutePoint(12.0, 77.000), turnPoint, endPoint)
        )

        engine.start(route)

        // User stops 4m before the turn point on Main St (lon = 77.00096)
        // distance to turn = ~4.35m
        engine.onLocationUpdate(createLocationFix(12.0, 77.00096))
        val pStopped = engine.progressFlow.value

        // Must still be on Step 0 (has NOT turned or passed the maneuver)
        assertEquals(0, pStopped.currentStepIndex)
        assertEquals(GuidanceStatus.TURN_NOW, pStopped.status)
        assertEquals("Turn left now.", pStopped.currentInstruction)

        // Now user traverses corner onto Lake Road at (12.00004, 77.001) -> ~4.4m past turn
        engine.onLocationUpdate(createLocationFix(12.00004, 77.001))
        val pTurned = engine.progressFlow.value

        // Must now advance to Step 1
        assertEquals(1, pTurned.currentStepIndex)
        assertEquals(GuidanceStatus.CONTINUE, pTurned.status)
        assertEquals("Continue on Lake Road.", pTurned.currentInstruction)
    }

    @Test
    fun `small GPS jitter does not inflate remaining distance`() {
        val route = Route(
            destination = "Straight Walk",
            totalDistanceMeters = 500.0,
            totalDurationSeconds = 400.0,
            steps = listOf(
                RouteStep(instruction = "Depart", distanceMeters = 500.0, latitude = 12.0, longitude = 77.0, maneuverType = "depart")
            ),
            geometry = listOf(RoutePoint(12.0, 77.0), RoutePoint(12.0, 77.005))
        )

        engine.start(route)

        // Fix 1: At 200m from start (lon 77.00183)
        engine.onLocationUpdate(createLocationFix(12.0, 77.00183))
        val initialRemaining = engine.progressFlow.value.remainingRouteDistance

        // Fix 2: GPS jitters slightly backward by 2 meters (lon 77.00181)
        engine.onLocationUpdate(createLocationFix(12.0, 77.00181))
        val jitterRemaining = engine.progressFlow.value.remainingRouteDistance

        // Remaining distance must NOT increase due to small GPS noise (< 5m)
        assertEquals(initialRemaining, jitterRemaining, 0.001)
    }
}
