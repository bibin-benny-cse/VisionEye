package com.bibin.visioneye.navigation

import android.content.Context
import android.location.LocationManager
import android.util.Log
import androidx.core.location.LocationManagerCompat
import com.bibin.visioneye.core.mode.ModeManager
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.navigation.guidance.DefaultNavigationGuidanceEngine
import com.bibin.visioneye.navigation.guidance.GuidanceEvent
import com.bibin.visioneye.navigation.guidance.GuidanceProgress
import com.bibin.visioneye.navigation.guidance.GuidanceStatus
import com.bibin.visioneye.navigation.guidance.NavigationGuidanceEngine
import com.bibin.visioneye.navigation.location.LocationEngine
import com.bibin.visioneye.navigation.location.LocationFix
import com.bibin.visioneye.navigation.orientation.CompassStatus
import com.bibin.visioneye.navigation.orientation.HeadingState
import com.bibin.visioneye.navigation.orientation.OrientationEngine
import com.bibin.visioneye.navigation.routing.GeocodingResult
import com.bibin.visioneye.navigation.routing.GeocodingService
import com.bibin.visioneye.navigation.routing.PedestrianRoutingService
import com.bibin.visioneye.navigation.routing.Route
import com.bibin.visioneye.navigation.routing.RoutePoint
import com.bibin.visioneye.navigation.routing.RoutingResult
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Production implementation of [NavigationController] managing outdoor pedestrian GPS,
 * compass orientation, destination geocoding, pedestrian routing, and turn-by-turn guidance.
 *
 * Coordinates:
 * - GPS acquisition via [LocationEngine]
 * - Device physical heading acquisition via [OrientationEngine]
 * - Geocoding destination queries via [GeocodingService]
 * - Walking route retrieval via [PedestrianRoutingService]
 * - Turn-by-turn guidance, maneuver tracking, off-route detection, and arrival via [NavigationGuidanceEngine]
 * - Diagnostic state emission for real-time verification and HUD display
 * - Spoken acoustic announcements via [SpeechController] with strict deduplication
 * - Mode synchronization via [ModeManager] and [com.bibin.visioneye.core.contract.ModeAwareComponent]
 *
 * @param context Application context used for checking device location provider status.
 * @param locationEngine Hardware-abstracted location provider.
 * @param orientationEngine Hardware-abstracted orientation provider.
 * @param geocodingService Service for resolving text destinations to coordinates.
 * @param pedestrianRoutingService Service for calculating on-foot routes.
 * @param guidanceEngine Engine for turn-by-turn progress tracking and maneuver instructions.
 * @param speechController Synthesizer for spoken auditory feedback.
 * @param modeManager Central mode coordinator.
 * @param backgroundDispatcher Coroutine dispatcher for flow collection and background arbitration.
 * @param isLocationServiceEnabledProvider Provider checking if system GPS is enabled (injectable for testing).
 * @param clock Time source for deterministic testing.
 */
