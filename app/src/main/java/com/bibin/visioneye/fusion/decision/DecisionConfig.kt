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
    val defaultPriority: AlertPriority = AlertPriority.LOW,
    val classProximityThresholds: Map<String, ProximityThresholds> = DEFAULT_CLASS_PROXIMITY_THRESHOLDS,
    val defaultProximityThresholds: ProximityThresholds = DEFAULT_FALLBACK_THRESHOLDS,
    val proximitySmoothingAlpha: Float = 0.35f,
    val proximityHysteresisMargin: Float = 0.04f,
    val proximityPersistenceObservations: Int = 2
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

    /**
     * Resolves the [ProximityThresholds] for a given class name.
     */
    fun getProximityThresholds(className: String): ProximityThresholds {
        val canonical = normalizeClassName(className)
        return classProximityThresholds[canonical] ?: defaultProximityThresholds
    }

    companion object {
        /**
         * Initial engineering threshold tiers for coarse proximity estimation.
         * Note: These represent coarse relative visual angles, NOT exact metric distances.
         */
        val DEFAULT_TALL_THRESHOLDS = ProximityThresholds(nearHeight = 0.60f, mediumHeight = 0.30f)
        val DEFAULT_MEDIUM_THRESHOLDS = ProximityThresholds(nearHeight = 0.55f, mediumHeight = 0.25f)
        val DEFAULT_SMALL_THRESHOLDS = ProximityThresholds(nearHeight = 0.38f, mediumHeight = 0.15f)
        val DEFAULT_FALLBACK_THRESHOLDS = ProximityThresholds(nearHeight = 0.55f, mediumHeight = 0.25f)

        val DEFAULT_CLASS_PROXIMITY_THRESHOLDS: Map<String, ProximityThresholds> = mapOf(
            // Tall tier: person, door, refrigerator, stairs
            "person" to DEFAULT_TALL_THRESHOLDS,
            "door" to DEFAULT_TALL_THRESHOLDS,
            "refrigerator" to DEFAULT_TALL_THRESHOLDS,
            "stairs" to DEFAULT_TALL_THRESHOLDS,

            // Medium / Furniture / Vehicle tier
            "chair" to DEFAULT_MEDIUM_THRESHOLDS,
            "table" to DEFAULT_MEDIUM_THRESHOLDS,
            "bed" to DEFAULT_MEDIUM_THRESHOLDS,
            "car" to DEFAULT_MEDIUM_THRESHOLDS,
            "bus" to DEFAULT_MEDIUM_THRESHOLDS,
            "truck" to DEFAULT_MEDIUM_THRESHOLDS,
            "bicycle" to DEFAULT_MEDIUM_THRESHOLDS,
            "motorcycle" to DEFAULT_MEDIUM_THRESHOLDS,

            // Small / Low obstacle tier
            "bag" to DEFAULT_SMALL_THRESHOLDS,
            "book" to DEFAULT_SMALL_THRESHOLDS,
            "cup" to DEFAULT_SMALL_THRESHOLDS
        )

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
