package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.LetterboxTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for YOLO11n output decoding and Non-Maximum Suppression (NMS).
 * Verifies that competing classes for the same physical note are suppressed,
 * while genuinely distinct overlapping banknotes are preserved.
 */
class CurrencyDecoderAndNmsTest {

    private val identityTransform = LetterboxTransform(
        scale = 1.0f,
        padX = 0f,
        padY = 0f,
        originalWidth = 640,
        originalHeight = 640,
        targetWidth = 640,
        targetHeight = 640
    )

    private val config = CurrencyDetectorConfig(
        confidenceThreshold = 0.45f,
        iouThreshold = 0.45f,
        maxDetections = 10
    )

    @Test
    fun decodeCurrencyOutput_channelsFirst_decodesAccurately() {
        // Output shape: [10, 8400]
        val output = Array(10) { FloatArray(8400) }

        // Anchor 42: ₹500 note (class 5) at center with size 320x160 (in model 640x640 space)
        output[0][42] = 320f // cx
        output[1][42] = 320f // cy
        output[2][42] = 320f // w
        output[3][42] = 160f // h
        // Scores: class 0..4 = 0.05, class 5 = 0.95
        output[4][42] = 0.02f
        output[5][42] = 0.01f
        output[6][42] = 0.03f
        output[7][42] = 0.04f
        output[8][42] = 0.01f
        output[9][42] = 0.95f // Class 5 = FIVE_HUNDRED

        val candidates = CurrencyPostProcessor.decodeCurrencyOutput(
            output = output,
            transform = identityTransform,
            isChannelsFirst = true,
            outputDim1 = 10,
            outputDim2 = 8400,
            config = config
        )

        assertEquals(1, candidates.size)
        val note = candidates[0]
        assertEquals(CurrencyDenomination.FIVE_HUNDRED, note.denomination)
        assertEquals(0.95f, note.confidence, 0.001f)
        assertEquals(0.25f, note.box.left, 0.001f) // (320 - 160) / 640
        assertEquals(0.75f, note.box.right, 0.001f) // (320 + 160) / 640
        assertEquals(0.375f, note.box.top, 0.001f) // (320 - 80) / 640
        assertEquals(0.625f, note.box.bottom, 0.001f) // (320 + 80) / 640
    }

    @Test
    fun decodeCurrencyOutput_belowConfidenceThreshold_discarded() {
        val output = Array(10) { FloatArray(8400) }

        // Anchor 10: max confidence 0.35 (below 0.45)
        output[0][10] = 320f
        output[1][10] = 320f
        output[2][10] = 100f
        output[3][10] = 100f
        output[4][10] = 0.35f // Class 0 = TEN, but below 0.45

        val candidates = CurrencyPostProcessor.decodeCurrencyOutput(
            output = output,
            transform = identityTransform,
            isChannelsFirst = true,
            outputDim1 = 10,
            outputDim2 = 8400,
            config = config
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun nms_suppressesCompetingClassesForSamePhysicalNote() {
        // Two anchors predict the same physical banknote:
        // Prediction A: ₹10 (Class 0) with confidence 0.90
        // Prediction B: ₹50 (Class 4) with confidence 0.65 (almost identical bounding box)
        val candidateA = CandidateNote(
            denomination = CurrencyDenomination.TEN,
            confidence = 0.90f,
            box = BoundingBox(left = 0.20f, top = 0.30f, right = 0.80f, bottom = 0.70f)
        )
        val candidateB = CandidateNote(
            denomination = CurrencyDenomination.FIFTY,
            confidence = 0.65f,
            box = BoundingBox(left = 0.21f, top = 0.29f, right = 0.79f, bottom = 0.71f)
        )

        val detections = CurrencyPostProcessor.applyNms(
            candidates = listOf(candidateA, candidateB),
            iouThreshold = config.iouThreshold,
            maxDetections = config.maxDetections
        )

        // Competing ₹50 prediction must be suppressed, leaving only the ₹10 note
        assertEquals(1, detections.size)
        assertEquals(CurrencyDenomination.TEN, detections[0].denomination)
        assertEquals(0.90f, detections[0].confidence, 0.001f)
    }

    @Test
    fun nms_suppressesDuplicateSameClassPredictions() {
        val candidate1 = CandidateNote(
            denomination = CurrencyDenomination.ONE_HUNDRED,
            confidence = 0.92f,
            box = BoundingBox(left = 0.10f, top = 0.10f, right = 0.50f, bottom = 0.50f)
        )
        val candidate2 = CandidateNote(
            denomination = CurrencyDenomination.ONE_HUNDRED,
            confidence = 0.75f,
            box = BoundingBox(left = 0.12f, top = 0.11f, right = 0.51f, bottom = 0.52f)
        )

        val detections = CurrencyPostProcessor.applyNms(
            candidates = listOf(candidate1, candidate2),
            iouThreshold = config.iouThreshold,
            maxDetections = config.maxDetections
        )

        assertEquals(1, detections.size)
        assertEquals(CurrencyDenomination.ONE_HUNDRED, detections[0].denomination)
        assertEquals(0.92f, detections[0].confidence, 0.001f)
    }

    @Test
    fun nms_preservesGenuinelyDistinctOverlappingNotes() {
        // Two genuine banknotes partially overlapping (e.g. ₹100 note lying partially over ₹500 note)
        // Note 1: [0.10, 0.10, 0.50, 0.50] (area = 0.16)
        // Note 2: [0.35, 0.35, 0.75, 0.75] (overlap = [0.35..0.50, 0.35..0.50] = 0.15 * 0.15 = 0.0225)
        // IoU = 0.0225 / (0.16 + 0.16 - 0.0225) = 0.0225 / 0.2975 ≈ 0.075 (< 0.40 threshold)
        val note1 = CandidateNote(
            denomination = CurrencyDenomination.ONE_HUNDRED,
            confidence = 0.88f,
            box = BoundingBox(left = 0.10f, top = 0.10f, right = 0.50f, bottom = 0.50f)
        )
        val note2 = CandidateNote(
            denomination = CurrencyDenomination.FIVE_HUNDRED,
            confidence = 0.91f,
            box = BoundingBox(left = 0.35f, top = 0.35f, right = 0.75f, bottom = 0.75f)
        )

        val detections = CurrencyPostProcessor.applyNms(
            candidates = listOf(note1, note2),
            iouThreshold = config.iouThreshold,
            maxDetections = config.maxDetections
        )

        // Both distinct notes must be preserved!
        assertEquals(2, detections.size)
        val denominations = detections.map { it.denomination }.toSet()
        assertTrue(denominations.contains(CurrencyDenomination.ONE_HUNDRED))
        assertTrue(denominations.contains(CurrencyDenomination.FIVE_HUNDRED))
    }

    @Test
    fun nms_respectsMaxDetectionsCap() {
        val candidates = (0 until 15).map { index ->
            CandidateNote(
                denomination = CurrencyDenomination.TWENTY,
                confidence = 0.50f + (index * 0.02f),
                box = BoundingBox(
                    left = (index * 0.05f),
                    top = 0.1f,
                    right = (index * 0.05f) + 0.04f,
                    bottom = 0.2f
                )
            )
        }

        val detections = CurrencyPostProcessor.applyNms(
            candidates = candidates,
            iouThreshold = config.iouThreshold,
            maxDetections = 5
        )

        assertEquals(5, detections.size)
    }
}
