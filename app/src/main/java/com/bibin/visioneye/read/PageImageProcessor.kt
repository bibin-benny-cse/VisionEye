package com.bibin.visioneye.read

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import kotlin.math.hypot
import kotlin.math.max

/**
 * Handles high-resolution perspective correction and rectangular page cropping.
 *
 * Straightens tilted or skewed documents using standard Android [Matrix.setPolyToPoly]
 * 4-point homography, producing an upright, un-distorted rectangular image optimized for OCR.
 *
 * Keeps image preprocessing minimal: avoids destructive binarization, aggressive thresholding,
 * or color-space compression to preserve subtle character strokes and text clarity.
 */
open class PageImageProcessor {

    /**
     * Diagnostic timing for the most recent perspective rectification pass in milliseconds.
     */
    var lastProcessingDurationMs: Long = 0L
        private set

    /**
     * Whether the most recent call to [rectifyPage] performed a 4-point perspective
     * transformation (true) or bypassed it due to unverified boundaries / non-convex corners (false).
     */
    var lastWasPerspectiveRectified: Boolean = false
        private set

    /**
     * Determines whether [page] qualifies for 4-point perspective rectification.
     * Requires verified boundary detection, valid clockwise convex quadrilateral corners,
     * and reasonable rectangular perspective geometry (no severe shear or distortion).
     */
    open fun shouldPerformPerspectiveRectification(page: DetectedPage): Boolean {
        return page.isVerifiedBoundary && page.hasValidClockwiseConvexCorners() && hasReasonablePerspectiveGeometry(page)
    }

    /**
     * Validates that the quadrilateral corners have reasonable interior angles (~90°)
     * and symmetric opposite edges, preventing extreme homography stretching that damages OCR.
     */
    internal fun hasReasonablePerspectiveGeometry(page: DetectedPage): Boolean {
        val corners = page.corners
        if (corners.size != 4) return false

        // 1. Verify interior corner angles: must be reasonably close to 90 degrees (no extreme shear)
        for (i in 0 until 4) {
            val prev = corners[(i + 3) % 4]
            val curr = corners[i]
            val next = corners[(i + 1) % 4]

            val v1x = (prev.x - curr.x).toDouble()
            val v1y = (prev.y - curr.y).toDouble()
            val v2x = (next.x - curr.x).toDouble()
            val v2y = (next.y - curr.y).toDouble()

            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)
            if (len1 < 1e-4 || len2 < 1e-4) return false

            val cosTheta = (v1x * v2x + v1y * v2y) / (len1 * len2)
            // Cosine of 90 degrees is 0. If |cosTheta| > 0.65, angle is < 49° or > 131° (extreme shear)
            if (kotlin.math.abs(cosTheta) > 0.65) {
                return false
            }
        }

        // 2. Verify edge ratio symmetry: opposite edges of a book page should not differ excessively
        val tl = corners[0]
        val tr = corners[1]
        val br = corners[2]
        val bl = corners[3]

