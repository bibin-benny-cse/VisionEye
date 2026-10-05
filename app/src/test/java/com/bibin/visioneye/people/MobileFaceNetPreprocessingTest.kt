package com.bibin.visioneye.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests verifying MobileFaceNet preprocessing, normalization formulas,
 * L2 embedding normalization, cosine similarity calculations, and aspect-ratio-preserving square cropping.
 */
class MobileFaceNetPreprocessingTest {

    // =========================================================================
    // 1. Pixel Normalization & Value Range Tests
    // =========================================================================

    @Test
    fun normalizePixel_verifiesExpectedFormulaAndRange() {
        // Formula: normalized = (pixel - 127.5f) / 128.0f
        assertEquals(127.5f, MobileFaceNetEmbeddingModel.IMAGE_MEAN, 0.0001f)
        assertEquals(128.0f, MobileFaceNetEmbeddingModel.IMAGE_STD, 0.0001f)

        // Pixel = 0 -> (0 - 127.5) / 128 = -0.99609375
        val norm0 = MobileFaceNetEmbeddingModel.normalizePixel(0)
        assertEquals(-0.99609375f, norm0, 0.00001f)
        assertTrue("Normalized value must be >= -1.0", norm0 >= -1.0f)

        // Pixel = 127 -> (127 - 127.5) / 128 = -0.00390625
        val norm127 = MobileFaceNetEmbeddingModel.normalizePixel(127)
        assertEquals(-0.00390625f, norm127, 0.00001f)

        // Pixel = 128 -> (128 - 127.5) / 128 = +0.00390625
        val norm128 = MobileFaceNetEmbeddingModel.normalizePixel(128)
        assertEquals(0.00390625f, norm128, 0.00001f)

        // Pixel = 255 -> (255 - 127.5) / 128 = +0.99609375
        val norm255 = MobileFaceNetEmbeddingModel.normalizePixel(255)
        assertEquals(0.99609375f, norm255, 0.00001f)
        assertTrue("Normalized value must be <= 1.0", norm255 <= 1.0f)

        // Test full dynamic range [0..255]
        for (p in 0..255) {
            val norm = MobileFaceNetEmbeddingModel.normalizePixel(p)
            assertTrue("Value for pixel $p must be in [-1.0, 1.0]", norm in -1.0f..1.0f)
        }
    }

    // =========================================================================
    // 2. L2 Normalization Tests
    // =========================================================================

    @Test
    fun l2Normalize_producesUnitVector() {
        val vector3d = floatArrayOf(3.0f, 4.0f, 0.0f)
        val normalized = MobileFaceNetEmbeddingModel.l2Normalize(vector3d)

        assertEquals(0.6f, normalized[0], 0.0001f)
        assertEquals(0.8f, normalized[1], 0.0001f)
        assertEquals(0.0f, normalized[2], 0.0001f)

        var sumSq = 0f
        for (v in normalized) sumSq += v * v
        assertEquals(1.0f, sqrt(sumSq), 0.0001f)
    }

    @Test
    fun l2Normalize_full192Embedding_producesUnitNorm() {
        val raw192 = FloatArray(MobileFaceNetEmbeddingModel.EMBEDDING_SIZE) { i ->
            (i % 17 - 8).toFloat()
        }
        val normalized = MobileFaceNetEmbeddingModel.l2Normalize(raw192)

        var sumSq = 0f
        for (v in normalized) sumSq += v * v
        val norm = sqrt(sumSq)
        assertEquals(1.0f, norm, 0.0001f)
    }

    @Test
    fun l2Normalize_idempotent() {
        val raw = FloatArray(192) { i -> (i + 1).toFloat() }
        val pass1 = MobileFaceNetEmbeddingModel.l2Normalize(raw)
        val pass2 = MobileFaceNetEmbeddingModel.l2Normalize(pass1)

        for (i in pass1.indices) {
            assertEquals(pass1[i], pass2[i], 0.00001f)
        }
    }

    @Test
    fun l2Normalize_zeroVector_returnsZeroWithoutNaN() {
        val zero = FloatArray(192) { 0.0f }
        val result = MobileFaceNetEmbeddingModel.l2Normalize(zero)
        for (v in result) {
            assertFalse(v.isNaN())
            assertEquals(0.0f, v, 0.00001f)
        }
    }

    // =========================================================================
    // 3. Cosine Similarity Calculation Tests
    // =========================================================================

    @Test
    fun cosineSimilarity_identicalVectors_returnsOne() {
        val vec = MobileFaceNetEmbeddingModel.l2Normalize(FloatArray(192) { (it * 3).toFloat() })
        val sim = MobileFaceNetEmbeddingModel.cosineSimilarity(vec, vec)
        assertEquals(1.0f, sim, 0.0001f)
    }

    @Test
    fun cosineSimilarity_orthogonalVectors_returnsZero() {
        val u = FloatArray(192) { 0f }.apply { this[0] = 1.0f }
        val v = FloatArray(192) { 0f }.apply { this[1] = 1.0f }
        val sim = MobileFaceNetEmbeddingModel.cosineSimilarity(u, v)
        assertEquals(0.0f, sim, 0.0001f)
    }

