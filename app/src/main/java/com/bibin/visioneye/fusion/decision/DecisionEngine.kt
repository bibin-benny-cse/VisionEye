package com.bibin.visioneye.fusion.decision

/**
 * Priority levels for the VisionEye Decision Engine.
 *
 * Strict prioritization order:
 * COLLISION_WARNING > IMMEDIATE_OBSTACLE > NAVIGATION_INSTRUCTION > GENERAL_OBJECT_INFO
 */
enum class AlertPriority(val rank: Int) {
    /**
     * General spatial and ambient visual feedback (e.g. "Bench on right", "Doorway open").
     */
    GENERAL_OBJECT_INFO(1),

    /**
     * Pedestrian navigation guidance (e.g. "In 20 meters, turn right on Main Street").
     */
    NAVIGATION_INSTRUCTION(2),

    /**
     * Urgent obstacles in the direct walkable path (e.g. "Person ahead", "Obstacle close").
     */
    IMMEDIATE_OBSTACLE(3),

    /**
     * Critical imminent danger requiring immediate user halt (e.g. "Stop. Vehicle approaching").
     */
    COLLISION_WARNING(4)
}

/**
 * Discrete alert candidate dispatched to the Decision Engine.
 *
 * @param key Unique semantic identifier for cooldown suppression (e.g., "obstacle_person_ahead").
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
 * Contract for the VisionEye Decision Engine.
 *
 * Arbitrates multi-modal alerts, prioritizes safety-critical events,
 * and suppresses repeated redundant announcements using cooldown logic.
 */
interface DecisionEngine {
    /**
     * Processes an incoming [AlertItem].
     *
     * @param alert The candidate alert item.
     * @return True if the alert should be spoken, false if suppressed by cooldown or lower priority.
     */
    fun shouldEmitAlert(alert: AlertItem): Boolean

    /**
     * Configurable cooldown window in milliseconds (default: 4000ms).
     */
    var cooldownPeriodMs: Long

    /**
     * Clears cached cooldown timestamps.
     */
    fun resetCooldowns()
}

/**
 * Default implementation of [DecisionEngine] with thread-safe cooldown tracking.
 */
class DefaultDecisionEngine(
    override var cooldownPeriodMs: Long = 4000L
) : DecisionEngine {

    private val lastAlertTimestamps = mutableMapOf<String, Long>()

    @Synchronized
    override fun shouldEmitAlert(alert: AlertItem): Boolean {
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

    @Synchronized
    override fun resetCooldowns() {
        lastAlertTimestamps.clear()
    }
}
