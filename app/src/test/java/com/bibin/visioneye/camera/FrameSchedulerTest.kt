package com.bibin.visioneye.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSchedulerTest {

    @Test
    fun initialFrame_isProcessed() {
        val scheduler = FrameScheduler(targetFps = 5.0)
        val shouldProcess = scheduler.shouldProcess(currentTimeMs = 1000L)
        assertTrue(shouldProcess)
        scheduler.onAnalysisComplete()
    }

    @Test
    fun throttling_rejectsFramesWithinIntervalAndAcceptsAfterInterval() {
        val scheduler = FrameScheduler(targetFps = 5.0) // 200ms interval
        assertEquals(200L, scheduler.minIntervalMs)

        // First frame accepted
        assertTrue(scheduler.shouldProcess(currentTimeMs = 1000L))
        scheduler.onAnalysisComplete()

        // 50ms later -> rejected
        assertFalse(scheduler.shouldProcess(currentTimeMs = 1050L))
        // 199ms later -> rejected
        assertFalse(scheduler.shouldProcess(currentTimeMs = 1199L))
        // 200ms later -> accepted
        assertTrue(scheduler.shouldProcess(currentTimeMs = 1200L))
        scheduler.onAnalysisComplete()
    }

    @Test
    fun nonAccumulation_dropsFramesWhileAnalysisIsOngoing() {
        val scheduler = FrameScheduler(targetFps = 5.0)

        // Frame 1 accepted
        assertTrue(scheduler.shouldProcess(currentTimeMs = 1000L))

        // Analysis is still executing; even though interval (300ms > 200ms) has passed,
        // it must NOT accept frame 2, avoiding queued work accumulation
        assertFalse(scheduler.shouldProcess(currentTimeMs = 1300L))

        // Analysis completes
        scheduler.onAnalysisComplete()

        // Next frame after completion and interval is admitted
        assertTrue(scheduler.shouldProcess(currentTimeMs = 1301L))
        scheduler.onAnalysisComplete()
    }

    @Test
    fun targetFps_dynamicallyUpdatesMinInterval() {
        val scheduler = FrameScheduler(targetFps = 5.0)
        assertEquals(200L, scheduler.minIntervalMs)

        scheduler.targetFps = 10.0
        assertEquals(100L, scheduler.minIntervalMs)

        scheduler.targetFps = 2.0
        assertEquals(500L, scheduler.minIntervalMs)
    }

    @Test
    fun reset_restoresCleanState() {
        val scheduler = FrameScheduler(targetFps = 5.0)
        assertTrue(scheduler.shouldProcess(currentTimeMs = 1000L))

        // Reset clears last analyzed timestamp and processing lock
        scheduler.reset()

        // Can process immediately even if timestamp is earlier or same
        assertTrue(scheduler.shouldProcess(currentTimeMs = 500L))
        scheduler.onAnalysisComplete()
    }
}