        val topEdge = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val bottomEdge = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val leftEdge = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val rightEdge = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())

        if (topEdge < 1e-4 || bottomEdge < 1e-4 || leftEdge < 1e-4 || rightEdge < 1e-4) return false

        val horizRatio = kotlin.math.max(topEdge, bottomEdge) / kotlin.math.min(topEdge, bottomEdge)
        val vertRatio = kotlin.math.max(leftEdge, rightEdge) / kotlin.math.min(leftEdge, rightEdge)

        // If one edge is more than 2.0x larger than the opposite edge, perspective is too extreme
        if (horizRatio > 2.0 || vertRatio > 2.0) {
            return false
        }

        return true
    }

    /**
     * Rectifies the detected page region from the captured [bitmap] into an upright rectangular [Bitmap].
     *
     * Unverified page boundaries (e.g., full-frame manual capture fallbacks) bypass perspective
     * rectification completely and return the unwarped image.
     *
     * @param bitmap High-resolution captured camera image.
     * @param page Detected page containing normalized [0.0, 1.0] boundary coordinates.
     * @return Rectified upright [Bitmap] ready for text recognition.
     */
    open fun rectifyPage(bitmap: Bitmap, page: DetectedPage): Bitmap {
        val startTime = System.currentTimeMillis()
        lastWasPerspectiveRectified = false
        try {
            val w = bitmap.width
            val h = bitmap.height

            // Full-frame fallbacks or unverified boundaries must not be processed as validated page quadrilaterals
            if (!shouldPerformPerspectiveRectification(page)) {
                if (!page.isVerifiedBoundary) {
                    Log.d(TAG, "Unverified page boundary (full-frame fallback); bypassing perspective rectification")
                } else {
                    Log.d(TAG, "Corners not convex/clockwise or sheared; using axis-aligned crop fallback")
                }
                return fallbackCrop(bitmap, page, w, h)
            }

            // Convert normalized corners to pixel coordinates
            val x0 = page.topLeft.x * w
            val y0 = page.topLeft.y * h

            val x1 = page.topRight.x * w
            val y1 = page.topRight.y * h

            val x2 = page.bottomRight.x * w
            val y2 = page.bottomRight.y * h

            val x3 = page.bottomLeft.x * w
            val y3 = page.bottomLeft.y * h

            // Compute natural physical dimensions of quadrilateral edges
            val topEdge = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
            val bottomEdge = hypot((x2 - x3).toDouble(), (y2 - y3).toDouble()).toFloat()
            val leftEdge = hypot((x3 - x0).toDouble(), (y3 - y0).toDouble()).toFloat()
            val rightEdge = hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()

            val targetWidth = max(topEdge, bottomEdge).toInt().coerceIn(320, w)
            val targetHeight = max(leftEdge, rightEdge).toInt().coerceIn(320, h)

            val src = floatArrayOf(
                x0, y0,
                x1, y1,
                x2, y2,
                x3, y3
            )

            val dst = floatArrayOf(
                0f, 0f,
                targetWidth.toFloat(), 0f,
                targetWidth.toFloat(), targetHeight.toFloat(),
                0f, targetHeight.toFloat()
            )

            val matrix = Matrix()
            val success = matrix.setPolyToPoly(src, 0, dst, 0, 4)

            if (success) {
                try {
                    val rectified = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(rectified)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }
                    canvas.drawBitmap(bitmap, matrix, paint)
                    lastWasPerspectiveRectified = true
                    return rectified
                } catch (t: Throwable) {
                    Log.w(TAG, "Perspective warp failed, falling back to rectangular bounding crop", t)
                }
            }

            return fallbackCrop(bitmap, page, w, h)
        } finally {
            lastProcessingDurationMs = System.currentTimeMillis() - startTime
        }
    }

    internal fun computeCropCoordinates(bounds: com.bibin.visioneye.ai.BoundingBox, w: Int, h: Int): IntArray {
        val cropLeft = (bounds.left * w).toInt().coerceIn(0, w - 1)
        val cropTop = (bounds.top * h).toInt().coerceIn(0, h - 1)
        val cropRight = (bounds.right * w).toInt().coerceIn(cropLeft + 1, w)
        val cropBottom = (bounds.bottom * h).toInt().coerceIn(cropTop + 1, h)
        return intArrayOf(cropLeft, cropTop, cropRight, cropBottom)
    }

    internal open fun cropBitmap(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun fallbackCrop(bitmap: Bitmap, page: DetectedPage, w: Int, h: Int): Bitmap {
        val coords = computeCropCoordinates(page.bounds, w, h)
        val cropLeft = coords[0]
        val cropTop = coords[1]
        val cropRight = coords[2]
        val cropBottom = coords[3]
        val cropWidth = (cropRight - cropLeft).coerceAtLeast(1)
        val cropHeight = (cropBottom - cropTop).coerceAtLeast(1)

        // For full-frame boundary, return source bitmap directly without redundant allocation
        if (cropLeft == 0 && cropTop == 0 && cropWidth == w && cropHeight == h) {
            return bitmap
        }

        return cropBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
    }

    companion object {
        private const val TAG = "PageImageProcessor"
    }
}
