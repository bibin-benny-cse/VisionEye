package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.HorizontalPosition
import com.bibin.visioneye.ai.LetterboxTransform
import kotlin.math.max
import kotlin.math.min

/**
 * Candidate note detection before NMS filtering.
 */
data class CandidateNote(
    val denomination: CurrencyDenomination,
    val confidence: Float,
    val box: BoundingBox
)

/**
 * Post-processing routines for YOLO11n Indian Currency output decoding and Non-Maximum Suppression.
 * Separated to allow comprehensive unit testing on host JVMs without Android dependencies.
 */
object CurrencyPostProcessor {

    /**
     * Decodes the raw YOLO tensor output into [CandidateNote] bounding boxes.
     */
    fun decodeCurrencyOutput(
        output: Array<FloatArray>,
        transform: LetterboxTransform,
        isChannelsFirst: Boolean,
        outputDim1: Int,
        outputDim2: Int,
        config: CurrencyDetectorConfig
    ): List<CandidateNote> {
        val candidates = mutableListOf<CandidateNote>()

        if (isChannelsFirst) {
            // Shape: [10, 8400] -> rows are [cx, cy, w, h, class0...class5]
            val numBoxes = outputDim2
            val numClasses = outputDim1 - 4 // Exactly 6

            for (b in 0 until numBoxes) {
                var maxScore = 0f
                var maxClassId = -1

                for (c in 0 until numClasses) {
                    val score = output[4 + c][b]
                    if (score > maxScore) {
                        maxScore = score
                        maxClassId = c
                    }
                }

                if (maxScore >= config.confidenceThreshold) {
                    val denomination = CurrencyDenomination.fromClassId(maxClassId)
                    if (denomination != null) {
                        var cx = output[0][b]
                        var cy = output[1][b]
                        var w = output[2][b]
                        var h = output[3][b]

                        // If coordinates are normalized [0.0, 1.0], convert to model pixel space
                        if (cx <= 1.0f && w <= 1.0f && config.inputWidth > 10) {
                            cx *= config.inputWidth
                            cy *= config.inputHeight
                            w *= config.inputWidth
                            h *= config.inputHeight
                        }

                        val normalizedBox = transform.mapBoxToNormalized(cx, cy, w, h)
                        candidates.add(CandidateNote(denomination, maxScore, normalizedBox))
                    }
                }
            }
        } else {
            // Shape: [8400, 10] -> rows are anchors, columns are [cx, cy, w, h, class0...class5]
            val numBoxes = outputDim1
            val numClasses = outputDim2 - 4

            for (b in 0 until numBoxes) {
                var maxScore = 0f
                var maxClassId = -1

                for (c in 0 until numClasses) {
                    val score = output[b][4 + c]
                    if (score > maxScore) {
                        maxScore = score
                        maxClassId = c
                    }
                }

                if (maxScore >= config.confidenceThreshold) {
                    val denomination = CurrencyDenomination.fromClassId(maxClassId)
                    if (denomination != null) {
                        var cx = output[b][0]
                        var cy = output[b][1]
                        var w = output[b][2]
                        var h = output[b][3]

                        if (cx <= 1.0f && w <= 1.0f && config.inputWidth > 10) {
                            cx *= config.inputWidth
                            cy *= config.inputHeight
                            w *= config.inputWidth
                            h *= config.inputHeight
                        }

                        val normalizedBox = transform.mapBoxToNormalized(cx, cy, w, h)
                        candidates.add(CandidateNote(denomination, maxScore, normalizedBox))
                    }
                }
            }
        }

        return candidates
    }

    /**
     * Non-Maximum Suppression tailored for currency detection:
     * - Prevents competing class predictions for the same physical note (suppresses if IoU is high or containment is high).
     * - Does NOT suppress genuinely distinct overlapping banknotes (where IoU is moderate and centers are distinct).
     */
    fun applyNms(
        candidates: List<CandidateNote>,
        iouThreshold: Float,
        maxDetections: Int,
        timestampMs: Long = System.currentTimeMillis()
    ): List<CurrencyDetection> {
        if (candidates.isEmpty()) return emptyList()

        // Sort descending by prediction confidence
        val sorted = candidates.sortedByDescending { it.confidence }
        val selected = mutableListOf<CandidateNote>()

        for (candidate in sorted) {
            if (selected.size >= maxDetections) break

            var shouldSelect = true
            for (chosen in selected) {
                val iou = calculateIoU(chosen.box, candidate.box)
                val ioMin = calculateIntersectionOverMinArea(chosen.box, candidate.box)

                if (chosen.denomination == candidate.denomination) {
                    // Same class duplicate suppression
                    if (iou >= iouThreshold) {
                        shouldSelect = false
                        break
                    }
                } else {
                    // Different classes competing for the same physical banknote:
                    // If IoU is high or one box largely covers the other, suppress the lower confidence one.
                    if (iou >= 0.40f || ioMin >= 0.65f) {
                        shouldSelect = false
                        break
                    }
                }
            }

            if (shouldSelect) {
                selected.add(candidate)
            }
        }

        return selected.map { note ->
            CurrencyDetection(
                denomination = note.denomination,
                confidence = note.confidence,
                boundingBox = note.box,
                position = HorizontalPosition.fromNormalizedX(note.box.centerX),
                timestampMs = timestampMs
            )
        }
    }

    fun calculateIoU(a: BoundingBox, b: BoundingBox): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    fun calculateIntersectionOverMinArea(a: BoundingBox, b: BoundingBox): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val minArea = min(areaA, areaB)

        return if (minArea > 0f) interArea / minArea else 0f
    }
}
