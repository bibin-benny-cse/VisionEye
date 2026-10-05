package com.bibin.visioneye.navigation

import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.navigation.location.LocationEngine
import com.bibin.visioneye.navigation.location.LocationFix
import com.bibin.visioneye.navigation.routing.GeocodingResult
import com.bibin.visioneye.navigation.routing.GeocodingService
import com.bibin.visioneye.navigation.routing.PedestrianRoutingService
import com.bibin.visioneye.navigation.routing.Route
import com.bibin.visioneye.navigation.routing.RouteStep
import com.bibin.visioneye.navigation.routing.RoutingResult
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests for [DefaultNavigationController].
 *
 * Verifies:
 * 1. Permission-denied state and guidance suppression.
 * 2. Permission-granted state transition and GPS start.
 * 3. GPS acquisition state while waiting for satellite fix.
 * 4. Location update state, diagnostics emission, and non-repeating acoustic announcements.
 * 5. Clean deactivation and stopping of location updates when leaving NAVIGATION mode.
 * 6. Repeated activation idempotency (preventing duplicate callbacks/listeners).
 * 7. Handling of disabled device location services.
 */
class NavigationControllerTest {

    private val testDispatcher = Dispatchers.Unconfined
    private lateinit var fakeLocationEngine: FakeLocationEngine
    private lateinit var fakeOrientationEngine: com.bibin.visioneye.navigation.orientation.OrientationEngineTest.FakeOrientationEngine
    private lateinit var fakeSpeechController: FakeSpeechController
    private lateinit var fakeGeocodingService: FakeGeocodingService
    private lateinit var fakePedestrianRoutingService: FakePedestrianRoutingService
    private var isLocationServicesEnabled = true
    private var mockCurrentTimeMs = 1700000000000L

    private lateinit var controller: DefaultNavigationController

    @Before
    fun setUp() {
        fakeLocationEngine = FakeLocationEngine()
        fakeOrientationEngine = com.bibin.visioneye.navigation.orientation.OrientationEngineTest.FakeOrientationEngine()
        fakeSpeechController = FakeSpeechController()
        fakeGeocodingService = FakeGeocodingService()
        fakePedestrianRoutingService = FakePedestrianRoutingService()
        isLocationServicesEnabled = true
        mockCurrentTimeMs = 1700000000000L

        controller = DefaultNavigationController(
            context = null,
            locationEngine = fakeLocationEngine,
            orientationEngine = fakeOrientationEngine,
            geocodingService = fakeGeocodingService,
            pedestrianRoutingService = fakePedestrianRoutingService,
            speechController = fakeSpeechController,
            backgroundDispatcher = testDispatcher,
            isLocationServiceEnabledProvider = { isLocationServicesEnabled },
            clock = { mockCurrentTimeMs }
        )
    }


