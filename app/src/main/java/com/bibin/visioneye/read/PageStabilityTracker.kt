package com.bibin.visioneye.read

import kotlin.math.abs
import kotlin.math.max

/**
 * Result of evaluating candidate page stability.
 *
 * @property isStable True if the detected page has remained stable for the required duration.
 * @property progress Stabilization progress ratio in [0.0, 1.0].
 * @property page The candidate page associated with this stability evaluation.
 */
data class StabilityResult(
    val isStable: Boolean,
    val progress: Float,
    val page: DetectedPage?
)

/**
 * Evaluates temporal stability of detected pages across consecutive video frames.
 *
 * Prevents triggering high-resolution captures while the user is actively
 * repositioning the phone or while the camera is shaking.
 *
 * @property requiredStableFrames Number of consecutive stable frames required (default: 4 frames ~ 800ms at 5 FPS).
 * @property maxCenterDrift Maximum permissible horizontal or vertical center drift as a fraction of image dimensions (default: 0.05 = 5%).
 * @property maxAreaVariation Maximum permissible relative area variation between frames (default: 0.12 = 12%).
 */
class PageStabilityTracker(
    val requiredStableFrames: Int = 4,
    val maxCenterDrift: Float = 0.05f,
    val maxAreaVariation: Float = 0.12f
) {
    var stableCount: Int = 0
        private set

    private var referencePage: DetectedPage? = null

    /**
     * Updates the tracker with the latest frame's [DetectedPage] candidate.
     *
     * @param page Detected candidate page in the current frame, or null if no page detected.
     * @return [StabilityResult] indicating stability state and progress.
     */
    fun update(page: DetectedPage?): StabilityResult {
        if (page == null) {
            stableCount = 0
            referencePage = null
            return StabilityResult(isStable = false, progress = 0.0f, page = null)
        }

        val ref = referencePage
        if (ref == null) {
            // First candidate frame
            referencePage = page
            stableCount = 1
            val progress = (1.0f / requiredStableFrames).coerceIn(0.0f, 1.0f)
            return StabilityResult(
                isStable = stableCount >= requiredStableFrames,
                progress = progress,
                page = page
            )
        }

        // Compute center coordinates
        val refCenterX = ref.bounds.centerX
        val refCenterY = ref.bounds.centerY
        val currCenterX = page.bounds.centerX
        val currCenterY = page.bounds.centerY

        val driftX = abs(currCenterX - refCenterX)
        val driftY = abs(currCenterY - refCenterY)

        // Compute relative area variation
        val maxArea = max(ref.areaRatio, page.areaRatio)
        val areaDiff = abs(ref.areaRatio - page.areaRatio)
        val areaVar = if (maxArea > 0f) areaDiff / maxArea else 0f

        val isWithinLimits = driftX <= maxCenterDrift &&
                driftY <= maxCenterDrift &&
                areaVar <= maxAreaVariation

        if (isWithinLimits) {
            stableCount++
            referencePage = page
        } else {
            // Motion broke stability; restart stability tracking with current candidate
            stableCount = 1
            referencePage = page
        }

        val progress = (stableCount.toFloat() / requiredStableFrames).coerceIn(0.0f, 1.0f)
        val isStable = stableCount >= requiredStableFrames

        return StabilityResult(
            isStable = isStable,
            progress = progress,
            page = page
        )
    }

    /**
     * Resets the stability counter and reference state.
     */
    fun reset() {
        stableCount = 0
        referencePage = null
    }
}
