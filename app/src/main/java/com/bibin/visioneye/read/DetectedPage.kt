package com.bibin.visioneye.read

import android.graphics.PointF
import android.graphics.RectF
import com.bibin.visioneye.ai.BoundingBox

/**
 * 2D point with normalized coordinates in [0.0, 1.0].
 */
data class PagePoint(
    val x: Float,
    val y: Float
) {
    fun toPointF(): PointF = PointF(x, y)
}

/**
 * Representation of a detected rectangular page or document region in the camera frame.
 *
 * Coordinates are normalized in [0.0, 1.0] relative to upright image dimensions:
 * - (0.0, 0.0) is the top-left corner
 * - (1.0, 1.0) is the bottom-right corner
 *
 * @property bounds Bounding box enclosing the detected page.
 * @property corners Four ordered vertices: Top-Left, Top-Right, Bottom-Right, Bottom-Left.
 * @property confidence Quality / contrast confidence score in [0.0, 1.0].
 * @property areaRatio Area of the page as a fraction of total image area.
 */
data class DetectedPage(
    val bounds: BoundingBox,
    val corners: List<PagePoint>,
    val confidence: Float,
    val areaRatio: Float
) {
    init {
        require(corners.size == 4) { "DetectedPage must contain exactly 4 corners (TL, TR, BR, BL)." }
    }

    val topLeft: PagePoint get() = corners[0]
    val topRight: PagePoint get() = corners[1]
    val bottomRight: PagePoint get() = corners[2]
    val bottomLeft: PagePoint get() = corners[3]

    val boundsRectF: RectF get() = bounds.toRectF()
}
