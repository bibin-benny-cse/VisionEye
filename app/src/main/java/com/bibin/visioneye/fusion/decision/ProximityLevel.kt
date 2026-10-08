package com.bibin.visioneye.fusion.decision

/**
 * Coarse relative proximity tiers for navigational obstacle awareness.
 *
 * Designed for relative voice feedback ("close ahead" vs "ahead") and does NOT
 * represent precise metric distance measurements.
 */
enum class ProximityLevel(val spokenText: String) {
    NEAR("close"),
    MEDIUM("medium"),
    FAR("far")
}

/**
 * Immutable class-specific height thresholds for coarse proximity estimation.
 *
 * @property nearHeight Normalized bounding-box height threshold above which an object is considered NEAR.
 * @property mediumHeight Normalized bounding-box height threshold above which an object is considered MEDIUM (below is FAR).
 */
data class ProximityThresholds(
    val nearHeight: Float,
    val mediumHeight: Float
) {
    init {
        require(nearHeight > mediumHeight) {
            "nearHeight ($nearHeight) must be greater than mediumHeight ($mediumHeight)"
        }
        require(mediumHeight >= 0f && nearHeight <= 1.0f) {
            "Thresholds must be within normalized [0.0, 1.0] range"
        }
    }
}
