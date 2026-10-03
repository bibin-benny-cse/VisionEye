package com.bibin.visioneye.read

import com.bibin.visioneye.ai.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PageStabilityTrackerTest {

    private lateinit var tracker: PageStabilityTracker

    @Before
    fun setUp() {
        tracker = PageStabilityTracker(
            requiredStableFrames = 4,
            maxCenterDrift = 0.05f,
            maxAreaVariation = 0.12f
        )
    }

    private fun createPage(
        left: Float = 0.1f,
        top: Float = 0.1f,
        right: Float = 0.9f,
        bottom: Float = 0.9f,
        areaRatio: Float = 0.64f
    ): DetectedPage {
        return DetectedPage(
            bounds = BoundingBox(left, top, right, bottom),
            corners = listOf(
                PagePoint(left, top),
                PagePoint(right, top),
                PagePoint(right, bottom),
                PagePoint(left, bottom)
            ),
            confidence = 0.9f,
            areaRatio = areaRatio
        )
    }

    @Test
    fun update_withNullPage_returnsNotStableAndResetsProgress() {
        val result = tracker.update(null)

        assertFalse(result.isStable)
        assertEquals(0.0f, result.progress, 0.001f)
        assertNull(result.page)
        assertEquals(0, tracker.stableCount)
    }

    @Test
    fun update_firstCandidateFrame_initializesProgressToOneQuarter() {
        val page = createPage()
        val result = tracker.update(page)

        assertFalse(result.isStable)
        assertEquals(0.25f, result.progress, 0.001f)
        assertEquals(1, tracker.stableCount)
        assertEquals(page, result.page)
    }

    @Test
    fun update_consecutiveStableFrames_reachesStabilityAtRequiredCount() {
        val page1 = createPage()
        val page2 = createPage(left = 0.11f, right = 0.91f) // 1% drift
        val page3 = createPage(left = 0.10f, right = 0.90f)
        val page4 = createPage(left = 0.12f, right = 0.92f)

        val res1 = tracker.update(page1)
        assertFalse(res1.isStable)
        assertEquals(0.25f, res1.progress, 0.001f)

        val res2 = tracker.update(page2)
        assertFalse(res2.isStable)
        assertEquals(0.50f, res2.progress, 0.001f)

        val res3 = tracker.update(page3)
        assertFalse(res3.isStable)
        assertEquals(0.75f, res3.progress, 0.001f)

        val res4 = tracker.update(page4)
        assertTrue(res4.isStable)
        assertEquals(1.0f, res4.progress, 0.001f)
        assertEquals(4, tracker.stableCount)
    }

    @Test
    fun update_centerDriftExceedingThreshold_resetsStabilityCounter() {
        val page1 = createPage(left = 0.1f, right = 0.5f) // center = 0.3
        tracker.update(page1)
        tracker.update(page1)
        assertEquals(2, tracker.stableCount)

        // Abrupt horizontal shift: center shifts to 0.45 (15% drift > 5% threshold)
        val shiftedPage = createPage(left = 0.25f, right = 0.65f)
        val result = tracker.update(shiftedPage)

        assertFalse(result.isStable)
        assertEquals(1, tracker.stableCount)
        assertEquals(0.25f, result.progress, 0.001f)
    }

    @Test
    fun update_areaVariationExceedingThreshold_resetsStabilityCounter() {
        val page1 = createPage(areaRatio = 0.50f)
        tracker.update(page1)
        tracker.update(page1)
        assertEquals(2, tracker.stableCount)

        // Rapid zoom/distance change: area increases to 0.70f (28% change > 12% threshold)
        val zoomedPage = createPage(areaRatio = 0.70f)
        val result = tracker.update(zoomedPage)

        assertFalse(result.isStable)
        assertEquals(1, tracker.stableCount)
        assertEquals(0.25f, result.progress, 0.001f)
    }

    @Test
    fun update_nullFrameAfterStableProgress_resetsToZero() {
        val page = createPage()
        tracker.update(page)
        tracker.update(page)
        assertEquals(2, tracker.stableCount)

        val nullResult = tracker.update(null)
        assertFalse(nullResult.isStable)
        assertEquals(0.0f, nullResult.progress, 0.001f)
        assertEquals(0, tracker.stableCount)
    }

    @Test
    fun reset_clearsStabilityState() {
        val page = createPage()
        tracker.update(page)
        tracker.update(page)
        assertEquals(2, tracker.stableCount)

        tracker.reset()
        assertEquals(0, tracker.stableCount)

        val nextResult = tracker.update(page)
        assertEquals(1, tracker.stableCount)
        assertEquals(0.25f, nextResult.progress, 0.001f)
    }
}