    @Test
    fun `permission denied state updates diagnostics and vocalizes requirement without starting updates`() {
        fakeLocationEngine.setPermission(false)

        controller.activate()

        val diag = controller.diagnostics.value
        assertEquals(GpsStatus.PERMISSION_DENIED, diag.status)
        assertFalse(diag.hasPermission)
        assertNull(diag.locationFix)
        assertFalse(fakeLocationEngine.isTrackingActive)

        val navState = controller.state.value
        assertTrue(navState is NavigationState.AcquiringGps)
        assertFalse((navState as NavigationState.AcquiringGps).hasPermission)

        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("permission", ignoreCase = true) == true
        )
    }

    @Test
    fun `permission granted result starts location acquisition and vocalizes acquiring signal`() {
        fakeLocationEngine.setPermission(false)
        controller.activate()
        assertFalse(fakeLocationEngine.isTrackingActive)

        // Grant permission through UI prompt callback
        fakeLocationEngine.setPermission(true)
        controller.onPermissionResult(granted = true)

        val diag = controller.diagnostics.value
        assertEquals(GpsStatus.ACQUIRING, diag.status)
        assertTrue(diag.hasPermission)
        assertTrue(fakeLocationEngine.isTrackingActive)
        assertEquals(1, fakeLocationEngine.startInvocationCount)

        val navState = controller.state.value
        assertTrue(navState is NavigationState.AcquiringGps)
        assertTrue((navState as NavigationState.AcquiringGps).hasPermission)

        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("Acquiring GPS signal", ignoreCase = true) == true
        )
    }

    @Test
    fun `GPS acquisition state is active while waiting for initial fix`() {
        fakeLocationEngine.setPermission(true)

        controller.activate()

        val diag = controller.diagnostics.value
        assertEquals(GpsStatus.ACQUIRING, diag.status)
        assertTrue(diag.hasPermission)
        assertNull(diag.locationFix)
        assertNull(diag.lastUpdateTimeMs)
        assertTrue(fakeLocationEngine.isTrackingActive)

        val navState = controller.state.value
        assertTrue(navState is NavigationState.AcquiringGps)
        assertNull((navState as NavigationState.AcquiringGps).accuracyMeters)
    }

    @Test
    fun `location update state transitions to ACTIVE and announces fix without repeating on subsequent ticks`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        // 1. Initial valid location fix
        val fix1 = LocationFix(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 4.2f,
            speedMetersPerSecond = 1.2f,
            bearingDegrees = 180.0f
        )
        fakeLocationEngine.emitLocation(fix1)

        val diag1 = controller.diagnostics.value
        assertEquals(GpsStatus.ACTIVE, diag1.status)
        assertNotNull(diag1.locationFix)
        assertEquals(12.9716, diag1.locationFix!!.latitude, 0.0001)
        assertEquals(77.5946, diag1.locationFix!!.longitude, 0.0001)
        assertEquals(4.2f, diag1.locationFix!!.accuracyMeters, 0.01f)
        assertEquals(mockCurrentTimeMs, diag1.lastUpdateTimeMs)

        val navState1 = controller.state.value
        assertTrue(navState1 is NavigationState.AcquiringGps)
        assertEquals(4.2f, (navState1 as NavigationState.AcquiringGps).accuracyMeters!!, 0.01f)

        assertEquals(
            "GPS signal acquired. Accuracy: 4 meters.",
            fakeSpeechController.lastSpokenUtterance
        )
        val announcementCount = fakeSpeechController.spokenUtterances.size

        // 2. Subsequent location fix on periodic 3-second tick
        mockCurrentTimeMs += 3000L
        val fix2 = LocationFix(
            latitude = 12.97165,
            longitude = 77.59462,
            accuracyMeters = 3.8f,
            speedMetersPerSecond = 1.3f,
            bearingDegrees = 182.0f
        )
        fakeLocationEngine.emitLocation(fix2)

        // Telemetry diagnostics must be updated
        val diag2 = controller.diagnostics.value
        assertEquals(12.97165, diag2.locationFix!!.latitude, 0.0001)
        assertEquals(mockCurrentTimeMs, diag2.lastUpdateTimeMs)

        // Spoken announcement must NOT be repeated
        assertEquals(announcementCount, fakeSpeechController.spokenUtterances.size)
    }

    @Test
    fun `leaving NAVIGATION stops location updates and releases audio`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()
        assertTrue(fakeLocationEngine.isTrackingActive)

        // Deactivate explicitly
        controller.deactivate()

        assertFalse(fakeLocationEngine.isTrackingActive)
        assertEquals(1, fakeLocationEngine.stopInvocationCount)
        assertEquals(NavigationState.Inactive, controller.state.value)
        assertEquals(1, fakeSpeechController.stopInvocationCount)

        // Test deactivation triggered via ModeManager transition
        controller.activate()
        assertTrue(fakeLocationEngine.isTrackingActive)

        controller.onModeChanged(newMode = VisionMode.NAVIGATE, previousMode = VisionMode.NAVIGATION)
        assertFalse(fakeLocationEngine.isTrackingActive)
        assertEquals(2, fakeLocationEngine.stopInvocationCount)
    }

    @Test
    fun `repeated NAVIGATION activation does not create duplicate location callbacks`() {
        fakeLocationEngine.setPermission(true)

        controller.activate()
        controller.activate()
        controller.activate()

        assertEquals(1, fakeLocationEngine.startInvocationCount)
        assertTrue(fakeLocationEngine.isTrackingActive)
    }

    @Test
    fun `location services disabled sets status UNAVAILABLE and vocalizes instructions`() {
        isLocationServicesEnabled = false
        fakeLocationEngine.setPermission(true)

        controller.activate()

        val diag = controller.diagnostics.value
        assertEquals(GpsStatus.UNAVAILABLE, diag.status)
        assertFalse(diag.isLocationServiceEnabled)
        assertFalse(fakeLocationEngine.isTrackingActive)

        val navState = controller.state.value
        assertTrue(navState is NavigationState.Error)

        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("Location services are disabled", ignoreCase = true) == true
        )
    }

    @Test
    fun `compass heading and cardinal direction updates are reflected in diagnostics`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        fakeOrientationEngine.emitHeading(123.0f)

        val diag = controller.diagnostics.value
        assertEquals(123.0f, diag.headingDegrees!!, 0.01f)
        assertEquals("SE", diag.cardinalDirection)
        assertEquals(com.bibin.visioneye.navigation.orientation.CompassStatus.ACTIVE, diag.compassStatus)
    }

    @Test
    fun `leaving NAVIGATION stops orientation updates`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()
        assertTrue(fakeOrientationEngine.isRunning)

        controller.deactivate()
        assertFalse(fakeOrientationEngine.isRunning)
        assertEquals(1, fakeOrientationEngine.stopCount)

        // Verify via ModeManager transition
        controller.activate()
        assertTrue(fakeOrientationEngine.isRunning)

        controller.onModeChanged(newMode = VisionMode.NAVIGATE, previousMode = VisionMode.NAVIGATION)
        assertFalse(fakeOrientationEngine.isRunning)
        assertEquals(2, fakeOrientationEngine.stopCount)
    }

    @Test
    fun `re-entering NAVIGATION does not create duplicate sensor listeners`() {
        fakeLocationEngine.setPermission(true)

        controller.activate()
        controller.activate()
        controller.activate()

        assertEquals(1, fakeOrientationEngine.startCount)
        assertTrue(fakeOrientationEngine.isRunning)
    }

    @Test
    fun `compass sensor unavailable sets diagnostic status and vocalizes warning once`() {
        fakeLocationEngine.setPermission(true)
        fakeOrientationEngine.setAvailable(false)

        controller.activate()

        val diag = controller.diagnostics.value
        assertEquals(com.bibin.visioneye.navigation.orientation.CompassStatus.UNAVAILABLE, diag.compassStatus)
        assertNull(diag.headingDegrees)

        val compassUtterances = fakeSpeechController.spokenUtterances.filter {
            it.first.contains("Compass sensor unavailable", ignoreCase = true)
        }
        assertEquals(1, compassUtterances.size)
    }

    @Test
    fun `searchDestinationAndRoute starts geocoding, fetches route, and announces state transitions`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        val fix = LocationFix(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 5.0f
        )
        fakeLocationEngine.emitLocation(fix)

        controller.searchDestinationAndRoute("City Library")

        // Diagnostics must expose loaded route summary
        val diag = controller.diagnostics.value
        assertEquals(RouteSearchState.ROUTE_READY, diag.routeState)
        assertNotNull(diag.currentRoute)
        assertEquals("City Library", diag.currentRoute!!.destination)
        assertEquals(540.0, diag.currentRoute!!.totalDistanceMeters, 0.01)
        assertEquals(420.0, diag.currentRoute!!.totalDurationSeconds, 0.01)
        assertEquals(2, diag.currentRoute!!.steps.size)
        assertNull(diag.routeErrorMessage)

        // State must be RouteLoaded or Navigating (guidance auto-started)
        val state = controller.state.value
        assertTrue("Expected RouteLoaded or Navigating, got: $state", state is NavigationState.RouteLoaded || state is NavigationState.Navigating)
        when (state) {
            is NavigationState.RouteLoaded -> assertEquals("City Library", state.route.destination)
            is NavigationState.Navigating -> assertEquals("City Library", state.destination)
            else -> org.junit.Assert.fail("Unexpected state: $state")
        }

        // TTS announcements must contain all 3 state transitions
        val messages = fakeSpeechController.spokenUtterances.map { it.first }
        assertTrue("Must announce finding destination", messages.contains("Finding destination."))
        assertTrue("Must announce getting walking route", messages.contains("Getting walking route."))
        assertTrue("Must announce route ready", messages.any { it.startsWith("Route ready") })
    }

    @Test
    fun `searchDestinationAndRoute without GPS fix informs user and does not start network calls`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()
        // No location fix emitted!

        controller.searchDestinationAndRoute("Central Hospital")

        val diag = controller.diagnostics.value
        assertEquals(RouteSearchState.ROUTE_FAILED, diag.routeState)
        assertNotNull(diag.routeErrorMessage)
        assertNull(diag.currentRoute)

        assertEquals(0, fakeGeocodingService.invocationCount)
        assertEquals(0, fakePedestrianRoutingService.invocationCount)

        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("GPS", ignoreCase = true) == true
        )
    }

    @Test
    fun `blank search query is safely ignored without crashing or network calls`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        controller.searchDestinationAndRoute("")
        controller.searchDestinationAndRoute("   \t  ")

        assertEquals(0, fakeGeocodingService.invocationCount)
        assertEquals(RouteSearchState.IDLE, controller.diagnostics.value.routeState)
    }

    @Test
    fun `geocoding failure sets GeocodingFailure state and announces unable to find destination`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        fakeLocationEngine.emitLocation(
            LocationFix(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5.0f)
        )

        fakeGeocodingService.resultToReturn = GeocodingResult.NoResults

        controller.searchDestinationAndRoute("Nonexistent Wonderland")

        val diag = controller.diagnostics.value
        assertEquals(RouteSearchState.GEOCODING_FAILED, diag.routeState)
        assertNull(diag.currentRoute)

        val state = controller.state.value
        assertTrue(state is NavigationState.GeocodingFailure)

        assertEquals(0, fakePedestrianRoutingService.invocationCount)
        assertEquals("Unable to find that destination.", fakeSpeechController.lastSpokenUtterance)
    }

    @Test
    fun `routing failure sets RouteFailure state and announces unable to get walking route`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        fakeLocationEngine.emitLocation(
            LocationFix(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5.0f)
        )

        fakePedestrianRoutingService.resultToReturn = RoutingResult.NoRouteFound

        controller.searchDestinationAndRoute("Island Across Ocean")

        val diag = controller.diagnostics.value
        assertEquals(RouteSearchState.ROUTE_FAILED, diag.routeState)
        assertNull(diag.currentRoute)

        val state = controller.state.value
        assertTrue(state is NavigationState.RouteFailure)

        assertEquals("Unable to get a walking route.", fakeSpeechController.lastSpokenUtterance)
    }

    @Test
    fun `cancellation clears route and resets state to AcquiringGps`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        fakeLocationEngine.emitLocation(
            LocationFix(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5.0f)
        )

        controller.searchDestinationAndRoute("City Library")
        assertEquals(RouteSearchState.ROUTE_READY, controller.diagnostics.value.routeState)

        controller.cancelRouteSearch()

        val diag = controller.diagnostics.value
        assertEquals(RouteSearchState.IDLE, diag.routeState)
        assertNull(diag.currentRoute)
        assertNull(diag.routeErrorMessage)

        val state = controller.state.value
        assertTrue(state is NavigationState.AcquiringGps)
        assertEquals(1, fakeSpeechController.stopInvocationCount)
    }

    @Test
    fun `leaving NAVIGATION cleans up route jobs and diagnostics`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        fakeLocationEngine.emitLocation(
            LocationFix(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5.0f)
        )

        controller.searchDestinationAndRoute("City Library")
        assertEquals(RouteSearchState.ROUTE_READY, controller.diagnostics.value.routeState)

        // Deactivate when exiting NAVIGATION mode
        controller.deactivate()

        val diag = controller.diagnostics.value
        assertEquals(RouteSearchState.IDLE, diag.routeState)
        assertNull(diag.currentRoute)
        assertNull(diag.routeErrorMessage)
        assertEquals(NavigationState.Inactive, controller.state.value)

        assertFalse(fakeLocationEngine.isTrackingActive)
        assertFalse(fakeOrientationEngine.isRunning)
        assertFalse(controller.internalGuidanceEngine.isGuiding)
    }

    @Test
    fun `stopGuidance halts guidance engine and resets guidance diagnostics while keeping GPS active`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        fakeLocationEngine.emitLocation(
            LocationFix(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5.0f)
        )

        controller.searchDestinationAndRoute("City Library")
        assertTrue("Guidance engine must be guiding", controller.internalGuidanceEngine.isGuiding)

        controller.stopGuidance()

        assertFalse("Guidance engine must stop", controller.internalGuidanceEngine.isGuiding)
        val diag = controller.diagnostics.value
        assertEquals(RouteSearchState.IDLE, diag.routeState)
        assertNull(diag.currentRoute)
        assertEquals(com.bibin.visioneye.navigation.guidance.GuidanceStatus.IDLE, diag.guidanceProgress.status)

        // GPS must remain active
        assertTrue(fakeLocationEngine.isTrackingActive)
        assertTrue(controller.state.value is NavigationState.AcquiringGps)
    }

    @Test
    fun `location fix updates feed into guidance engine and progress is emitted`() {
        fakeLocationEngine.setPermission(true)
        controller.activate()

        fakeLocationEngine.emitLocation(
            LocationFix(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5.0f)
        )

        controller.searchDestinationAndRoute("City Library")
        assertTrue(controller.internalGuidanceEngine.isGuiding)

        // Feed another location
        val fix2 = LocationFix(latitude = 12.9720, longitude = 77.5946, accuracyMeters = 3.0f)
        fakeLocationEngine.emitLocation(fix2)

        val diag = controller.diagnostics.value
        assertTrue("Total steps should match", diag.guidanceProgress.totalSteps == 2)
        assertNotNull(diag.guidanceProgress.currentInstruction)
    }

    /**
     * Fake [LocationEngine] for hardware-independent unit testing.
     */
    private class FakeLocationEngine(
        private var permissionGranted: Boolean = true
    ) : LocationEngine {

        private val _locationFlow = MutableStateFlow<LocationFix?>(null)
        override val locationFlow: StateFlow<LocationFix?> = _locationFlow.asStateFlow()

        var isTrackingActive: Boolean = false
            private set
        var startInvocationCount: Int = 0
            private set
        var stopInvocationCount: Int = 0
            private set

        fun setPermission(granted: Boolean) {
            permissionGranted = granted
        }

        fun emitLocation(fix: LocationFix) {
            if (isTrackingActive && permissionGranted) {
                _locationFlow.value = fix
            }
        }

        override fun hasPermission(): Boolean = permissionGranted

        override fun startLocationUpdates() {
            if (isTrackingActive) return
            startInvocationCount++
            if (!hasPermission()) return
            isTrackingActive = true
        }

        override fun stopLocationUpdates() {
            if (!isTrackingActive) return
            stopInvocationCount++
            isTrackingActive = false
        }
    }

    /**
     * Fake [SpeechController] to record vocalized utterances.
     */
    private class FakeSpeechController : SpeechController {
        val spokenUtterances = mutableListOf<Pair<String, SpeechPriority>>()
        var lastSpokenUtterance: String? = null
            private set
        var stopInvocationCount: Int = 0
            private set

        override fun speak(utterance: String, priority: SpeechPriority) {
            spokenUtterances.add(utterance to priority)
            lastSpokenUtterance = utterance
        }

        override fun stop() {
            stopInvocationCount++
        }
    }

    /**
     * Fake [GeocodingService] for deterministic testing of destination resolution.
     */
    private class FakeGeocodingService : GeocodingService {
        var resultToReturn: GeocodingResult = GeocodingResult.Success(
            displayName = "City Library",
            latitude = 12.9750,
            longitude = 77.6000
        )
        var lastQuery: String? = null
        var invocationCount: Int = 0

        override suspend fun geocode(query: String): GeocodingResult {
            invocationCount++
            lastQuery = query
            return resultToReturn
        }
    }

    /**
     * Fake [PedestrianRoutingService] for deterministic testing of pedestrian route calculation.
     */
    private class FakePedestrianRoutingService : PedestrianRoutingService {
        var resultToReturn: RoutingResult = RoutingResult.Success(
            Route(
                destination = "City Library",
                totalDistanceMeters = 540.0,
                totalDurationSeconds = 420.0,
                steps = listOf(
                    RouteStep(
                        instruction = "Head north on MG Road",
                        distanceMeters = 200.0,
                        durationSeconds = 150.0,
                        latitude = 12.9716,
                        longitude = 77.5946,
                        maneuverType = "depart"
                    ),
                    RouteStep(
                        instruction = "Turn right onto Brigade Road",
                        distanceMeters = 340.0,
                        durationSeconds = 270.0,
                        latitude = 12.9734,
                        longitude = 77.5946,
                        maneuverType = "turn",
                        modifier = "right"
                    )
                )
            )
        )
        var invocationCount: Int = 0

        override suspend fun calculateRoute(
            startLat: Double,
            startLon: Double,
            destLat: Double,
            destLon: Double,
            destinationName: String
        ): RoutingResult {
            invocationCount++
            return resultToReturn
        }
    }
}

