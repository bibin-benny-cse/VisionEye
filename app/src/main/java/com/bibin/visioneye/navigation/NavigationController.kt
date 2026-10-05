package com.bibin.visioneye.navigation

import com.bibin.visioneye.core.contract.ModeAwareComponent
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.navigation.guidance.GuidanceProgress
import com.bibin.visioneye.navigation.location.LocationFix
import com.bibin.visioneye.navigation.orientation.CompassStatus
import com.bibin.visioneye.navigation.orientation.RelativeDirection
import com.bibin.visioneye.navigation.routing.Route
import kotlinx.coroutines.flow.StateFlow

/**
 * Diagnostic status of the device GPS system.
 */
enum class GpsStatus {
    ACQUIRING,
    ACTIVE,
    UNAVAILABLE,
    PERMISSION_DENIED
}

/**
 * Operational state of destination search and route retrieval.
 */
enum class RouteSearchState {
    IDLE,
    GEOCODING,
    GEOCODING_FAILED,
    ROUTE_LOADING,
    ROUTE_READY,
    ROUTE_FAILED
}

/**
 * Real-time diagnostic state for outdoor GPS navigation, compass orientation, and physical device verification.
 *
 * @property hasPermission True if location permissions are currently granted.
 * @property status Current diagnostic status of the GPS provider.
 * @property locationFix Most recent valid geographic fix, or null if acquiring.
 * @property lastUpdateTimeMs Timestamp in milliseconds when the last fix was received.
 * @property isLocationServiceEnabled True if device location services (GPS) are toggled ON.
 * @property errorMessage Optional human-readable diagnostic error message.
 * @property headingDegrees Azimuth in degrees [0.0f, 360.0f), or null if compass is unavailable or initializing.
 * @property cardinalDirection 8-point compass quadrant string (e.g. "SE", "N"), or null if unavailable.
 * @property compassStatus Operational status of the orientation sensor engine.
 * @property relativeDirection Relative direction classification if target bearing is known.
 * @property routeState Progress status of destination search and route calculation.
 * @property currentRoute Active pedestrian route if loaded, or null if not yet requested.
 * @property routeErrorMessage Error message if geocoding or routing failed.
 * @property guidanceProgress Real-time turn-by-turn guidance progress metrics.
 */
data class NavigationGpsDiagnostics(
    val hasPermission: Boolean = false,
    val status: GpsStatus = GpsStatus.ACQUIRING,
    val locationFix: LocationFix? = null,
    val lastUpdateTimeMs: Long? = null,
    val isLocationServiceEnabled: Boolean = true,
    val errorMessage: String? = null,
    val headingDegrees: Float? = null,
    val cardinalDirection: String? = null,
    val compassStatus: CompassStatus = CompassStatus.INITIALIZING,
    val relativeDirection: RelativeDirection? = null,
    val routeState: RouteSearchState = RouteSearchState.IDLE,
    val currentRoute: Route? = null,
    val routeErrorMessage: String? = null,
    val guidanceProgress: GuidanceProgress = GuidanceProgress()
)

/**
 * High-level state representing outdoor navigation status.
 */
sealed interface NavigationState {
    data object Inactive : NavigationState
    data class AcquiringGps(
        val hasPermission: Boolean = true,
        val accuracyMeters: Float? = null
    ) : NavigationState

    // Phase 3: Destination Input, Geocoding, and Pedestrian Routing states
    data class DestinationInput(
        val destinationQuery: String = ""
    ) : NavigationState

    data class GeocodingInProgress(
        val query: String
    ) : NavigationState

    data class GeocodingFailure(
        val query: String,
        val errorMessage: String
    ) : NavigationState

    data class RouteLoading(
        val destination: String,
        val latitude: Double,
        val longitude: Double
    ) : NavigationState

    data class RouteLoaded(
        val route: Route
    ) : NavigationState

    data class RouteFailure(
        val destination: String,
        val errorMessage: String
    ) : NavigationState

    // Phase 4: Active turn-by-turn guidance state
    data class Navigating(
        val destination: String,
        val distanceRemainingMeters: Float,
        val currentInstruction: String = "",
        val currentStepIndex: Int = 0,
        val totalSteps: Int = 0
    ) : NavigationState

    data object DestinationReached : NavigationState
    data class Error(val message: String) : NavigationState
}


/**
 * Architectural contract for outdoor pedestrian GPS navigation.
 */
interface NavigationController : ModeAwareComponent {
    /**
     * High-level user guidance state.
     */
    val state: StateFlow<NavigationState>

    /**
     * Observable stream of real-time GPS diagnostic metrics for HUD and physical testing.
     */
    val diagnostics: StateFlow<NavigationGpsDiagnostics>

    override val supportedModes: Set<VisionMode>
        get() = setOf(VisionMode.NAVIGATION)

    /**
     * Activates GPS location acquisition and prepares guidance for NAVIGATION mode.
     */
    fun activate()

    /**
     * Halts GPS tracking and cleans up callbacks and listeners.
     */
    fun deactivate()

    /**
     * Updates permission grant state following interactive permission prompt in the UI.
     */
    fun onPermissionResult(granted: Boolean)

    /**
     * Starts turn-by-turn guidance towards [destinationAddress].
     */
    fun startGuidance(destinationAddress: String)

    /**
     * Halts guidance and returns to standby.
     */
    fun stopGuidance()

    /**
     * Geocodes [query] and calculates a pedestrian walking route from current GPS position.
     */
    fun searchDestinationAndRoute(query: String)

    /**
     * Cancels any in-flight geocoding or routing query and clears active route state.
     */
    fun cancelRouteSearch()
}