    @Test
    fun cosineSimilarity_oppositeVectors_returnsMinusOne() {
        val u = FloatArray(192) { 0f }.apply { this[0] = 1.0f }
        val v = FloatArray(192) { 0f }.apply { this[0] = -1.0f }
        val sim = MobileFaceNetEmbeddingModel.cosineSimilarity(u, v)
        assertEquals(-1.0f, sim, 0.0001f)
    }

    @Test
    fun cosineSimilarity_thresholdEvaluation_matchesConfigDefault() {
        val config = PeopleRecognitionConfig()
        assertEquals(0.70f, config.similarityThreshold, 0.0001f)

        // 45-degree angle vectors: dot product = cos(45 deg) ~ 0.7071f
        val u = floatArrayOf(1.0f, 0.0f)
        val v = floatArrayOf(sqrt(0.5f), sqrt(0.5f))
        val sim = MobileFaceNetEmbeddingModel.cosineSimilarity(u, v)
        assertEquals(0.7071f, sim, 0.001f)
        assertTrue("cos(45 deg) ~ 0.7071 should meet threshold 0.70f", sim >= config.similarityThreshold)

        // 60-degree angle vectors: dot product = cos(60 deg) = 0.50f
        val w = floatArrayOf(0.5f, sqrt(0.75f))
        val sim60 = MobileFaceNetEmbeddingModel.cosineSimilarity(u, w)
        assertEquals(0.50f, sim60, 0.001f)
        assertFalse("cos(60 deg) = 0.50 should not meet threshold 0.70f", sim60 >= config.similarityThreshold)
    }

    // =========================================================================
    // 4. Square Face Crop & Aspect Ratio Tests
    // =========================================================================

    @Test
    fun computeSquareCropBounds_portraitRect_expandsHorizontallyToSquare() {
        // Typical portrait face rect: 100 wide x 160 high
        val bounds = PeopleCoordinator.computeSquareCropBounds(
            imageWidth = 640,
            imageHeight = 480,
            faceLeft = 200f,
            faceTop = 100f,
            faceRight = 300f,
            faceBottom = 260f
        )
        assertNotNull(bounds)
        bounds?.let {
            assertTrue("Crop must be square to prevent geometric facial distortion", it.isSquare)
            assertEquals(160, it.width)
            assertEquals(160, it.height)
            // Center X = 250, side = 160 -> left = 170, right = 330
            assertEquals(170, it.left)
            assertEquals(100, it.top)
            assertEquals(330, it.right)
            assertEquals(260, it.bottom)
        }
    }

    @Test
    fun computeSquareCropBounds_landscapeRect_expandsVerticallyToSquare() {
        // Wide face rect: 160 wide x 100 high
        val bounds = PeopleCoordinator.computeSquareCropBounds(
            imageWidth = 640,
            imageHeight = 480,
            faceLeft = 200f,
            faceTop = 100f,
            faceRight = 360f,
            faceBottom = 200f
        )
        assertNotNull(bounds)
        bounds?.let {
            assertTrue("Crop must be square", it.isSquare)
            assertEquals(160, it.width)
            assertEquals(160, it.height)
            // Center Y = 150, side = 160 -> top = 70, bottom = 230
            assertEquals(200, it.left)
            assertEquals(70, it.top)
            assertEquals(360, it.right)
            assertEquals(230, it.bottom)
        }
    }

    @Test
    fun computeSquareCropBounds_nearLeftEdge_shiftsRightToPreserveSquare() {
        // Face close to left boundary: center X = 60, height = 150 (side = 150, halfSide = 75)
        // Without shift: left would be 60 - 75 = -15 (clipped).
        // With shift: shifts right by 15px so left = 0, right = 150.
        val bounds = PeopleCoordinator.computeSquareCropBounds(
            imageWidth = 640,
            imageHeight = 480,
            faceLeft = 10f,
            faceTop = 100f,
            faceRight = 110f,
            faceBottom = 250f
        )
        assertNotNull(bounds)
        bounds?.let {
            assertTrue("Crop must remain square even near image boundaries", it.isSquare)
            assertEquals(150, it.width)
            assertEquals(150, it.height)
            assertEquals(0, it.left)
            assertEquals(150, it.right)
            assertEquals(100, it.top)
            assertEquals(250, it.bottom)
        }
    }

    @Test
    fun computeSquareCropBounds_nearRightEdge_shiftsLeftToPreserveSquare() {
        // Face close to right boundary: width = 100, height = 150
        val bounds = PeopleCoordinator.computeSquareCropBounds(
            imageWidth = 640,
            imageHeight = 480,
            faceLeft = 530f,
            faceTop = 100f,
            faceRight = 630f,
            faceBottom = 250f
        )
        assertNotNull(bounds)
        bounds?.let {
            assertTrue("Crop must remain square near right edge", it.isSquare)
            assertEquals(150, it.width)
            assertEquals(150, it.height)
            assertEquals(640, it.right)
            assertEquals(490, it.left)
        }
    }

    @Test
    fun computeSquareCropBounds_tooSmallFace_returnsNull() {
        val bounds = PeopleCoordinator.computeSquareCropBounds(
            imageWidth = 640,
            imageHeight = 480,
            faceLeft = 100f,
            faceTop = 100f,
            faceRight = 115f,
            faceBottom = 115f
        )
        assertNull("Tiny faces (<20px) should be rejected for recognition safety", bounds)
    }
}
