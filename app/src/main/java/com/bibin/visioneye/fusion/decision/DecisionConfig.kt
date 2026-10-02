package com.bibin.visioneye.fusion.decision

/**
 * Centralized configuration for the rule-based [DecisionEngine].
 *
 * Provides a single source of truth for confidence filtering, temporal cooldown
 * suppression, maximum simultaneous alert capacity, and class priority ordering.
 *
 * @property alertConfidenceThreshold Minimum detection confidence for an alert candidate (default: 0.40f).
 * @property cooldownPeriodMs Milliseconds to suppress duplicate alerts for the same class & position (default: 4000ms).
 * @property maxSelectedAlerts Maximum number of alert candidates selected per analysis cycle (default: 2).
 * @property priorityOrder Ordered list of classes from highest to lowest presentation priority.
 * @property defaultPriority Priority tier assigned to common objects not explicitly prioritized.
 */
data class DecisionConfig(
    val alertConfidenceThreshold: Float = 0.40f,
    val cooldownPeriodMs: Long = 4000L,
    val maxSelectedAlerts: Int = 2,
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
         * Canonical priority ordering as specified in Milestone 5:
         * person -> vehicle-related classes -> bicycle -> motorcycle -> chair -> table -> bag -> book -> cup -> others.
         */
        val DEFAULT_PRIORITY_ORDER: List<String> = listOf(
            "person",
            "car", "bus", "truck",
            "bicycle",
            "motorcycle",
            "chair",
            "table", "dining table",
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
