package com.bibin.visioneye.ai

import kotlin.math.max
import kotlin.math.min

/**
 * Pixel coordinates of a bounding box projected onto the display view.
 *
 * Coordinates may extend beyond the display bounds if the camera stream
 * is center-cropped by [androidx.camera.view.PreviewView.ScaleType.FILL_CENTER].
 *
 * @property left Left edge in view pixel coordinates.
 * @property top Top edge in view pixel coordinates.
 * @property right Right edge in view pixel coordinates.
 * @property bottom Bottom edge in view pixel coordinates.
 */
data class ViewRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Isolated utility that projects normalized [BoundingBox] coordinates [0.0, 1.0] from
 * the camera sensor's upright coordinate space into actual display view pixel space.
 *
 * Replicates the exact geometric transformation of CameraX [androidx.camera.view.PreviewView]
 * using [androidx.camera.view.PreviewView.ScaleType.FILL_CENTER]:
 * - Aspect-ratio preserving uniform scaling (no non-uniform distortion)
 * - Excess image margins center-cropped equally across top/bottom or left/right
 * - Fully isolated from Android UI classes for fast, deterministic JVM unit testing
 */
object PreviewCoordinateMapper {

    /**
     * Projects a normalized [BoundingBox] into display view pixel space under FILL_CENTER.
     *
     * @param box Normalized bounding box with coordinates in [0.0, 1.0].
     * @param viewWidth Display view width in pixels.
     * @param viewHeight Display view height in pixels.
     * @param streamWidth Upright camera stream width (e.g. 480).
     * @param streamHeight Upright camera stream height (e.g. 640).
     * @return [ViewRect] containing scaled, offset display pixel coordinates.
     */
    fun mapBoxToView(
        box: BoundingBox,
        viewWidth: Float,
        viewHeight: Float,
        streamWidth: Float = 480f,
        streamHeight: Float = 640f
    ): ViewRect {
        if (viewWidth <= 0f || viewHeight <= 0f || streamWidth <= 0f || streamHeight <= 0f) {
            return ViewRect(0f, 0f, 0f, 0f)
        }

        // Align stream orientation with view orientation (portrait vs landscape)
        val isViewPortrait = viewHeight >= viewWidth
        val effectiveStreamW = if (isViewPortrait) min(streamWidth, streamHeight) else max(streamWidth, streamHeight)
        val effectiveStreamH = if (isViewPortrait) max(streamWidth, streamHeight) else min(streamWidth, streamHeight)

        val viewAspect = viewWidth / viewHeight
        val streamAspect = effectiveStreamW / effectiveStreamH

        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (viewAspect > streamAspect) {
            // View is wider than camera stream: fit width, crop height top/bottom
            scale = viewWidth / effectiveStreamW
            val scaledH = effectiveStreamH * scale
            offsetX = 0f
            offsetY = (viewHeight - scaledH) / 2f
        } else {
            // View is taller/narrower than camera stream: fit height, crop width left/right
            scale = viewHeight / effectiveStreamH
            val scaledW = effectiveStreamW * scale
            offsetX = (viewWidth - scaledW) / 2f
            offsetY = 0f
        }

        val scaledW = effectiveStreamW * scale
        val scaledH = effectiveStreamH * scale

        return ViewRect(
            left = box.left * scaledW + offsetX,
            top = box.top * scaledH + offsetY,
            right = box.right * scaledW + offsetX,
            bottom = box.bottom * scaledH + offsetY
        )
    }

    /**
     * Projects a single normalized point (normX, normY) into view pixel coordinates.
     */
    fun mapPointToView(
        normX: Float,
        normY: Float,
        viewWidth: Float,
        viewHeight: Float,
        streamWidth: Float = 480f,
        streamHeight: Float = 640f
    ): Pair<Float, Float> {
        val dummyBox = BoundingBox(left = normX, top = normY, right = normX, bottom = normY)
        val rect = mapBoxToView(dummyBox, viewWidth, viewHeight, streamWidth, streamHeight)
        return Pair(rect.left, rect.top)
    }
}
