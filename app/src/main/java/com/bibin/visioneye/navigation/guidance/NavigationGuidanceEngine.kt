package com.bibin.visioneye.navigation.guidance

import android.util.Log
import com.bibin.visioneye.navigation.location.LocationFix
import com.bibin.visioneye.navigation.orientation.HeadingUtils
import com.bibin.visioneye.navigation.orientation.RelativeDirection
import com.bibin.visioneye.navigation.routing.Route
import com.bibin.visioneye.navigation.routing.RoutePoint
import com.bibin.visioneye.navigation.routing.RouteStep
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Architectural contract for on-device pedestrian turn-by-turn guidance and progress tracking.
 *
 * Decoupled from networking: Receives active [Route], continuous [LocationFix], and phone heading,
 * and emits deterministic progress updates and spoken guidance events.
 */
interface NavigationGuidanceEngine {
    /**
     * Real-time progress snapshot stream for UI diagnostics.
     */
    val progressFlow: StateFlow<GuidanceProgress>

    /**
     * Event stream for vocalized speech instructions, off-route warnings, and arrival notifications.
     */
    val eventFlow: SharedFlow<GuidanceEvent>

    /**
     * True if active route guidance is currently in progress.
     */
    val isGuiding: Boolean

    /**
     * Initiates turn-by-turn tracking along [route].
     */
    fun start(route: Route, initialLocation: LocationFix? = null)

    /**
     * Processes a new GPS location fix from [com.bibin.visioneye.navigation.location.LocationEngine].
     */
    fun onLocationUpdate(fix: LocationFix)

    /**
     * Processes a new physical phone heading from [com.bibin.visioneye.navigation.orientation.OrientationEngine].
     */
    fun onHeadingUpdate(headingDegrees: Float?)

    /**
     * Stops active guidance and resets tracking state.
     */
    fun stop()
}

/**
 * Production implementation of [NavigationGuidanceEngine].
 *
 * Implements:
 * - Multi-threshold pre-turn previews (50m, 20m, 8m "Turn now") with monotonic progression
 * - Robust off-route deviation detection with GPS accuracy filtering and debounce
 * - Stable destination arrival confirmation (10m radius)
 * - Heading-aware relative direction computation via [HeadingUtils]
 * - Deterministic, non-repeating speech event arbitration
 *
 * @param config Thresholds, radii, and cooldowns.
 * @param clock Time provider for deterministic testing.
 */
