package com.bibin.visioneye.fusion.decision

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.HorizontalPosition
import kotlin.math.sqrt

/**
 * Discrete alert candidate dispatched to the Decision Engine (legacy format).
 *
 * @param key Unique semantic identifier for cooldown suppression.
 * @param spokenText Short, concise speech utterance to be vocalized to the user.
 * @param priority Relative urgency rank used to arbitrate speech queueing.
 * @param timestampMs Creation epoch timestamp in milliseconds.
 */
data class AlertItem(
    val key: String,
    val spokenText: String,
    val priority: AlertPriority,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Result of an analysis cycle processed by the [DecisionEngine].
 *
 * @property selectedAlerts Prioritized, unsuppressed alert candidates (at most [DecisionConfig.maxSelectedAlerts]).
 * @property suppressedCount Number of detections filtered out by transient stabilization, cooldown, or capacity limit.
 * @property totalCandidateCount Total number of detections evaluated above confidence threshold.
 * @property timestampMs Epoch timestamp of the decision cycle.
 */
data class DecisionResult(
    val selectedAlerts: List<AlertCandidate> = emptyList(),
    val suppressedCount: Int = 0,
    val totalCandidateCount: Int = 0,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Lightweight spatial-temporal tracking entry representing an object observed across frames.
 */
data class TrackedObject(
    val trackId: Int,
    val className: String,
    var lastBoundingBox: BoundingBox,
    var lastPosition: HorizontalPosition,
    var observationCount: Int = 1,
    var firstSeenTimestampMs: Long,
    var lastSeenTimestampMs: Long,
    var confidence: Float,
    var lastAlertTimestampMs: Long = 0L,
    var lastAlertPosition: HorizontalPosition? = null
)

/**
 * Contract for the VisionEye Context-Aware Decision Engine.
 *
 * Arbitrates raw object detections, applies rule-based class priorities,
 * enforces temporal repeat suppression cooldowns, requires observation stability,
 * and generates concise position-aware contextual alert candidates.
 */
interface DecisionEngine {
    /**
     * Active configuration governing thresholds, cooldowns, and class priorities.
     */
    val config: DecisionConfig

    /**
     * Transforms raw YOLO detections into a small set of prioritized [DecisionResult] candidates.
     *
     * @param detections List of current object detections from the vision pipeline.
     * @param timestampMs Optional evaluation epoch timestamp (defaults to current time).
     * @return [DecisionResult] containing selected candidates and suppression metrics.
     */
    fun process(
        detections: List<Detection>,
        timestampMs: Long = System.currentTimeMillis()
    ): DecisionResult

    /**
     * Legacy evaluation for discrete [AlertItem]s.
     */
    fun shouldEmitAlert(alert: AlertItem): Boolean

    /**
     * Configurable cooldown window in milliseconds.
     */
    var cooldownPeriodMs: Long

    /**
     * Clears all recorded cooldown timestamps and tracked object history.
     */
    fun resetCooldowns()
}

/**
 * Concrete implementation of [DecisionEngine] with deterministic arbitration,
 * lightweight temporal stabilization across frames, and thread-safe cooldown tracking.
 */
class DefaultDecisionEngine(
    override val config: DecisionConfig = DecisionConfig()
) : DecisionEngine {

    // Secondary constructor for backward compatibility with existing tests
    constructor(cooldownPeriodMs: Long, minimumStableObservations: Int = 1) : this(
        config = DecisionConfig(
            cooldownPeriodMs = cooldownPeriodMs,
            minimumStableObservations = minimumStableObservations
        )
    )

    private var _overrideCooldownPeriodMs: Long? = null

    override var cooldownPeriodMs: Long
        get() = _overrideCooldownPeriodMs ?: config.cooldownPeriodMs
        set(value) {
            _overrideCooldownPeriodMs = value
        }

    private val lastAlertTimestamps = mutableMapOf<String, Long>()
    private val trackedObjects = mutableListOf<TrackedObject>()
    private var nextTrackId = 1
    private val lock = Any()

    override fun process(
        detections: List<Detection>,
        timestampMs: Long
    ): DecisionResult = synchronized(lock) {
        // 1. Purge stale tracked objects that disappeared beyond timeout
        trackedObjects.removeAll { timestampMs - it.lastSeenTimestampMs > config.objectDisappearanceTimeoutMs }

        if (detections.isEmpty()) {
            return DecisionResult(emptyList(), 0, 0, timestampMs)
        }

        val activeCooldown = cooldownPeriodMs

        // 2. Centralized confidence filtering
        val validDetections = detections.filter { it.confidence >= config.alertConfidenceThreshold }

        // 3. Lightweight temporal tracking & association
        val updatedTracksInThisCycle = mutableSetOf<TrackedObject>()
        val matchedDetectionIndices = mutableSetOf<Int>()

        for (track in trackedObjects) {
            var bestMatchIndex = -1
            var bestMatchDist = Float.MAX_VALUE

            for ((index, det) in validDetections.withIndex()) {
                if (matchedDetectionIndices.contains(index)) continue
                if (!track.className.equals(det.className, ignoreCase = true)) continue

                val dx = det.boundingBox.centerX - track.lastBoundingBox.centerX
                val dy = det.boundingBox.centerY - track.lastBoundingBox.centerY
                val dist = sqrt(dx * dx + dy * dy)

                if (dist <= config.trackingMaxDisplacement && dist < bestMatchDist) {
                    bestMatchDist = dist
                    bestMatchIndex = index
                }
            }

            if (bestMatchIndex != -1) {
                val matchedDet = validDetections[bestMatchIndex]
                matchedDetectionIndices.add(bestMatchIndex)
                track.lastPosition = matchedDet.position
                track.lastBoundingBox = matchedDet.boundingBox
                track.confidence = matchedDet.confidence
                track.lastSeenTimestampMs = timestampMs
                track.observationCount++
                updatedTracksInThisCycle.add(track)
            }
        }

        // Detections without an existing track become newly observed tracks
        for ((index, det) in validDetections.withIndex()) {
            if (!matchedDetectionIndices.contains(index)) {
                val newTrack = TrackedObject(
                    trackId = nextTrackId++,
                    className = det.className,
                    lastBoundingBox = det.boundingBox,
                    lastPosition = det.position,
                    observationCount = 1,
                    firstSeenTimestampMs = timestampMs,
                    lastSeenTimestampMs = timestampMs,
                    confidence = det.confidence
                )
                trackedObjects.add(newTrack)
                updatedTracksInThisCycle.add(newTrack)
            }
        }

        // 4. Form candidate alerts from tracks updated in this cycle
        val candidates = mutableListOf<AlertCandidate>()
        var transientSuppressed = 0

        for (track in updatedTracksInThisCycle) {
            // Enforce temporal stability requirement
            if (track.observationCount < config.minimumStableObservations) {
                transientSuppressed++
                continue
            }

            val priority = config.getPriorityForClass(track.className)
            val priorityRank = config.getPriorityRank(track.className)
            val message = AlertMessageFormatter.format(track.className, track.lastPosition)

            val candidate = AlertCandidate(
                className = track.className,
                confidence = track.confidence,
                position = track.lastPosition,
                priority = priority,
                priorityRank = priorityRank,
                message = message,
                timestampMs = timestampMs,
                alertType = NavigationAlertType.fromPosition(track.lastPosition),
                reason = "Stable observation (${track.observationCount} frames) in ${track.lastPosition.name}",
                observationCount = track.observationCount
            )
            candidates.add(candidate)
        }

        // 5. Deterministic multi-object selection ordering:
        // Priority rank descending -> Position relevance (CENTER ahead of LEFT/RIGHT) -> Confidence descending -> Persistence descending -> Position ordinal -> Class name
        val sortedCandidates = candidates.sortedWith(
            Comparator { a, b ->
                val priorityDiff = b.priorityRank.compareTo(a.priorityRank)
                if (priorityDiff != 0) return@Comparator priorityDiff

                // Position relevance: CENTER (direct path) prioritized ahead of side sectors
                val aCenter = if (a.position == HorizontalPosition.CENTER) 1 else 0
                val bCenter = if (b.position == HorizontalPosition.CENTER) 1 else 0
                val centerDiff = bCenter.compareTo(aCenter)
                if (centerDiff != 0) return@Comparator centerDiff

                val confDiff = b.confidence.compareTo(a.confidence)
                if (confDiff != 0) return@Comparator confDiff

                val obsDiff = b.observationCount.compareTo(a.observationCount)
                if (obsDiff != 0) return@Comparator obsDiff

                val posDiff = a.position.ordinal.compareTo(b.position.ordinal)
                if (posDiff != 0) return@Comparator posDiff

                a.className.compareTo(b.className)
            }
        )

        // 6. Temporal cooldown suppression and maxSelectedAlerts cap
        val selected = mutableListOf<AlertCandidate>()
        var cooldownOrCapacitySuppressed = 0
        val seenInCurrentCycle = mutableSetOf<String>()

        for (candidate in sortedCandidates) {
            val key = candidate.deduplicationKey
            val lastEmitted = lastAlertTimestamps[key]
            val isWithinCooldown = lastEmitted != null && (timestampMs - lastEmitted) < activeCooldown
            val isDuplicateInSameCycle = seenInCurrentCycle.contains(key)

            if (isWithinCooldown || isDuplicateInSameCycle) {
                cooldownOrCapacitySuppressed++
                continue
            }

            if (selected.size < config.maxSelectedAlerts) {
                selected.add(candidate)
                seenInCurrentCycle.add(key)
                lastAlertTimestamps[key] = timestampMs

                // Record alert emission on tracked object
                trackedObjects.find {
                    it.className.equals(candidate.className, ignoreCase = true) && it.lastPosition == candidate.position
                }?.let {
                    it.lastAlertTimestampMs = timestampMs
                    it.lastAlertPosition = candidate.position
                }
            } else {
                cooldownOrCapacitySuppressed++
            }
        }

        DecisionResult(
            selectedAlerts = selected,
            suppressedCount = transientSuppressed + cooldownOrCapacitySuppressed,
            totalCandidateCount = validDetections.size,
            timestampMs = timestampMs
        )
    }

    override fun shouldEmitAlert(alert: AlertItem): Boolean = synchronized(lock) {
        val now = alert.timestampMs
        val lastEmitted = lastAlertTimestamps[alert.key]

        // Collision warnings always break through unless identical warning happened within 1 second
        if (alert.priority == AlertPriority.COLLISION_WARNING) {
            if (lastEmitted != null && (now - lastEmitted) < 1000L) {
                return false
            }
            lastAlertTimestamps[alert.key] = now
            return true
        }

        // Standard cooldown check for all other priority alerts
        if (lastEmitted != null && (now - lastEmitted) < cooldownPeriodMs) {
            return false // Suppressed by cooldown
        }

        lastAlertTimestamps[alert.key] = now
        return true
    }

    override fun resetCooldowns() = synchronized(lock) {
        lastAlertTimestamps.clear()
        trackedObjects.clear()
        nextTrackId = 1
    }
}
