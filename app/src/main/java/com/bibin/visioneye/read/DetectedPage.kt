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
    val areaRatio: Float,
    val isVerifiedBoundary: Boolean = true
) {
    init {
        require(corners.size == 4) { "DetectedPage must contain exactly 4 corners (TL, TR, BR, BL)." }
    }

    val topLeft: PagePoint get() = corners[0]
    val topRight: PagePoint get() = corners[1]
    val bottomRight: PagePoint get() = corners[2]
    val bottomLeft: PagePoint get() = corners[3]

    val boundsRectF: RectF get() = bounds.toRectF()

    /**
     * Whether this detected page is a verified, non-fallback page quadrilateral.
     */
    val isValidPageQuadrilateral: Boolean
        get() = isVerifiedBoundary && hasValidClockwiseConvexCorners()

    /**
     * Validates whether the 4 corners form a clockwise, non-degenerate convex quadrilateral.
     */
    fun hasValidClockwiseConvexCorners(): Boolean {
        if (corners.size != 4) return false
        val p0 = corners[0]
        val p1 = corners[1]
        val p2 = corners[2]
        val p3 = corners[3]

        // 2D cross product z-component: (Bx - Ax)*(Cy - By) - (By - Ay)*(Cx - Bx)
        fun crossProduct(a: PagePoint, b: PagePoint, c: PagePoint): Float {
            val abX = b.x - a.x
            val abY = b.y - a.y
            val bcX = c.x - b.x
            val bcY = c.y - b.y
            return abX * bcY - abY * bcX
        }

        val cp0 = crossProduct(p0, p1, p2)
        val cp1 = crossProduct(p1, p2, p3)
        val cp2 = crossProduct(p2, p3, p0)
        val cp3 = crossProduct(p3, p0, p1)

        // In screen coordinates (Y downwards), clockwise turns have positive cross products
        return cp0 > 0.001f && cp1 > 0.001f && cp2 > 0.001f && cp3 > 0.001f
    }

    companion object {
        /**
         * Fallback full-frame page representation when no page boundary candidate was detected.
         * Explicitly marked with [isVerifiedBoundary] = false and [confidence] = 0.0f.
         */
        fun fullFrame(): DetectedPage = DetectedPage(
            bounds = BoundingBox(0f, 0f, 1f, 1f),
            corners = listOf(
                PagePoint(0f, 0f),
                PagePoint(1f, 0f),
                PagePoint(1f, 1f),
                PagePoint(0f, 1f)
            ),
            confidence = 0.0f,
            areaRatio = 1.0f,
            isVerifiedBoundary = false
        )
    }
}
