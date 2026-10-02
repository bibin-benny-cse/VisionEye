package com.bibin.visioneye.ai

import android.graphics.RectF

/**
 * Spatial horizontal position of a detected object relative to the user's field of view.
 */
enum class HorizontalPosition {
    LEFT,
    CENTER,
    RIGHT;

    companion object {
        /**
         * Resolves the [HorizontalPosition] from a normalized horizontal coordinate [0.0, 1.0]
         * using configurable boundaries.
         *
         * @param normalizedX Horizontal center normalized to [0.0, 1.0] on the upright camera view.
         * @param leftBoundary Upper bound for the LEFT zone (default: 0.33f).
         * @param rightBoundary Lower bound for the RIGHT zone (default: 0.67f).
         */
        fun fromNormalizedX(
            normalizedX: Float,
            leftBoundary: Float = 0.33f,
            rightBoundary: Float = 0.67f
        ): HorizontalPosition {
            return when {
                normalizedX < leftBoundary -> LEFT
                normalizedX < rightBoundary -> CENTER
                else -> RIGHT
            }
        }
    }
}

/**
 * Immutable bounding box coordinates normalized to the [0.0, 1.0] range
 * relative to the camera image view.
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /**
     * Converts to Android [RectF] for Canvas/UI rendering.
     */
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

/**
 * Immutable representation of an object detected by an [ObjectDetector].
 *
 * Coordinates in [boundingBox] are normalized to the [0.0, 1.0] range relative
 * to the original, un-letterboxed camera frame (left, top, right, bottom).
 *
 * @property classId The zero-based integer index of the predicted class.
 * @property label The human-readable string name of the class (e.g., "person", "chair").
 * @property confidence Prediction score between 0.0f and 1.0f.
 * @property boundingBox Normalized bounding box coordinates within the camera view.
 * @property position Horizontal sector ([HorizontalPosition.LEFT], [HorizontalPosition.CENTER], [HorizontalPosition.RIGHT]).
 * @property timestampMs Epoch timestamp in milliseconds when this detection occurred.
 */
data class Detection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val position: HorizontalPosition = HorizontalPosition.fromNormalizedX(boundingBox.centerX),
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Standard human-readable name of the detected object class.
     */
    val className: String get() = label

    /**
     * Normalized horizontal center coordinate in the [0.0, 1.0] range relative to the upright camera image.
     * Calculated as `(left + right) / 2`.
     */
    val normalizedCenterX: Float get() = boundingBox.centerX
}