class DefaultNavigationController(
    private val context: Context? = null,
    val locationEngine: LocationEngine,
    val orientationEngine: OrientationEngine? = null,
    val geocodingService: GeocodingService? = null,
    val pedestrianRoutingService: PedestrianRoutingService? = null,
    val guidanceEngine: NavigationGuidanceEngine? = null,
    val speechController: SpeechController? = null,
    val modeManager: ModeManager? = null,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val isLocationServiceEnabledProvider: (Context?) -> Boolean = { ctx ->
        val lm = ctx?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        lm?.let { LocationManagerCompat.isLocationEnabled(it) } ?: true
    },
    private val clock: () -> Long = { System.currentTimeMillis() }
) : NavigationController {

    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val lock = Any()

    val internalGuidanceEngine: NavigationGuidanceEngine by lazy {
        guidanceEngine ?: DefaultNavigationGuidanceEngine(clock = clock)
    }

    private val _state = MutableStateFlow<NavigationState>(NavigationState.Inactive)
    override val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow(NavigationGpsDiagnostics())
    override val diagnostics: StateFlow<NavigationGpsDiagnostics> = _diagnostics.asStateFlow()

    private var isActive = false
    private var locationCollectorJob: Job? = null
    private var orientationCollectorJob: Job? = null
    private var routeJob: Job? = null
    private var guidanceProgressJob: Job? = null
    private var guidanceEventJob: Job? = null

    private var hasAnnouncedFix = false
    private var lastSpokenMessage: String? = null

    override fun activate() {
        synchronized(lock) {
            if (isActive) {
                Log.d(TAG, "activate ignored: NavigationController is already active.")
                return
            }
            isActive = true
            hasAnnouncedFix = false
            lastSpokenMessage = null

            // Start orientation monitoring
            startOrientationTracking()

            performGpsStart()
        }
    }

    private fun startOrientationTracking() {
        val engine = orientationEngine ?: return
        engine.start()

        orientationCollectorJob?.cancel()
        orientationCollectorJob = scope.launch {
            engine.headingFlow.collect { headingState ->
                onHeadingStateChanged(headingState)
            }
        }
        Log.d(TAG, "Orientation tracking initiated.")
    }

    private fun onHeadingStateChanged(headingState: HeadingState) {
        synchronized(lock) {
            if (!isActive) return

            when (headingState) {
                is HeadingState.Active -> {
                    _diagnostics.value = _diagnostics.value.copy(
                        headingDegrees = headingState.headingDegrees,
                        cardinalDirection = headingState.cardinalDirection,
                        compassStatus = CompassStatus.ACTIVE
                    )
                    internalGuidanceEngine.onHeadingUpdate(headingState.headingDegrees)
                }
                is HeadingState.Unavailable -> {
                    _diagnostics.value = _diagnostics.value.copy(
                        headingDegrees = null,
                        cardinalDirection = null,
                        compassStatus = CompassStatus.UNAVAILABLE
                    )
                    internalGuidanceEngine.onHeadingUpdate(null)
                    speakOnce("Compass sensor unavailable.")
                }
                is HeadingState.Initializing -> {
                    _diagnostics.value = _diagnostics.value.copy(
                        compassStatus = CompassStatus.INITIALIZING
                    )
                }
            }
        }
    }

    private fun performGpsStart() {
        // 1. Validate runtime permission
        if (!locationEngine.hasPermission()) {
            _diagnostics.value = _diagnostics.value.copy(
                hasPermission = false,
                status = GpsStatus.PERMISSION_DENIED,
                errorMessage = "Location permission not granted"
            )
            _state.value = NavigationState.AcquiringGps(hasPermission = false)
            speakOnce("Location permission required for navigation. Please grant access.")
            Log.w(TAG, "Navigation activated but location permission is not granted.")
            return
        }

        // 2. Validate system location services
        val isServiceEnabled = isLocationServiceEnabledProvider(context)
        if (!isServiceEnabled) {
            _diagnostics.value = _diagnostics.value.copy(
                hasPermission = true,
                isLocationServiceEnabled = false,
                status = GpsStatus.UNAVAILABLE,
                errorMessage = "Location services disabled"
            )
            _state.value = NavigationState.Error("Location services disabled on device.")
            speakOnce("Location services are disabled. Please turn on GPS in settings.")
            Log.w(TAG, "Navigation activated but system location services are disabled.")
            return
        }

        // 3. Start location acquisition
        _diagnostics.value = _diagnostics.value.copy(
            hasPermission = true,
            isLocationServiceEnabled = true,
            status = GpsStatus.ACQUIRING
        )
        _state.value = NavigationState.AcquiringGps(hasPermission = true)
        speakOnce("Navigation active. Acquiring GPS signal.")

        locationEngine.startLocationUpdates()

        // 4. Collect location stream
        locationCollectorJob?.cancel()
        locationCollectorJob = scope.launch {
            locationEngine.locationFlow.collect { fix ->
                onLocationFixReceived(fix)
            }
        }
    }

    private fun onLocationFixReceived(fix: LocationFix?) {
        synchronized(lock) {
            if (!isActive || fix == null) return

            _diagnostics.value = _diagnostics.value.copy(
                hasPermission = true,
                status = GpsStatus.ACTIVE,
                locationFix = fix,
                lastUpdateTimeMs = clock(),
                isLocationServiceEnabled = true
            )

            // Feed location to turn-by-turn guidance engine
            internalGuidanceEngine.onLocationUpdate(fix)

            // Only transition high-level state to AcquiringGps if not actively searching/navigating
            val current = _state.value
            if (current is NavigationState.Inactive || current is NavigationState.AcquiringGps) {
                _state.value = NavigationState.AcquiringGps(
                    hasPermission = true,
                    accuracyMeters = fix.accuracyMeters
                )
            }

            if (!hasAnnouncedFix) {
                hasAnnouncedFix = true
                val accuracy = fix.accuracyMeters.toInt()
                speakOnce("GPS signal acquired. Accuracy: $accuracy meters.")
                Log.d(TAG, "Initial GPS fix vocalized with accuracy: $accuracy m")
            }
        }
    }

    override fun searchDestinationAndRoute(query: String) {
        synchronized(lock) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) {
                Log.w(TAG, "searchDestinationAndRoute ignored: query is blank.")
                return
            }

            if (!isActive) {
                activate()
            }

            val fix = _diagnostics.value.locationFix
            if (fix == null) {
                _diagnostics.value = _diagnostics.value.copy(
                    routeState = RouteSearchState.ROUTE_FAILED,
                    routeErrorMessage = "GPS signal required before finding route"
                )
                _state.value = NavigationState.RouteFailure(trimmed, "GPS signal required before finding route")
                speakStateTransition("Waiting for GPS signal before finding route.")
                return
            }

            // Cancel any in-flight route job
            routeJob?.cancel()

            _diagnostics.value = _diagnostics.value.copy(
                routeState = RouteSearchState.GEOCODING,
                currentRoute = null,
                routeErrorMessage = null
            )
            _state.value = NavigationState.GeocodingInProgress(trimmed)
            speakStateTransition("Finding destination.")

            routeJob = scope.launch {
                executeGeocodingAndRouting(trimmed, fix)
            }
        }
    }

    private suspend fun executeGeocodingAndRouting(query: String, origin: LocationFix) {
        val geoService = geocodingService
        if (geoService == null) {
            Log.e(TAG, "GeocodingService not injected.")
            handleGeocodingFailure(query, "Geocoding service unavailable")
            return
        }

        when (val geoResult = geoService.geocode(query)) {
            is GeocodingResult.Success -> {
                Log.d(TAG, "Geocoded '${geoResult.displayName}' at (${geoResult.latitude}, ${geoResult.longitude})")
                handleGeocodingSuccess(geoResult, origin)
            }
            is GeocodingResult.EmptyQuery -> {
                handleGeocodingFailure(query, "Empty query")
            }
            is GeocodingResult.NoResults -> {
                handleGeocodingFailure(query, "Destination not found")
            }
            is GeocodingResult.Error -> {
                handleGeocodingFailure(query, geoResult.message)
            }
        }
    }

    private suspend fun handleGeocodingSuccess(geoResult: GeocodingResult.Success, origin: LocationFix) {
        synchronized(lock) {
            _diagnostics.value = _diagnostics.value.copy(
                routeState = RouteSearchState.ROUTE_LOADING,
                routeErrorMessage = null
            )
            _state.value = NavigationState.RouteLoading(
                destination = geoResult.displayName,
                latitude = geoResult.latitude,
                longitude = geoResult.longitude
            )
        }
        speakStateTransition("Getting walking route.")

        val routeService = pedestrianRoutingService
        if (routeService == null) {
            Log.e(TAG, "PedestrianRoutingService not injected.")
            handleRoutingFailure(geoResult.displayName, "Routing service unavailable")
            return
        }

        when (val routeResult = routeService.calculateRoute(
            startLat = origin.latitude,
            startLon = origin.longitude,
            destLat = geoResult.latitude,
            destLon = geoResult.longitude,
            destinationName = geoResult.displayName
        )) {
            is RoutingResult.Success -> {
                synchronized(lock) {
                    _diagnostics.value = _diagnostics.value.copy(
                        routeState = RouteSearchState.ROUTE_READY,
                        currentRoute = routeResult.route,
                        routeErrorMessage = null
                    )
                    _state.value = NavigationState.RouteLoaded(routeResult.route)
                }
                Log.d(
                    TAG,
                    "Route loaded: ${routeResult.route.totalDistanceMeters}m, " +
                    "${routeResult.route.totalDurationSeconds}s, ${routeResult.route.steps.size} steps"
                )

                // Phase 4: Start turn-by-turn pedestrian guidance
                startTurnByTurnGuidance(routeResult.route)
            }
            is RoutingResult.NoRouteFound -> {
                handleRoutingFailure(geoResult.displayName, "No walkable path found to destination")
            }
            is RoutingResult.Error -> {
                handleRoutingFailure(geoResult.displayName, routeResult.message)
            }
        }
    }

    private fun startTurnByTurnGuidance(route: Route) {
        guidanceProgressJob?.cancel()
        guidanceEventJob?.cancel()

        // Collect continuous guidance progression
        guidanceProgressJob = scope.launch {
            internalGuidanceEngine.progressFlow.collect { progress ->
                onGuidanceProgressReceived(progress, route)
            }
        }

        // Collect guidance audio & reroute events
        guidanceEventJob = scope.launch {
            internalGuidanceEngine.eventFlow.collect { event ->
                onGuidanceEventReceived(event, route)
            }
        }

        // Start guidance engine
        internalGuidanceEngine.start(route, _diagnostics.value.locationFix)
    }

    private fun onGuidanceProgressReceived(progress: GuidanceProgress, route: Route) {
        synchronized(lock) {
            if (!isActive) return

            _diagnostics.value = _diagnostics.value.copy(
                guidanceProgress = progress,
                relativeDirection = progress.relativeDirection
            )

            when (progress.status) {
                GuidanceStatus.ARRIVED -> {
                    _state.value = NavigationState.DestinationReached
                }
                GuidanceStatus.STARTING,
                GuidanceStatus.CONTINUE,
                GuidanceStatus.APPROACHING_TURN,
                GuidanceStatus.TURN_NOW,
                GuidanceStatus.OFF_ROUTE,
                GuidanceStatus.REROUTING -> {
                    _state.value = NavigationState.Navigating(
                        destination = route.destination,
                        distanceRemainingMeters = progress.remainingRouteDistance.toFloat(),
                        currentInstruction = progress.currentInstruction,
                        currentStepIndex = progress.currentStepIndex + 1,
                        totalSteps = progress.totalSteps
                    )
                }
                GuidanceStatus.IDLE, GuidanceStatus.ERROR -> {
                    // Retain existing state
                }
            }
        }
    }

    private fun onGuidanceEventReceived(event: GuidanceEvent, route: Route) {
        when (event) {
            is GuidanceEvent.SpokenInstruction -> {
                speechController?.speak(event.message, event.priority)
            }
            is GuidanceEvent.OffRoute -> {
                Log.w(TAG, "Off-route event: ${event.deviationMeters}m deviation.")
            }
            is GuidanceEvent.RerouteNeeded -> {
                performAutomaticReroute(event.originLat, event.originLon, route)
            }
            is GuidanceEvent.DestinationArrived -> {
                synchronized(lock) {
                    _state.value = NavigationState.DestinationReached
                }
            }
        }
    }

    private fun performAutomaticReroute(originLat: Double, originLon: Double, originalRoute: Route) {
        val destPoint = originalRoute.geometry.lastOrNull()
            ?: originalRoute.steps.lastOrNull()?.let { RoutePoint(it.latitude, it.longitude) }
            ?: return

        routeJob?.cancel()

        _diagnostics.value = _diagnostics.value.copy(
            routeState = RouteSearchState.ROUTE_LOADING,
            guidanceProgress = _diagnostics.value.guidanceProgress.copy(
                status = GuidanceStatus.REROUTING,
                currentInstruction = "Recalculating route..."
            )
        )

        routeJob = scope.launch {
            val routeService = pedestrianRoutingService ?: return@launch
            when (val rerouteResult = routeService.calculateRoute(
                startLat = originLat,
                startLon = originLon,
                destLat = destPoint.latitude,
                destLon = destPoint.longitude,
                destinationName = originalRoute.destination
            )) {
                is RoutingResult.Success -> {
                    synchronized(lock) {
                        _diagnostics.value = _diagnostics.value.copy(
                            routeState = RouteSearchState.ROUTE_READY,
                            currentRoute = rerouteResult.route,
                            routeErrorMessage = null
                        )
                        _state.value = NavigationState.RouteLoaded(rerouteResult.route)
                    }
                    startTurnByTurnGuidance(rerouteResult.route)
                    speechController?.speak("Route updated.", SpeechPriority.HIGH)
                    Log.d(TAG, "Automatic reroute successfully applied.")
                }
                is RoutingResult.NoRouteFound -> {
                    handleRoutingFailure(originalRoute.destination, "No walkable path found during rerouting")
                }
                is RoutingResult.Error -> {
                    handleRoutingFailure(originalRoute.destination, rerouteResult.message)
                }
            }
        }
    }

    private fun handleGeocodingFailure(query: String, errorMessage: String) {
        synchronized(lock) {
            _diagnostics.value = _diagnostics.value.copy(
                routeState = RouteSearchState.GEOCODING_FAILED,
                routeErrorMessage = errorMessage
            )
            _state.value = NavigationState.GeocodingFailure(query, errorMessage)
        }
        speakStateTransition("Unable to find that destination.")
    }

    private fun handleRoutingFailure(destination: String, errorMessage: String) {
        synchronized(lock) {
            _diagnostics.value = _diagnostics.value.copy(
                routeState = RouteSearchState.ROUTE_FAILED,
                routeErrorMessage = errorMessage
            )
            _state.value = NavigationState.RouteFailure(destination, errorMessage)
        }
        speakStateTransition("Unable to get a walking route.")
    }

    override fun cancelRouteSearch() {
        synchronized(lock) {
            routeJob?.cancel()
            routeJob = null

            guidanceProgressJob?.cancel()
            guidanceProgressJob = null

            guidanceEventJob?.cancel()
            guidanceEventJob = null

            internalGuidanceEngine.stop()

            _diagnostics.value = _diagnostics.value.copy(
                routeState = RouteSearchState.IDLE,
                currentRoute = null,
                routeErrorMessage = null,
                guidanceProgress = GuidanceProgress()
            )
            val currentFix = _diagnostics.value.locationFix
            _state.value = NavigationState.AcquiringGps(
                hasPermission = locationEngine.hasPermission(),
                accuracyMeters = currentFix?.accuracyMeters
            )
            lastSpokenMessage = null
            speechController?.stop()
            Log.d(TAG, "Route search and guidance cancelled.")
        }
    }

    override fun deactivate() {
        synchronized(lock) {
            if (!isActive) {
                Log.d(TAG, "deactivate ignored: NavigationController is not active.")
                return
            }
            isActive = false
            hasAnnouncedFix = false
            lastSpokenMessage = null

            routeJob?.cancel()
            routeJob = null

            guidanceProgressJob?.cancel()
            guidanceProgressJob = null

            guidanceEventJob?.cancel()
            guidanceEventJob = null

            internalGuidanceEngine.stop()

            locationCollectorJob?.cancel()
            locationCollectorJob = null

            orientationCollectorJob?.cancel()
            orientationCollectorJob = null

            locationEngine.stopLocationUpdates()
            orientationEngine?.stop()

            _state.value = NavigationState.Inactive
            _diagnostics.value = NavigationGpsDiagnostics(
                hasPermission = locationEngine.hasPermission(),
                status = GpsStatus.ACQUIRING,
                compassStatus = CompassStatus.INITIALIZING,
                routeState = RouteSearchState.IDLE,
                currentRoute = null,
                routeErrorMessage = null,
                guidanceProgress = GuidanceProgress()
            )

            speechController?.stop()
            Log.d(TAG, "NavigationController cleanly deactivated; GPS, compass, routing, and guidance jobs stopped.")
        }
    }

    override fun onPermissionResult(granted: Boolean) {
        synchronized(lock) {
            if (granted) {
                Log.d(TAG, "onPermissionResult: Location permission granted.")
                if (isActive) {
                    performGpsStart()
                }
            } else {
                Log.w(TAG, "onPermissionResult: Location permission denied.")
                _diagnostics.value = _diagnostics.value.copy(
                    hasPermission = false,
                    status = GpsStatus.PERMISSION_DENIED,
                    errorMessage = "Location permission denied"
                )
                _state.value = NavigationState.AcquiringGps(hasPermission = false)
                locationEngine.stopLocationUpdates()
                speakOnce("Location permission denied. Navigation unavailable.")
            }
        }
    }

    override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {
        if (newMode == VisionMode.NAVIGATION) {
            Log.d(TAG, "Mode switched to NAVIGATION. Activating GPS navigation controller.")
            activate()
        } else if (previousMode == VisionMode.NAVIGATION) {
            Log.d(TAG, "Mode switched away from NAVIGATION to $newMode. Deactivating GPS navigation controller.")
            deactivate()
        }
    }

    override fun startGuidance(destinationAddress: String) {
        synchronized(lock) {
            if (!isActive) activate()
            val route = _diagnostics.value.currentRoute
            if (route != null) {
                startTurnByTurnGuidance(route)
            } else {
                searchDestinationAndRoute(destinationAddress)
            }
        }
    }

    override fun stopGuidance() {
        synchronized(lock) {
            guidanceProgressJob?.cancel()
            guidanceProgressJob = null

            guidanceEventJob?.cancel()
            guidanceEventJob = null

            internalGuidanceEngine.stop()

            _diagnostics.value = _diagnostics.value.copy(
                routeState = RouteSearchState.IDLE,
                currentRoute = null,
                guidanceProgress = GuidanceProgress()
            )
            _state.value = NavigationState.AcquiringGps(
                hasPermission = locationEngine.hasPermission(),
                accuracyMeters = diagnostics.value.locationFix?.accuracyMeters
            )
            speechController?.speak("Navigation guidance stopped.", SpeechPriority.NORMAL)
            Log.d(TAG, "Guidance halted.")
        }
    }

    private fun speakOnce(message: String) {
        if (lastSpokenMessage == message) return
        lastSpokenMessage = message
        speechController?.speak(message, SpeechPriority.HIGH)
    }

    private fun speakStateTransition(message: String) {
        if (lastSpokenMessage == message) return
        lastSpokenMessage = message
        speechController?.speak(message, SpeechPriority.HIGH)
    }

    companion object {
        private const val TAG = "DefaultNavController"
    }
}
