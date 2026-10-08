package com.bibin.visioneye.fusion.decision

import com.bibin.visioneye.ai.HorizontalPosition

/**
 * Structured navigational alert types categorized by horizontal spatial orientation.
 */
enum class NavigationAlertType {
    OBJECT_LEFT,
    OBJECT_CENTER,
    OBJECT_RIGHT;

    companion object {
        fun fromPosition(position: HorizontalPosition): NavigationAlertType = when (position) {
            HorizontalPosition.LEFT -> OBJECT_LEFT
            HorizontalPosition.CENTER -> OBJECT_CENTER
            HorizontalPosition.RIGHT -> OBJECT_RIGHT
        }
    }
}

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
 * @property alertType Structured alert categorization ([NavigationAlertType.OBJECT_LEFT], etc.).
 * @property reason Human-readable explanation of why this alert was generated.
 * @property observationCount Number of consecutive frames this object was stably observed.
 */
data class AlertCandidate(
    val className: String,
    val confidence: Float,
    val position: HorizontalPosition,
    val priority: AlertPriority,
    val priorityRank: Int = priority.rank,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val alertType: NavigationAlertType = NavigationAlertType.fromPosition(position),
    val reason: String = "Stable detection in ${position.name} sector",
    val observationCount: Int = 1,
    val trackId: Int? = null,
    val proximity: ProximityLevel = ProximityLevel.MEDIUM
) {
    /**
     * Unique semantic key used for cooldown duplicate suppression.
     * Combines object class name, horizontal position, and proximity level (e.g. "chair_LEFT_NEAR").
     */
    val deduplicationKey: String
        get() = "${className}_${position.name}_${proximity.name}"

    /**
     * Human-readable alias for [className] representing the detected object.
     */
    val objectName: String
        get() = className
}

/**
 * Dedicated utility for generating concise, position-aware contextual alert messages.
 *
 * Position & Proximity Mapping:
 * - NEAR:   "<object> close ahead", "<object> close on your left", "<object> close on your right"
 * - MEDIUM: "<object> ahead", "<object> on your left", "<object> on your right"
 * - FAR:    "<object> ahead", "<object> on your left", "<object> on your right"
 *
 * Safety Boundary:
 * This generator produces purely descriptive spatial announcements and strictly
 * avoids collision words ("danger", "collision", "stop") and exact metric distance assumptions.
 */
object AlertMessageFormatter {

    /**
     * Formats an alert utterance for an object at a given [position] and coarse [proximity].
     *
     * Exact patterns:
     * - NEAR:   "<object>, close, ahead" / "<object>, close, on your left" / "<object>, close, on your right"
     * - MEDIUM: "<object>, medium, ahead" / "<object>, medium, on your left" / "<object>, medium, on your right"
     * - FAR:    "<object>, far, ahead" / "<object>, far, on your left" / "<object>, far, on your right"
     */
    fun format(
        className: String,
        position: HorizontalPosition,
        proximity: ProximityLevel = ProximityLevel.MEDIUM
    ): String {
        val label = className.trim()
        val proximityWord = proximity.spokenText
        val positionWord = when (position) {
            HorizontalPosition.LEFT -> "on your left"
            HorizontalPosition.CENTER -> "ahead"
            HorizontalPosition.RIGHT -> "on your right"
        }
        return "$label, $proximityWord, $positionWord"
    }

    /**
     * Formats an alert utterance with capitalized first character for display banners.
     */
    fun formatCapitalized(
        className: String,
        position: HorizontalPosition,
        proximity: ProximityLevel = ProximityLevel.MEDIUM
    ): String {
        val base = format(className, position, proximity)
        return base.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
