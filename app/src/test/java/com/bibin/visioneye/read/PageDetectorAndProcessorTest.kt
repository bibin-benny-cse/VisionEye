package com.bibin.visioneye.read

import com.bibin.visioneye.ai.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PageDetectorAndProcessorTest {

    private lateinit var detector: PageDetector
    private lateinit var processor: PageImageProcessor

    @Before
    fun setUp() {
        detector = PageDetector()
        processor = PageImageProcessor()
    }

    @Test
    fun otsuThreshold_highContrastBrightPageOnDarkBackground_calculatesIntermediateThreshold() {
        // 1000 dark background pixels around value 40, 1000 bright page pixels around value 200
        val hist = IntArray(256)
        hist[40] = 1000
        hist[200] = 1000
        val total = 2000
        val sumTotal = 1000L * 40L + 1000L * 200L

        val threshold = detector.calculateOtsuThreshold(hist, total, sumTotal)

        assertTrue("Threshold should cleanly separate dark background and bright page", threshold in 41..199)
        assertEquals(120, threshold)
    }

    @Test
    fun otsuThreshold_creamPaperOnLightBackground_thresholdWithinValidRange() {
        // 1000 pixels at 150 (cream paper), 1000 pixels at 220 (light table)
        val hist = IntArray(256)
        hist[150] = 1000
        hist[220] = 1000
        val total = 2000
        val sumTotal = 1000L * 150L + 1000L * 220L

        val threshold = detector.calculateOtsuThreshold(hist, total, sumTotal)
        assertTrue("Threshold should lie between the two peaks", threshold in 151..219)
    }

    @Test
    fun otsuThreshold_darkOrDimlyLitScene_clampsToMinimumFloor() {
        // Scene with very low luminance (cluster at 20 and 40)
        val hist = IntArray(256)
        hist[20] = 1000
        hist[40] = 1000
        val total = 2000
        val sumTotal = 1000L * 20L + 1000L * 40L

        val threshold = detector.calculateOtsuThreshold(hist, total, sumTotal)
        assertTrue("Threshold must respect minimum clamp (>= 55)", threshold >= 55)
    }

    @Test
    fun computeCropCoordinates_standardCenteredPage_mapsCorrectPixelCoordinates() {
        val bounds = BoundingBox(left = 0.10f, top = 0.20f, right = 0.80f, bottom = 0.90f)
        val imageW = 1000
        val imageH = 2000

        val coords = processor.computeCropCoordinates(bounds, imageW, imageH)
        val cropLeft = coords[0]
        val cropTop = coords[1]
        val cropRight = coords[2]
        val cropBottom = coords[3]

        assertEquals(100, cropLeft)
        assertEquals(400, cropTop)
        assertEquals(800, cropRight)
        assertEquals(1800, cropBottom)
        assertEquals(700, cropRight - cropLeft)
        assertEquals(1400, cropBottom - cropTop)
    }

    @Test
    fun computeCropCoordinates_fullFrameBounds_mapsToZeroAndMaxDimensions() {
        val bounds = BoundingBox(left = 0.0f, top = 0.0f, right = 1.0f, bottom = 1.0f)
        val imageW = 1920
        val imageH = 1080

        val coords = processor.computeCropCoordinates(bounds, imageW, imageH)
        assertEquals(0, coords[0])
        assertEquals(0, coords[1])
        assertEquals(1920, coords[2])
        assertEquals(1080, coords[3])
    }

    @Test
    fun computeCropCoordinates_outOfBoundsInputs_safelyClampedWithinImageLimits() {
        val bounds = BoundingBox(left = -0.50f, top = -0.20f, right = 1.50f, bottom = 1.20f)
        val imageW = 800
        val imageH = 1200

        val coords = processor.computeCropCoordinates(bounds, imageW, imageH)
        assertEquals(0, coords[0])
        assertEquals(0, coords[1])
        assertEquals(800, coords[2])
        assertEquals(1200, coords[3])
    }

    @Test
    fun shouldPerformPerspectiveRectification_fullFrameFallback_returnsFalse() {
        val fullFrame = DetectedPage.fullFrame()
        assertFalse(
            "Unverified full-frame fallback must not qualify for perspective rectification",
            processor.shouldPerformPerspectiveRectification(fullFrame)
        )
    }

    @Test
    fun shouldPerformPerspectiveRectification_unverifiedBoundary_returnsFalse() {
        val unverifiedPage = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            corners = listOf(
                PagePoint(0.1f, 0.1f),
                PagePoint(0.9f, 0.1f),
                PagePoint(0.9f, 0.9f),
                PagePoint(0.1f, 0.9f)
            ),
            confidence = 0.95f,
            areaRatio = 0.64f,
            isVerifiedBoundary = false
        )

        assertFalse(
            "Unverified page boundary must be rejected from perspective rectification",
            processor.shouldPerformPerspectiveRectification(unverifiedPage)
        )
    }

    @Test
    fun shouldPerformPerspectiveRectification_verifiedClockwiseQuad_returnsTrue() {
        val verifiedPage = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            corners = listOf(
                PagePoint(0.1f, 0.1f),
                PagePoint(0.9f, 0.1f),
                PagePoint(0.9f, 0.9f),
                PagePoint(0.1f, 0.9f)
            ),
            confidence = 0.95f,
            areaRatio = 0.64f,
            isVerifiedBoundary = true
        )

        assertTrue(
            "Verified page with valid convex corners must qualify for perspective rectification",
            processor.shouldPerformPerspectiveRectification(verifiedPage)
        )
    }

    @Test
    fun shouldPerformPerspectiveRectification_verifiedDegenerateCorners_returnsFalse() {
        // Collinear corners
        val degeneratePage = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            corners = listOf(
                PagePoint(0.1f, 0.1f),
                PagePoint(0.5f, 0.1f),
                PagePoint(0.9f, 0.1f),
                PagePoint(0.1f, 0.9f)
            ),
            confidence = 0.85f,
            areaRatio = 0.30f,
            isVerifiedBoundary = true
        )

        assertFalse(
            "Degenerate corner geometry must be rejected from perspective rectification",
            processor.shouldPerformPerspectiveRectification(degeneratePage)
        )
    }

    @Test
    fun shouldPerformPerspectiveRectification_severelyShearedCorners_returnsFalse() {
        // Severe perspective shear: 33.7° acute and 146.3° obtuse corners (|cosTheta| = 0.83 > 0.65)
        val shearedPage = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.3f),
            corners = listOf(
                PagePoint(0.1f, 0.1f),
                PagePoint(0.6f, 0.1f),
                PagePoint(0.9f, 0.3f),
                PagePoint(0.4f, 0.3f)
            ),
            confidence = 0.85f,
            areaRatio = 0.40f,
            isVerifiedBoundary = true
        )

        assertFalse(
            "Severely sheared corner geometry must be rejected from perspective rectification to avoid stretching text",
            processor.shouldPerformPerspectiveRectification(shearedPage)
        )
    }

    @Test
    fun shouldPerformPerspectiveRectification_highlyAsymmetricEdges_returnsFalse() {
        // Trapezoid where bottom edge is > 2.5x the top edge
        val trapezoidPage = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            corners = listOf(
                PagePoint(0.4f, 0.1f),
                PagePoint(0.6f, 0.1f), // Top edge width = 0.2
                PagePoint(0.9f, 0.9f),
                PagePoint(0.1f, 0.9f)  // Bottom edge width = 0.8 (4x ratio)
            ),
            confidence = 0.85f,
            areaRatio = 0.40f,
            isVerifiedBoundary = true
        )

        assertFalse(
            "Trapezoidal quad with > 2x edge asymmetry must be rejected to prevent extreme stretching",
            processor.shouldPerformPerspectiveRectification(trapezoidPage)
        )
    }
}
