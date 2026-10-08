package com.bibin.visioneye.fusion.decision

import com.bibin.visioneye.ai.Detection

/**
 * Pure, deterministic estimator for coarse relative proximity.
 *
 * Computes proximity tiers (NEAR, MEDIUM, FAR) directly from normalized bounding-box
 * geometry produced by YOLOv8n, avoiding external depth neural networks.
 */
object ProximityEstimator {

    /**
     * Estimates raw instantaneous [ProximityLevel] for a given [detection] using [thresholds].
     *
     * Primary measurement: normalized bounding-box height (`detection.boundingBox.height`).
     * Secondary measurement: normalized bounding-box area is inspected only as a sanity signal.
     *
     * @param detection Candidate YOLO detection with normalized bounding-box coordinates.
     * @param thresholds Class-specific or fallback [ProximityThresholds].
     * @return [ProximityLevel.NEAR], [ProximityLevel.MEDIUM], or [ProximityLevel.FAR].
     */
    fun estimate(detection: Detection, thresholds: ProximityThresholds): ProximityLevel {
        val box = detection.boundingBox
        val width = box.width
        val height = box.height

        // Sanity signal: invalid or degenerate boxes classify safely as FAR
        if (width <= 0f || height <= 0f) {
            return ProximityLevel.FAR
        }
        val area = width * height
        if (area <= 0f) {
            return ProximityLevel.FAR
        }

        return estimateFromHeight(height, thresholds)
    }

    /**
     * Estimates raw instantaneous [ProximityLevel] given a normalized [heightScore] and [thresholds].
     *
     * @param heightScore Normalized height in the range [0.0, 1.0].
     * @param thresholds Class-specific or fallback [ProximityThresholds].
     */
    fun estimateFromHeight(heightScore: Float, thresholds: ProximityThresholds): ProximityLevel {
        if (heightScore <= 0f) return ProximityLevel.FAR

        return when {
            heightScore >= thresholds.nearHeight -> ProximityLevel.NEAR
            heightScore >= thresholds.mediumHeight -> ProximityLevel.MEDIUM
            else -> ProximityLevel.FAR
        }
    }

    /**
     * Determines the target [ProximityLevel] applying dual-threshold hysteresis margin.
     *
     * Moving closer:
     * - FAR -> MEDIUM when `smoothedHeight >= mediumThreshold`
     * - MEDIUM -> NEAR when `smoothedHeight >= nearThreshold`
     *
     * Moving farther:
     * - NEAR -> MEDIUM only below `nearThreshold - hysteresis`
     * - MEDIUM -> FAR only below `mediumThreshold - hysteresis`
     *
     * @param smoothedHeight Exponentially smoothed normalized height.
     * @param currentProximity Active proximity state.
     * @param thresholds Class-specific [ProximityThresholds].
     * @param hysteresis Configurable hysteresis margin to prevent boundary chattering.
     */
    fun resolveWithHysteresis(
        smoothedHeight: Float,
        currentProximity: ProximityLevel,
        thresholds: ProximityThresholds,
        hysteresis: Float
    ): ProximityLevel {
        if (smoothedHeight <= 0f) return ProximityLevel.FAR

        return when (currentProximity) {
            ProximityLevel.NEAR -> {
                when {
                    smoothedHeight >= (thresholds.nearHeight - hysteresis) -> ProximityLevel.NEAR
                    smoothedHeight >= (thresholds.mediumHeight - hysteresis) -> ProximityLevel.MEDIUM
                    else -> ProximityLevel.FAR
                }
            }
            ProximityLevel.MEDIUM -> {
                when {
                    smoothedHeight >= thresholds.nearHeight -> ProximityLevel.NEAR
                    smoothedHeight >= (thresholds.mediumHeight - hysteresis) -> ProximityLevel.MEDIUM
                    else -> ProximityLevel.FAR
                }
            }
            ProximityLevel.FAR -> {
                when {
                    smoothedHeight >= thresholds.nearHeight -> ProximityLevel.NEAR
                    smoothedHeight >= thresholds.mediumHeight -> ProximityLevel.MEDIUM
                    else -> ProximityLevel.FAR
                }
            }
        }
    }
}
