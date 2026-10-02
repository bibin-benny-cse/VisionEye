package com.bibin.visioneye.fusion.decision

import com.bibin.visioneye.ai.HorizontalPosition

/**
 * Immutable contextual alert candidate produced by the [DecisionEngine].
 *
 * Represents an object detection that has been prioritized and formatted
 * for user presentation.
 *
 * @property className Human-readable class or object name (e.g. "chair", "person").
 * @property confidence Detection confidence score [0.0, 1.0].
 * @property position Spatial horizontal direction ([HorizontalPosition.LEFT], [CENTER], [RIGHT]).
 * @property priority Relative presentation priority tier.
 * @property priorityRank Granular numerical priority score used for deterministic arbitration.
 * @property message Concise descriptive message (e.g. "chair on your right", "person ahead").
 * @property timestampMs Epoch timestamp when the alert candidate was evaluated.
 */
data class AlertCandidate(
    val className: String,
    val confidence: Float,
    val position: HorizontalPosition,
    val priority: AlertPriority,
    val priorityRank: Int = priority.rank,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Unique semantic key used for cooldown duplicate suppression.
     * Combines object class name and horizontal position (e.g. "chair_LEFT").
     */
    val deduplicationKey: String
        get() = "${className}_${position.name}"

    /**
     * Human-readable alias for [className] representing the detected object.
     */
    val objectName: String
        get() = className
}

/**
 * Dedicated utility for generating concise, position-aware contextual alert messages.
 *
 * Position Mapping:
 * - LEFT:   "<object> on your left"
 * - CENTER: "<object> ahead"
 * - RIGHT:  "<object> on your right"
 *
 * Safety Boundary:
 * This generator produces purely descriptive spatial announcements and strictly
 * avoids safety/collision words ("obstacle", "danger", "collision", "stop").
 */
object AlertMessageFormatter {

    /**
     * Formats an alert utterance for an object at a given [position].
     */
    fun format(className: String, position: HorizontalPosition): String {
        val label = className.trim()
        return when (position) {
            HorizontalPosition.LEFT -> "$label on your left"
            HorizontalPosition.CENTER -> "$label ahead"
            HorizontalPosition.RIGHT -> "$label on your right"
        }
    }
}
