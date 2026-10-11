package com.bibin.visioneye.read

import com.bibin.visioneye.ai.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedPageGeometryTest {

    @Test
    fun standardUprightCorners_validClockwiseConvex() {
        val page = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            corners = listOf(
                PagePoint(0.1f, 0.1f), // TL
                PagePoint(0.9f, 0.1f), // TR
                PagePoint(0.9f, 0.9f), // BR
                PagePoint(0.1f, 0.9f)  // BL
            ),
            confidence = 0.95f,
            areaRatio = 0.64f
        )

        assertTrue(page.hasValidClockwiseConvexCorners())
        assertEquals(PagePoint(0.1f, 0.1f), page.topLeft)
        assertEquals(PagePoint(0.9f, 0.1f), page.topRight)
        assertEquals(PagePoint(0.9f, 0.9f), page.bottomRight)
        assertEquals(PagePoint(0.1f, 0.9f), page.bottomLeft)
    }

    @Test
    fun slightlyRotatedPage_validPerspectiveClockwiseConvex() {
        // ~10 degree clockwise tilt: TL=(0.15, 0.10), TR=(0.85, 0.18), BR=(0.78, 0.90), BL=(0.08, 0.82)
        val rotatedPage = DetectedPage(
            bounds = BoundingBox(0.08f, 0.10f, 0.85f, 0.90f),
            corners = listOf(
                PagePoint(0.15f, 0.10f),
                PagePoint(0.85f, 0.18f),
                PagePoint(0.78f, 0.90f),
                PagePoint(0.08f, 0.82f)
            ),
            confidence = 0.88f,
            areaRatio = 0.55f
        )

        assertTrue(rotatedPage.hasValidClockwiseConvexCorners())
    }

    @Test
    fun counterClockwiseOrdering_rejectedAsInvalid() {
        // Reversed vertices (TL, BL, BR, TR) -> counter-clockwise
        val ccwPage = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            corners = listOf(
                PagePoint(0.1f, 0.1f),
                PagePoint(0.1f, 0.9f),
                PagePoint(0.9f, 0.9f),
                PagePoint(0.9f, 0.1f)
            ),
            confidence = 0.9f,
            areaRatio = 0.64f
        )

        assertFalse(ccwPage.hasValidClockwiseConvexCorners())
    }

    @Test
    fun selfIntersectingBowtie_rejectedAsInvalid() {
        // Self-intersecting bowtie polygon
        val bowtiePage = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            corners = listOf(
                PagePoint(0.1f, 0.1f),
                PagePoint(0.9f, 0.9f), // Crossed to BR
                PagePoint(0.9f, 0.1f), // Crossed to TR
                PagePoint(0.1f, 0.9f)
            ),
            confidence = 0.5f,
            areaRatio = 0.40f
        )

        assertFalse(bowtiePage.hasValidClockwiseConvexCorners())
    }

    @Test
    fun collinearCorners_rejectedAsDegenerate() {
        val collinearPage = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            corners = listOf(
                PagePoint(0.1f, 0.1f),
                PagePoint(0.5f, 0.1f),
                PagePoint(0.9f, 0.1f), // Collinear edge
                PagePoint(0.1f, 0.9f)
            ),
            confidence = 0.5f,
            areaRatio = 0.30f
        )

        assertFalse(collinearPage.hasValidClockwiseConvexCorners())
    }

    @Test
    fun fullFrameFactory_createsNormalizedUnverifiedFallback() {
        val fullFrame = DetectedPage.fullFrame()

        assertFalse("Full-frame fallback must not be marked as a verified page boundary", fullFrame.isVerifiedBoundary)
        assertFalse("Full-frame fallback must not be treated as a valid page quadrilateral", fullFrame.isValidPageQuadrilateral)
        assertEquals(0.0f, fullFrame.confidence, 0.001f)
        assertEquals(1.0f, fullFrame.areaRatio, 0.001f)
        assertEquals(0f, fullFrame.bounds.left, 0.001f)
        assertEquals(0f, fullFrame.bounds.top, 0.001f)
        assertEquals(1f, fullFrame.bounds.right, 0.001f)
        assertEquals(1f, fullFrame.bounds.bottom, 0.001f)
    }

    @Test
    fun verifiedDetectedPage_isValidPageQuadrilateral() {
        val page = DetectedPage(
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

        assertTrue(page.isVerifiedBoundary)
        assertTrue(page.isValidPageQuadrilateral)
    }
}
