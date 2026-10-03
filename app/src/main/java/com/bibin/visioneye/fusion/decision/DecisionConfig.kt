package com.bibin.visioneye.fusion.decision

/**
 * Centralized configuration for the rule-based [DecisionEngine].
 *
 * Provides a single source of truth for confidence filtering, temporal cooldown
 * suppression, observation persistence, maximum simultaneous alert capacity,
 * spatial zone boundaries, and class priority ordering.
 *
 * @property alertConfidenceThreshold Minimum detection confidence for an alert candidate (default: 0.40f).
 * @property cooldownPeriodMs Milliseconds to suppress duplicate alerts for the same class & position (default: 2500ms).
 * @property minimumStableObservations Number of consecutive frames an object must persist before alerting (default: 2).
 * @property maxSelectedAlerts Maximum number of alert candidates selected per analysis cycle (default: 2).
 * @property leftZoneBoundary Upper normalized X threshold for the LEFT horizontal sector (default: 0.33f).
 * @property rightZoneBoundary Lower normalized X threshold for the RIGHT horizontal sector (default: 0.67f).
 * @property trackingMaxDisplacement Maximum normalized bounding-box center distance to associate consecutive detections (default: 0.25f).
 * @property objectDisappearanceTimeoutMs Inactivity duration in ms after which a tracked object is considered lost (default: 1000ms).
 * @property priorityOrder Ordered list of classes from highest to lowest presentation priority.
 * @property defaultPriority Priority tier assigned to common objects not explicitly prioritized.
 */
data class DecisionConfig(
    val alertConfidenceThreshold: Float = 0.40f,
    val cooldownPeriodMs: Long = 2500L,
    val minimumStableObservations: Int = 2,
    val maxSelectedAlerts: Int = 2,
    val leftZoneBoundary: Float = 0.33f,
    val rightZoneBoundary: Float = 0.67f,
    val trackingMaxDisplacement: Float = 0.25f,
    val objectDisappearanceTimeoutMs: Long = 1000L,
    val priorityOrder: List<String> = DEFAULT_PRIORITY_ORDER,
    val defaultPriority: AlertPriority = AlertPriority.LOW
) {
    /**
     * Computes a granular integer priority score for a class name based on [priorityOrder].
     * Higher score = higher precedence during alert selection.
     */
    fun getPriorityRank(className: String): Int {
        val canonical = normalizeClassName(className)
        val index = priorityOrder.indexOfFirst { it.equals(canonical, ignoreCase = true) }
        return if (index != -1) {
            // Highest priority class (index 0) gets highest numerical rank
            (priorityOrder.size - index) * 10
        } else {
            defaultPriority.rank
        }
    }

    /**
     * Resolves the [AlertPriority] tier for a class name.
     */
    fun getPriorityForClass(className: String): AlertPriority {
        val rank = getPriorityRank(className)
        return when {
            rank >= 100 -> AlertPriority.URGENT
            rank >= 60 -> AlertPriority.HIGH
            rank >= 30 -> AlertPriority.MEDIUM
            else -> defaultPriority
        }
    }

    companion object {
        /**
         * Canonical priority ordering for navigation assistance:
         * person -> door -> stairs -> vehicles -> bicycle -> motorcycle -> chair -> table -> bed -> refrigerator -> bag -> book -> cup -> others.
         */
        val DEFAULT_PRIORITY_ORDER: List<String> = listOf(
            "person",
            "door",
            "stairs",
            "car", "bus", "truck",
            "bicycle",
            "motorcycle",
            "table", "dining table",
            "chair",
            "bed",
            "refrigerator",
            "bag", "backpack", "handbag", "suitcase",
            "book",
            "cup"
        )

        private fun normalizeClassName(name: String): String {
            val lower = name.trim().lowercase()
            return when (lower) {
                "dining table" -> "table"
                "backpack", "handbag", "suitcase" -> "bag"
                else -> lower
            }
        }
    }
}
