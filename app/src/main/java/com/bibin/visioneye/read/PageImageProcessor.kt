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
class PageImageProcessor {

    /**
     * Rectifies the detected page region from the captured [bitmap] into an upright rectangular [Bitmap].
     *
     * @param bitmap High-resolution captured camera image.
     * @param page Detected page containing normalized [0.0, 1.0] boundary coordinates.
     * @return Rectified upright [Bitmap] ready for text recognition.
     */
    fun rectifyPage(bitmap: Bitmap, page: DetectedPage): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

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

        val targetWidth = max(topEdge, bottomEdge).toInt().coerceIn(120, w)
        val targetHeight = max(leftEdge, rightEdge).toInt().coerceIn(120, h)

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
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                canvas.drawBitmap(bitmap, matrix, paint)
                return rectified
            } catch (t: Throwable) {
                Log.w(TAG, "Perspective warp failed, falling back to rectangular bounding crop", t)
            }
        }

        // Fallback: simple axis-aligned rectangular crop
        val cropLeft = (page.bounds.left * w).toInt().coerceIn(0, w - 1)
        val cropTop = (page.bounds.top * h).toInt().coerceIn(0, h - 1)
        val cropRight = (page.bounds.right * w).toInt().coerceIn(cropLeft + 1, w)
        val cropBottom = (page.bounds.bottom * h).toInt().coerceIn(cropTop + 1, h)

        return Bitmap.createBitmap(
            bitmap,
            cropLeft,
            cropTop,
            cropRight - cropLeft,
            cropBottom - cropTop
        )
    }

    companion object {
        private const val TAG = "PageImageProcessor"
    }
}