class DefaultNavigationGuidanceEngine(
    val config: GuidanceConfig = GuidanceConfig(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) : NavigationGuidanceEngine {

    private val lock = Any()

    private val _progressFlow = MutableStateFlow(GuidanceProgress())
    override val progressFlow: StateFlow<GuidanceProgress> = _progressFlow.asStateFlow()

    private val _eventFlow = MutableSharedFlow<GuidanceEvent>(replay = 1, extraBufferCapacity = 64)
    override val eventFlow: SharedFlow<GuidanceEvent> = _eventFlow.asSharedFlow()

    private var activeRoute: Route? = null
    private var currentStepIndex: Int = 0
    private var currentAnnouncementStage: TurnAnnouncementStage = TurnAnnouncementStage.NONE
    private var consecutiveOffRouteCount: Int = 0
    private var consecutiveArrivalCount: Int = 0
    private var lastRerouteTimestampMs: Long = 0L
    private var currentHeadingDegrees: Float? = null
    private var lastSpokenInstruction: String? = null
    private var hasAnnouncedStart: Boolean = false

    override var isGuiding: Boolean = false
        private set

    override fun start(route: Route, initialLocation: LocationFix?) {
        synchronized(lock) {
            activeRoute = route
            currentStepIndex = 0
            currentAnnouncementStage = TurnAnnouncementStage.NONE
            consecutiveOffRouteCount = 0
            consecutiveArrivalCount = 0
            lastSpokenInstruction = null
            hasAnnouncedStart = false
            isGuiding = true

            val steps = route.steps
            val firstStep = steps.firstOrNull()
            val nextStep = if (steps.size > 1) steps[1] else null

            val initialInstruction = "Route ready. Start walking."
            _progressFlow.value = GuidanceProgress(
                status = GuidanceStatus.STARTING,
                currentStepIndex = 0,
                totalSteps = steps.size,
                distanceToNextManeuver = firstStep?.distanceMeters ?: 0.0,
                remainingRouteDistance = route.totalDistanceMeters,
                currentInstruction = initialInstruction,
                currentStep = firstStep,
                nextStep = nextStep
            )

            emitSpeechEvent(initialInstruction, SpeechPriority.HIGH, GuidanceStatus.STARTING)
            hasAnnouncedStart = true

            if (initialLocation != null) {
                processLocation(initialLocation)
            }
            Log.d(TAG, "Navigation guidance started for '${route.destination}' with ${steps.size} steps.")
        }
    }

    override fun onLocationUpdate(fix: LocationFix) {
        synchronized(lock) {
            if (!isGuiding || activeRoute == null) return
            processLocation(fix)
        }
    }

    override fun onHeadingUpdate(headingDegrees: Float?) {
        synchronized(lock) {
            currentHeadingDegrees = headingDegrees
            val currentProgress = _progressFlow.value
            val stepBearing = currentProgress.stepBearing

            if (stepBearing != null && headingDegrees != null) {
                val relativeDir = HeadingUtils.relativeDirection(
                    targetBearing = stepBearing,
                    currentHeading = headingDegrees
                )
                if (currentProgress.relativeDirection != relativeDir) {
                    _progressFlow.value = currentProgress.copy(relativeDirection = relativeDir)
                }
            }
        }
    }

    private fun processLocation(fix: LocationFix) {
        val route = activeRoute ?: return
        val steps = route.steps
        if (steps.isEmpty()) return

        // 1. Destination Arrival Check
        val destinationPoint = route.geometry.lastOrNull() ?: RoutePoint(steps.last().latitude, steps.last().longitude)
        val distanceToDestination = GeoMathUtils.distanceMeters(
            fix.latitude, fix.longitude,
            destinationPoint.latitude, destinationPoint.longitude
        )

        if (distanceToDestination <= config.arrivalRadiusMeters) {
            consecutiveArrivalCount++
            if (consecutiveArrivalCount >= config.arrivalConsecutiveFixesRequired) {
                if (_progressFlow.value.status != GuidanceStatus.ARRIVED) {
                    val arrivalMsg = "You have arrived."
                    _progressFlow.value = _progressFlow.value.copy(
                        status = GuidanceStatus.ARRIVED,
                        currentInstruction = arrivalMsg,
                        distanceToNextManeuver = 0.0,
                        remainingRouteDistance = 0.0
                    )
                    emitSpeechEvent(arrivalMsg, SpeechPriority.HIGH, GuidanceStatus.ARRIVED)
                    _eventFlow.tryEmit(GuidanceEvent.DestinationArrived)
                    isGuiding = false
                    Log.d(TAG, "Destination arrived! Guidance completed.")
                }
                return
            }
        } else {
            consecutiveArrivalCount = 0
        }

        // 2. Off-Route Corridor Detection
        val distanceToPolyline = GeoMathUtils.distanceToPolylineMeters(
            fix.latitude, fix.longitude,
            route.geometry
        )

        // Only evaluate deviation if GPS accuracy is reliable (avoids false warnings on temporary signal loss)
        val isAccuracyReliable = fix.accuracyMeters <= config.maxGpsAccuracyForOffRouteMeters
        if (isAccuracyReliable) {
            if (distanceToPolyline > config.offRouteDistanceThresholdMeters) {
                consecutiveOffRouteCount++
                if (consecutiveOffRouteCount >= config.offRouteConsecutiveFixesRequired) {
                    val now = clock()
                    val canReroute = (now - lastRerouteTimestampMs) >= config.rerouteCooldownMs

                    _progressFlow.value = _progressFlow.value.copy(
                        status = GuidanceStatus.OFF_ROUTE,
                        offRouteDistance = distanceToPolyline,
                        consecutiveOffRouteCount = consecutiveOffRouteCount,
                        currentInstruction = "Off route. Recalculating."
                    )

                    if (canReroute) {
                        lastRerouteTimestampMs = now
                        emitSpeechEvent("Off route. Recalculating.", SpeechPriority.HIGH, GuidanceStatus.OFF_ROUTE)
                        _eventFlow.tryEmit(GuidanceEvent.OffRoute(distanceToPolyline))
                        _eventFlow.tryEmit(GuidanceEvent.RerouteNeeded(fix.latitude, fix.longitude))
                        Log.w(TAG, "Off-route detected ($distanceToPolyline m deviation). Reroute event emitted.")
                    }
                    return
                }
            } else {
                consecutiveOffRouteCount = 0
            }
        }

        // 3. Step Progression & Maneuver Targeting
        val currentStep = steps.getOrNull(currentStepIndex) ?: return
        val nextStep = steps.getOrNull(currentStepIndex + 1)

        val targetManeuverPoint = if (nextStep != null) {
            RoutePoint(nextStep.latitude, nextStep.longitude)
        } else {
            destinationPoint
        }

        val distanceToManeuver = GeoMathUtils.distanceMeters(
            fix.latitude, fix.longitude,
            targetManeuverPoint.latitude, targetManeuverPoint.longitude
        )

        // Calculate continuous remaining route distance
        val rawRemainingDistance = GeoMathUtils.remainingDistanceAlongPolylineMeters(
            fix.latitude, fix.longitude,
            route.geometry,
            searchStartIndex = max(0, currentStepIndex)
        )

        // Filter small GPS noise (< 5m) from increasing remaining distance during normal forward progress
        val prevRemaining = _progressFlow.value.remainingRouteDistance
        val remainingDistance = if (prevRemaining > 0.0 &&
            rawRemainingDistance > prevRemaining &&
            (rawRemainingDistance - prevRemaining) < 5.0
        ) {
            prevRemaining
        } else {
            rawRemainingDistance
        }

        // Outgoing leg target point for determining whether user has traversed past the turn
        val nextLegPoint = if (steps.size > currentStepIndex + 2) {
            val stepAfterNext = steps[currentStepIndex + 2]
            RoutePoint(stepAfterNext.latitude, stepAfterNext.longitude)
        } else {
            destinationPoint
        }

        val hasPassed = if (nextStep != null) {
            GeoMathUtils.hasPassedManeuver(
                fixLat = fix.latitude,
                fixLon = fix.longitude,
                maneuverLat = targetManeuverPoint.latitude,
                maneuverLon = targetManeuverPoint.longitude,
                nextLegLat = nextLegPoint.latitude,
                nextLegLon = nextLegPoint.longitude
            )
        } else {
            false
        }

        // Step Advance Check: User has passed the maneuver point into the outgoing leg, OR is within close intersection radius (<= 2.5m)
        val isManeuverCompleted = nextStep != null && (
            (hasPassed && distanceToManeuver <= config.stepAdvanceThresholdMeters) ||
            (distanceToManeuver <= 2.5)
        )

        if (isManeuverCompleted) {
            currentStepIndex++
            currentAnnouncementStage = TurnAnnouncementStage.NONE
            Log.d(TAG, "Advanced to step $currentStepIndex: '${nextStep.instruction}'")

            val newStep = steps[currentStepIndex]
            val upcomingStep = steps.getOrNull(currentStepIndex + 1)
            val street = if (newStep.streetName.isNotBlank()) newStep.streetName else "walkway"
            val continuationMsg = "Continue on $street."

            _progressFlow.value = _progressFlow.value.copy(
                status = GuidanceStatus.CONTINUE,
                currentStepIndex = currentStepIndex,
                currentInstruction = continuationMsg,
                distanceToNextManeuver = distanceToManeuver,
                remainingRouteDistance = remainingDistance,
                currentStep = newStep,
                nextStep = upcomingStep,
                offRouteDistance = distanceToPolyline
            )
            emitSpeechEvent(continuationMsg, SpeechPriority.NORMAL, GuidanceStatus.CONTINUE)
            return
        }

        // 4. Pre-turn Distance Announcement Generation
        val targetStepForManeuver = nextStep ?: currentStep
        val baseAction = synthesizeManeuverAction(targetStepForManeuver)

        var status = _progressFlow.value.status
        if (status == GuidanceStatus.STARTING || status == GuidanceStatus.OFF_ROUTE) {
            status = GuidanceStatus.CONTINUE
        }

        if (nextStep != null) {
            if (distanceToManeuver <= config.turnNowThresholdMeters) {
                if (currentAnnouncementStage < TurnAnnouncementStage.TURN_NOW) {
                    currentAnnouncementStage = TurnAnnouncementStage.TURN_NOW
                    status = GuidanceStatus.TURN_NOW
                    val msg = "$baseAction now."
                    emitSpeechEvent(msg, SpeechPriority.HIGH, GuidanceStatus.TURN_NOW)
                }
            } else if (distanceToManeuver <= config.nearPreviewThresholdMeters) {
                if (currentAnnouncementStage < TurnAnnouncementStage.NEAR_PREVIEW) {
                    currentAnnouncementStage = TurnAnnouncementStage.NEAR_PREVIEW
                    status = GuidanceStatus.APPROACHING_TURN
                    val msg = "$baseAction in 20 meters."
                    emitSpeechEvent(msg, SpeechPriority.HIGH, GuidanceStatus.APPROACHING_TURN)
                }
            } else if (distanceToManeuver <= config.farPreviewThresholdMeters) {
                if (currentAnnouncementStage < TurnAnnouncementStage.FAR_PREVIEW) {
                    currentAnnouncementStage = TurnAnnouncementStage.FAR_PREVIEW
                    status = GuidanceStatus.APPROACHING_TURN
                    val msg = "$baseAction in 50 meters."
                    emitSpeechEvent(msg, SpeechPriority.NORMAL, GuidanceStatus.APPROACHING_TURN)
                }
            }
        }

        // 5. Bearing & Relative Direction
        val stepBearing = GeoMathUtils.bearingDegrees(
            fix.latitude, fix.longitude,
            targetManeuverPoint.latitude, targetManeuverPoint.longitude
        )

        val relativeDir = currentHeadingDegrees?.let {
            HeadingUtils.relativeDirection(targetBearing = stepBearing, currentHeading = it)
        }

        val activeInstruction = lastSpokenInstruction ?: currentStep.instruction

        _progressFlow.value = GuidanceProgress(
            status = status,
            currentStepIndex = currentStepIndex,
            totalSteps = steps.size,
            distanceToNextManeuver = distanceToManeuver,
            remainingRouteDistance = remainingDistance,
            currentInstruction = activeInstruction,
            currentStep = currentStep,
            nextStep = nextStep,
            stepBearing = stepBearing,
            relativeDirection = relativeDir,
            offRouteDistance = distanceToPolyline,
            consecutiveOffRouteCount = consecutiveOffRouteCount
        )
    }

    private fun synthesizeManeuverAction(step: RouteStep): String {
        val mod = step.modifier.trim().lowercase()
        val type = step.maneuverType.trim().lowercase()

        return when {
            mod.contains("sharp") && mod.contains("left") -> "Make a sharp left"
            mod.contains("sharp") && mod.contains("right") -> "Make a sharp right"
            mod.contains("slight") && mod.contains("left") -> "Keep slightly left"
            mod.contains("slight") && mod.contains("right") -> "Keep slightly right"
            mod == "left" -> "Turn left"
            mod == "right" -> "Turn right"
            mod == "straight" || type == "continue" -> "Continue straight"
            type == "arrive" -> "Arrive at destination"
            step.instruction.isNotBlank() -> step.instruction
            else -> "Continue"
        }
    }

    private fun emitSpeechEvent(message: String, priority: SpeechPriority, status: GuidanceStatus) {
        if (lastSpokenInstruction == message) return
        lastSpokenInstruction = message
        _eventFlow.tryEmit(
            GuidanceEvent.SpokenInstruction(
                message = message,
                priority = priority,
                status = status
            )
        )
    }

    override fun stop() {
        synchronized(lock) {
            isGuiding = false
            activeRoute = null
            currentStepIndex = 0
            currentAnnouncementStage = TurnAnnouncementStage.NONE
            consecutiveOffRouteCount = 0
            consecutiveArrivalCount = 0
            lastSpokenInstruction = null
            _progressFlow.value = GuidanceProgress(status = GuidanceStatus.IDLE)
            Log.d(TAG, "Navigation guidance stopped.")
        }
    }

    companion object {
        private const val TAG = "NavigationGuidance"
    }
}
