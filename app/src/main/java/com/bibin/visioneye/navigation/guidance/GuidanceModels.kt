package com.bibin.visioneye.navigation.guidance

import com.bibin.visioneye.navigation.orientation.RelativeDirection
import com.bibin.visioneye.navigation.routing.RouteStep
import com.bibin.visioneye.speech.SpeechPriority

/**
 * Operational progression status of pedestrian turn-by-turn guidance.
 */
enum class GuidanceStatus {
    IDLE,
    STARTING,
    CONTINUE,
    APPROACHING_TURN,
    TURN_NOW,
    OFF_ROUTE,
    REROUTING,
    ARRIVED,
    ERROR
}

/**
 * Stage of pre-turn distance announcement for the current step to avoid repeating identical alerts.
 */
enum class TurnAnnouncementStage {
    NONE,
    FAR_PREVIEW,    // ~50m
    NEAR_PREVIEW,   // ~20m
    TURN_NOW        // ~5-8m
}

/**
 * Real-time progress snapshot along the active pedestrian route.
 *
 * @property status Current progression state.
 * @property currentStepIndex 0-based index of the currently active [RouteStep].
 * @property totalSteps Total number of steps in the active route.
 * @property distanceToNextManeuver Distance in meters to the upcoming turn or arrival point.
 * @property remainingRouteDistance Total remaining walking distance in meters to final destination.
 * @property currentInstruction Spoken navigation guidance string (e.g. "Turn left in 20 meters").
 * @property currentStep Current route maneuver step.
 * @property nextStep Next upcoming route maneuver step (if any).
 * @property stepBearing Bearing in degrees along current segment.
 * @property relativeDirection Relative direction to target bearing given current phone heading.
 * @property offRouteDistance Current perpendicular deviation from the route path in meters.
 * @property consecutiveOffRouteCount Number of consecutive reliable location fixes outside route corridor.
 */
data class GuidanceProgress(
    val status: GuidanceStatus = GuidanceStatus.IDLE,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val distanceToNextManeuver: Double = 0.0,
    val remainingRouteDistance: Double = 0.0,
    val currentInstruction: String = "",
    val currentStep: RouteStep? = null,
    val nextStep: RouteStep? = null,
    val stepBearing: Float? = null,
    val relativeDirection: RelativeDirection? = null,
    val offRouteDistance: Double = 0.0,
    val consecutiveOffRouteCount: Int = 0
)

/**
 * Configurable parameters and threshold distances for turn-by-turn pedestrian guidance.
 *
 * @property farPreviewThresholdMeters Distance at which far preview is announced (~50m).
 * @property nearPreviewThresholdMeters Distance at which near preview is announced (~20m).
 * @property turnNowThresholdMeters Distance at which "Turn now" prompt is triggered (~5-8m).
 * @property stepAdvanceThresholdMeters Distance past/near maneuver point to advance to next step (~10m).
 * @property offRouteDistanceThresholdMeters Corridor threshold to declare off-route (~25m).
 * @property offRouteConsecutiveFixesRequired Number of consecutive outlier fixes required to confirm off-route (default 3).
 * @property arrivalRadiusMeters Distance threshold to declare destination arrival (~10m).
 * @property arrivalConsecutiveFixesRequired Number of consecutive fixes within arrival radius required (default 2).
 * @property rerouteCooldownMs Minimum delay in ms between consecutive automatic reroute requests.
 * @property maxGpsAccuracyForOffRouteMeters Maximum allowed location accuracy error (meters) to evaluate off-route.
 */
data class GuidanceConfig(
    val farPreviewThresholdMeters: Double = 50.0,
    val nearPreviewThresholdMeters: Double = 20.0,
    val turnNowThresholdMeters: Double = 8.0,
    val stepAdvanceThresholdMeters: Double = 5.0,
    val offRouteDistanceThresholdMeters: Double = 25.0,
    val offRouteConsecutiveFixesRequired: Int = 3,
    val arrivalRadiusMeters: Double = 10.0,
    val arrivalConsecutiveFixesRequired: Int = 2,
    val rerouteCooldownMs: Long = 10_000L,
    val maxGpsAccuracyForOffRouteMeters: Float = 25.0f
)

/**
 * Event emitted by the guidance engine to trigger audio alerts or controller actions.
 */
sealed interface GuidanceEvent {
    /**
     * Spoken navigation instruction to vocalize through [com.bibin.visioneye.speech.SpeechController].
     */
    data class SpokenInstruction(
        val message: String,
        val priority: SpeechPriority,
        val status: GuidanceStatus
    ) : GuidanceEvent

    /**
     * User has deviated outside the route corridor for consecutive reliable fixes.
     */
    data class OffRoute(val deviationMeters: Double) : GuidanceEvent

    /**
     * Automatic rerouting should be performed from the given origin coordinates.
     */
    data class RerouteNeeded(val originLat: Double, val originLon: Double) : GuidanceEvent

    /**
     * User has arrived at the final destination.
     */
    data object DestinationArrived : GuidanceEvent
}
