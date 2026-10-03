package com.bibin.visioneye.read

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight, on-device page and document boundary detector.
 *
 * Designed to answer: "Is there a recognizable page in front of the camera?"
 * Operates purely on image luminance and contrast geometry without requiring
 * external AI models, heavy native libraries, or deep learning inference.
 *
 * @property minAreaRatio Minimum page area as a fraction of the frame (default: 0.15 = 15%).
 * @property maxAreaRatio Maximum page area as a fraction of the frame (default: 0.92 = 92%).
 * @property minFillDensity Minimum fraction of foreground pixels within the bounding box (default: 0.60).
 */
class PageDetector(
    val minAreaRatio: Float = 0.15f,
    val maxAreaRatio: Float = 0.92f,
    val minFillDensity: Float = 0.60f
) {

    /**
     * Inspects the upright [bitmap] and detects a candidate page.
     *
     * @param bitmap Camera preview or analysis frame.
     * @return [DetectedPage] with normalized [0.0, 1.0] bounds and 4 corners, or null if no page detected.
     */
    fun detect(bitmap: Bitmap): DetectedPage? {
        if (bitmap.isRecycled || bitmap.width < 32 || bitmap.height < 32) return null

        // Downscale to ~240px wide for rapid preview processing (< 5ms)
        val targetW = 240
        val targetH = (targetW * (bitmap.height.toFloat() / bitmap.width)).toInt().coerceAtLeast(32)

        val scaled = if (bitmap.width > targetW * 1.5) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, false)
        } else {
            bitmap
        }

        val w = scaled.width
        val h = scaled.height
        val totalPixels = w * h

        val pixels = IntArray(totalPixels)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        if (scaled != bitmap) {
            scaled.recycle()
        }

        // 1. Calculate luminance array and 256-bin histogram
        val lum = IntArray(totalPixels)
        val hist = IntArray(256)
        var sumLum = 0L

        for (i in 0 until totalPixels) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = (299 * r + 587 * g + 114 * b) / 1000
            lum[i] = y
            hist[y]++
            sumLum += y
        }

        // 2. Otsu's thresholding to find optimal contrast boundary
        val threshold = calculateOtsuThreshold(hist, totalPixels, sumLum)

        // 3. Scan row and column projections of pixels exceeding threshold (bright page on background)
        val rowCounts = IntArray(h)
        val colCounts = IntArray(w)

        var totalForeground = 0
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                if (lum[rowOffset + x] >= threshold) {
                    rowCounts[y]++
                    colCounts[x]++
                    totalForeground++
                }
            }
        }

        // Noise floor: require row/col to have significant foreground pixels
        val minColCount = (h * 0.10f).toInt()
        val minRowCount = (w * 0.10f).toInt()

        var minX = 0
        while (minX < w && colCounts[minX] < minColCount) minX++

        var maxX = w - 1
        while (maxX >= 0 && colCounts[maxX] < minColCount) maxX--

        var minY = 0
        while (minY < h && rowCounts[minY] < minRowCount) minY++

        var maxY = h - 1
        while (maxY >= 0 && rowCounts[maxY] < minRowCount) maxY--

        if (minX >= maxX || minY >= maxY) return null

        val boxWidth = maxX - minX + 1
        val boxHeight = maxY - minY + 1
        val boxArea = boxWidth * boxHeight
        val areaRatio = boxArea.toFloat() / totalPixels

        // 4. Validate geometric constraints for a realistic page
        if (areaRatio < minAreaRatio || areaRatio > maxAreaRatio) {
            return null
        }

        val aspectRatio = boxWidth.toFloat() / boxHeight.toFloat()
        if (aspectRatio < 0.35f || aspectRatio > 2.8f) {
            return null
        }

        // Count foreground pixels inside the bounding box to check density
        var inBoxForeground = 0
        for (y in minY..maxY) {
            val rowOffset = y * w
            for (x in minX..maxX) {
                if (lum[rowOffset + x] >= threshold) {
                    inBoxForeground++
                }
            }
        }

        val fillDensity = inBoxForeground.toFloat() / boxArea
        if (fillDensity < minFillDensity) {
            return null
        }

        // 5. Estimate 4 corner vertices (TL, TR, BR, BL) in normalized coordinates
        val normLeft = (minX.toFloat() / w).coerceIn(0f, 1f)
        val normTop = (minY.toFloat() / h).coerceIn(0f, 1f)
        val normRight = ((maxX + 1).toFloat() / w).coerceIn(0f, 1f)
        val normBottom = ((maxY + 1).toFloat() / h).coerceIn(0f, 1f)

        // Refine corners by locating extreme foreground points in each quadrant
        val midX = minX + boxWidth / 2
        val midY = minY + boxHeight / 2

        val tl = findExtremum(lum, threshold, w, minX until midX, minY until midY, isMinX = true, isMinY = true)
            ?: PagePoint(normLeft, normTop)
        val tr = findExtremum(lum, threshold, w, midX..maxX, minY until midY, isMinX = false, isMinY = true)
            ?: PagePoint(normRight, normTop)
        val br = findExtremum(lum, threshold, w, midX..maxX, midY..maxY, isMinX = false, isMinY = false)
            ?: PagePoint(normRight, normBottom)
        val bl = findExtremum(lum, threshold, w, minX until midX, midY..maxY, isMinX = true, isMinY = false)
            ?: PagePoint(normLeft, normBottom)

        val corners = listOf(tl, tr, br, bl)
        val bounds = com.bibin.visioneye.ai.BoundingBox(normLeft, normTop, normRight, normBottom)
        val confidence = (fillDensity * 0.5f + (1f - (min(normLeft, 1f - normRight))) * 0.5f).coerceIn(0.5f, 0.99f)

        return DetectedPage(
            bounds = bounds,
            corners = corners,
            confidence = confidence,
            areaRatio = areaRatio
        )
    }

    private fun findExtremum(
        lum: IntArray,
        threshold: Int,
        w: Int,
        xRange: IntProgression,
        yRange: IntProgression,
        isMinX: Boolean,
        isMinY: Boolean
    ): PagePoint? {
        var bestX = -1
        var bestY = -1
        var bestScore = Float.MAX_VALUE

        val h = lum.size / w

        for (y in yRange) {
            val rowOffset = y * w
            for (x in xRange) {
                if (lum[rowOffset + x] >= threshold) {
                    val dx = if (isMinX) x.toFloat() else (w - x).toFloat()
                    val dy = if (isMinY) y.toFloat() else (h - y).toFloat()
                    val distSq = dx * dx + dy * dy
                    if (distSq < bestScore) {
                        bestScore = distSq
                        bestX = x
                        bestY = y
                    }
                }
            }
        }

        return if (bestX != -1) {
            PagePoint((bestX.toFloat() / w).coerceIn(0f, 1f), (bestY.toFloat() / h).coerceIn(0f, 1f))
        } else {
            null
        }
    }

    private fun calculateOtsuThreshold(hist: IntArray, total: Int, sumTotal: Long): Int {
        var sumB = 0L
        var wB = 0
        var maxVariance = 0.0
        var bestThreshold = 128

        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += (t * hist[t]).toLong()
            val mB = sumB.toDouble() / wB
            val mF = (sumTotal - sumB).toDouble() / wF

            val betweenVariance = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (betweenVariance > maxVariance) {
                maxVariance = betweenVariance
                bestThreshold = t
            }
        }

        // Clamp threshold to avoid extreme pitch black or saturated thresholds
        return bestThreshold.coerceIn(60, 200)
    }
}
