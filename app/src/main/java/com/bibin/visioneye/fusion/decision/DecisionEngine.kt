package com.bibin.visioneye.fusion.decision

import com.bibin.visioneye.ai.Detection

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
 * @property suppressedCount Number of detections filtered out by cooldown or capacity limit.
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
 * Contract for the VisionEye Context-Aware Decision Engine.
 *
 * Arbitrates raw object detections, applies rule-based class priorities,
 * enforces temporal repeat suppression cooldowns, and generates concise
 * position-aware contextual alert candidates.
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
     * Clears all recorded cooldown timestamps.
     */
    fun resetCooldowns()
}

/**
 * Concrete implementation of [DecisionEngine] with deterministic arbitration
 * and thread-safe temporal cooldown tracking.
 */
class DefaultDecisionEngine(
    override val config: DecisionConfig = DecisionConfig()
) : DecisionEngine {

    // Secondary constructor for backward compatibility with existing tests
    constructor(cooldownPeriodMs: Long) : this(
        config = DecisionConfig(cooldownPeriodMs = cooldownPeriodMs)
    )

    private var _overrideCooldownPeriodMs: Long? = null

    override var cooldownPeriodMs: Long
        get() = _overrideCooldownPeriodMs ?: config.cooldownPeriodMs
        set(value) {
            _overrideCooldownPeriodMs = value
        }

    private val lastAlertTimestamps = mutableMapOf<String, Long>()
    private val lock = Any()

    override fun process(
        detections: List<Detection>,
        timestampMs: Long
    ): DecisionResult = synchronized(lock) {
        if (detections.isEmpty()) {
            return DecisionResult(emptyList(), 0, 0, timestampMs)
        }

        val activeCooldown = cooldownPeriodMs

        // 1. Confidence filter (alertConfidenceThreshold)
        val validDetections = detections.filter { it.confidence >= config.alertConfidenceThreshold }

        // 2. Map detections to AlertCandidate with position-aware message and priority
        val candidates = validDetections.map { det ->
            val priority = config.getPriorityForClass(det.className)
            val priorityRank = config.getPriorityRank(det.className)
            val message = AlertMessageFormatter.format(det.className, det.position)

            AlertCandidate(
                className = det.className,
                confidence = det.confidence,
                position = det.position,
                priority = priority,
                priorityRank = priorityRank,
                message = message,
                timestampMs = timestampMs
            )
        }

        // 3. Deterministic ordering:
        // Priority rank descending -> Confidence descending -> Position ordinal -> Class name
        val sortedCandidates = candidates.sortedWith(
            compareByDescending<AlertCandidate> { it.priorityRank }
                .thenByDescending { it.confidence }
                .thenBy { it.position.ordinal }
                .thenBy { it.className }
        )

        // 4. Temporal cooldown suppression and maxSelectedAlerts cap
        val selected = mutableListOf<AlertCandidate>()
        var suppressed = 0
        val seenInCurrentCycle = mutableSetOf<String>()

        for (candidate in sortedCandidates) {
            val key = candidate.deduplicationKey
            val lastEmitted = lastAlertTimestamps[key]
            val isWithinCooldown = lastEmitted != null && (timestampMs - lastEmitted) < activeCooldown
            val isDuplicateInSameCycle = seenInCurrentCycle.contains(key)

            if (isWithinCooldown || isDuplicateInSameCycle) {
                suppressed++
                continue
            }

            if (selected.size < config.maxSelectedAlerts) {
                selected.add(candidate)
                seenInCurrentCycle.add(key)
                lastAlertTimestamps[key] = timestampMs
            } else {
                suppressed++
            }
        }

        DecisionResult(
            selectedAlerts = selected,
            suppressedCount = suppressed,
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
    }
}
