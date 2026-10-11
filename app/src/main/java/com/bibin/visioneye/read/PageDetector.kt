package com.bibin.visioneye.read

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight, on-device page and document boundary detector.
 *
 * Designed to answer: "Is there a recognizable page in front of the camera?"
 * Operates purely on image luminance and contrast geometry without requiring
 * external AI models, heavy native libraries, or deep learning inference.
 *
 * Supports single printed book pages with:
 * - Mild curvature near the spine
 * - Shadows and uneven lighting
 * - White or cream paper on light or varied backgrounds
 * - Text and illustrations near page margins
 *
 * @property minAreaRatio Minimum page area as a fraction of the frame (default: 0.12 = 12%).
 * @property maxAreaRatio Maximum page area as a fraction of the frame (default: 0.95 = 95%).
 * @property minFillDensity Minimum fraction of foreground pixels within the bounding box (default: 0.40).
 */
class PageDetector(
    val minAreaRatio: Float = 0.12f,
    val maxAreaRatio: Float = 0.95f,
    val minFillDensity: Float = 0.40f
) {
    /**
     * Diagnostic timing for the most recent detection pass in milliseconds.
     */
    var lastDetectionDurationMs: Long = 0L
        private set

    /**
     * Inspects the upright [bitmap] and detects a candidate page.
     *
     * @param bitmap Camera preview or analysis frame.
     * @return [DetectedPage] with normalized [0.0, 1.0] bounds and 4 corners, or null if no page detected.
     */
    fun detect(bitmap: Bitmap): DetectedPage? {
        val startTime = System.currentTimeMillis()
        try {
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
            val otsuThreshold = calculateOtsuThreshold(hist, totalPixels, sumLum)

            // Primary candidate evaluation
            val primaryCandidate = evaluateThresholdCandidate(lum, w, h, totalPixels, otsuThreshold)

            // If primary candidate is valid and has strong confidence / coverage, return it
            if (primaryCandidate != null && primaryCandidate.confidence >= 0.70f && primaryCandidate.areaRatio >= 0.20f) {
                return primaryCandidate
            }

            // Secondary pass: relaxed threshold to recover shadowed or cream pages on light backgrounds
            val shadowThreshold = (otsuThreshold - 25).coerceIn(45, 180)
            val shadowCandidate = if (shadowThreshold != otsuThreshold) {
                evaluateThresholdCandidate(lum, w, h, totalPixels, shadowThreshold)
            } else {
                null
            }

            val chosen = when {
                primaryCandidate == null -> shadowCandidate
                shadowCandidate == null -> primaryCandidate
                // If shadow candidate found significantly more of the shadowed page without exceeding bounds
                shadowCandidate.areaRatio > primaryCandidate.areaRatio * 1.25f &&
                        shadowCandidate.areaRatio <= maxAreaRatio -> shadowCandidate
                else -> primaryCandidate
            }

            return chosen
        } finally {
            lastDetectionDurationMs = System.currentTimeMillis() - startTime
        }
    }

    private fun evaluateThresholdCandidate(
        lum: IntArray,
        w: Int,
        h: Int,
        totalPixels: Int,
        threshold: Int
    ): DetectedPage? {
        // Build row and column projections
        val rowCounts = IntArray(h)
        val colCounts = IntArray(w)

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                if (lum[rowOffset + x] >= threshold) {
                    rowCounts[y]++
                    colCounts[x]++
                }
            }
        }

        // Apply 3-tap smoothing to eliminate single-line noise spikes
        val smoothCol = IntArray(w)
        for (x in 0 until w) {
            val prev = if (x > 0) colCounts[x - 1] else colCounts[x]
            val curr = colCounts[x]
            val next = if (x < w - 1) colCounts[x + 1] else colCounts[x]
            smoothCol[x] = (prev + 2 * curr + next) / 4
        }

        val smoothRow = IntArray(h)
        for (y in 0 until h) {
            val prev = if (y > 0) rowCounts[y - 1] else rowCounts[y]
            val curr = rowCounts[y]
            val next = if (y < h - 1) rowCounts[y + 1] else rowCounts[y]
            smoothRow[y] = (prev + 2 * curr + next) / 4
        }

        val maxCol = smoothCol.maxOrNull() ?: 0
        val maxRow = smoothRow.maxOrNull() ?: 0

        // Adaptive noise floor: accounts for book size and background variations
        val minColCount = max((h * 0.06f).toInt(), (maxCol * 0.12f).toInt())
        val minRowCount = max((w * 0.06f).toInt(), (maxRow * 0.12f).toInt())

        var minX = 0
        while (minX < w && smoothCol[minX] < minColCount) minX++

        var maxX = w - 1
        while (maxX >= 0 && smoothCol[maxX] < minColCount) maxX--

        var minY = 0
        while (minY < h && smoothRow[minY] < minRowCount) minY++

        var maxY = h - 1
        while (maxY >= 0 && smoothRow[maxY] < minRowCount) maxY--

        if (minX >= maxX || minY >= maxY) return null

        val boxWidth = maxX - minX + 1
        val boxHeight = maxY - minY + 1
        val boxArea = boxWidth * boxHeight
        val areaRatio = boxArea.toFloat() / totalPixels

        // Validate geometric constraints for a realistic page
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

        // Normalized bounding box coordinates
        val normLeft = (minX.toFloat() / w).coerceIn(0f, 1f)
        val normTop = (minY.toFloat() / h).coerceIn(0f, 1f)
        val normRight = ((maxX + 1).toFloat() / w).coerceIn(0f, 1f)
        val normBottom = ((maxY + 1).toFloat() / h).coerceIn(0f, 1f)

        val defaultTL = PagePoint(normLeft, normTop)
        val defaultTR = PagePoint(normRight, normTop)
        val defaultBR = PagePoint(normRight, normBottom)
        val defaultBL = PagePoint(normLeft, normBottom)

        // Refine corners using diagonal projection extrema within bounding box quadrants
        val midX = minX + boxWidth / 2
        val midY = minY + boxHeight / 2
        val slackX = (boxWidth * 0.10f).toInt()
        val slackY = (boxHeight * 0.10f).toInt()

        val tl = findExtremum(lum, threshold, w, minX..(midX + slackX), minY..(midY + slackY), ExtremumType.TOP_LEFT)
            ?: defaultTL
        val tr = findExtremum(lum, threshold, w, (midX - slackX)..maxX, minY..(midY + slackY), ExtremumType.TOP_RIGHT)
            ?: defaultTR
        val br = findExtremum(lum, threshold, w, (midX - slackX)..maxX, (midY - slackY)..maxY, ExtremumType.BOTTOM_RIGHT)
            ?: defaultBR
        val bl = findExtremum(lum, threshold, w, minX..(midX + slackX), (midY - slackY)..maxY, ExtremumType.BOTTOM_LEFT)
            ?: defaultBL

        val bounds = com.bibin.visioneye.ai.BoundingBox(normLeft, normTop, normRight, normBottom)
        val confidence = (fillDensity * 0.4f + (1f - min(normLeft, 1f - normRight)) * 0.3f + areaRatio * 0.3f)
            .coerceIn(0.5f, 0.99f)

        val candidatePage = DetectedPage(
            bounds = bounds,
            corners = listOf(tl, tr, br, bl),
            confidence = confidence,
            areaRatio = areaRatio
        )

        // Validate that the four corners form a geometrically valid clockwise convex quadrilateral
        return if (candidatePage.hasValidClockwiseConvexCorners()) {
            candidatePage
        } else {
            // Fall back to axis-aligned bounding box corners which are guaranteed convex & clockwise
            DetectedPage(
                bounds = bounds,
                corners = listOf(defaultTL, defaultTR, defaultBR, defaultBL),
                confidence = confidence * 0.90f,
                areaRatio = areaRatio
            )
        }
    }

    private enum class ExtremumType {
        TOP_LEFT,     // Minimizes x + y
        TOP_RIGHT,    // Maximizes x - y
        BOTTOM_RIGHT, // Maximizes x + y
        BOTTOM_LEFT   // Minimizes x - y
    }

    private fun findExtremum(
        lum: IntArray,
        threshold: Int,
        w: Int,
        xRange: IntProgression,
        yRange: IntProgression,
        type: ExtremumType
    ): PagePoint? {
        var bestX = -1
        var bestY = -1
        var bestScore = when (type) {
            ExtremumType.TOP_LEFT -> Int.MAX_VALUE
            ExtremumType.TOP_RIGHT -> Int.MIN_VALUE
            ExtremumType.BOTTOM_RIGHT -> Int.MIN_VALUE
            ExtremumType.BOTTOM_LEFT -> Int.MAX_VALUE
        }

        val h = lum.size / w

        for (y in yRange) {
            if (y !in 0 until h) continue
            val rowOffset = y * w
            for (x in xRange) {
                if (x !in 0 until w) continue
                if (lum[rowOffset + x] >= threshold) {
                    when (type) {
                        ExtremumType.TOP_LEFT -> {
                            val score = x + y
                            if (score < bestScore) {
                                bestScore = score
                                bestX = x
                                bestY = y
                            }
                        }
                        ExtremumType.TOP_RIGHT -> {
                            val score = x - y
                            if (score > bestScore) {
                                bestScore = score
                                bestX = x
                                bestY = y
                            }
                        }
                        ExtremumType.BOTTOM_RIGHT -> {
                            val score = x + y
                            if (score > bestScore) {
                                bestScore = score
                                bestX = x
                                bestY = y
                            }
                        }
                        ExtremumType.BOTTOM_LEFT -> {
                            val score = x - y
                            if (score < bestScore) {
                                bestScore = score
                                bestX = x
                                bestY = y
                            }
                        }
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

    internal fun calculateOtsuThreshold(hist: IntArray, total: Int, sumTotal: Long): Int {
        var sumB = 0L
        var wB = 0
        var maxVariance = 0.0
        var bestThresholdStart = 128
        var bestThresholdEnd = 128

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
                bestThresholdStart = t
                bestThresholdEnd = t
            } else if (betweenVariance == maxVariance && maxVariance > 0.0) {
                bestThresholdEnd = t
            }
        }

        // Center threshold across plateau of maximum variance to place divider midway in valley
        val bestThreshold = (bestThresholdStart + bestThresholdEnd) / 2 + 1
        // Clamp threshold to avoid extreme pitch black or saturated thresholds
        return bestThreshold.coerceIn(55, 205)
    }

    companion object {
        private const val TAG = "PageDetector"
    }
}

