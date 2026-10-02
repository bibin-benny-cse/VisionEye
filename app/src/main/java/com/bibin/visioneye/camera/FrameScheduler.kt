package com.bibin.visioneye.camera

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Throttles camera frames to a configurable target rate (e.g. 5 FPS)
 * and guarantees that delayed analysis work never accumulates.
 *
 * @param targetFps The desired analysis rate in frames per second (default: 5.0).
 */
class FrameScheduler(
    targetFps: Double = 5.0
) {
    /**
     * Target frames-per-second rate. Adjusting this updates the minimum interval between frames.
     */
    var targetFps: Double = targetFps
        set(value) {
            field = value
            minIntervalMs = if (value > 0.0) (1000.0 / value).toLong() else 0L
        }

    /**
     * Minimum interval in milliseconds required between consecutive frame analysis executions.
     */
    var minIntervalMs: Long = if (targetFps > 0.0) (1000.0 / targetFps).toLong() else 200L
        private set

    private var lastAnalyzedTimestampMs: Long = 0L
    private var hasAnalyzedFirstFrame: Boolean = false
    private val isProcessing = AtomicBoolean(false)

    /**
     * Determines whether an incoming frame at [currentTimeMs] should be analyzed.
     *
     * Returns true ONLY if:
     * 1. Sufficient time has passed since the last analyzed frame (>= [minIntervalMs]).
     * 2. No previous frame analysis is currently executing in the background pipeline.
     *
     * If true is returned, the caller MUST call [onAnalysisComplete] once frame processing finishes.
     */
    fun shouldProcess(currentTimeMs: Long = SystemClock.uptimeMillis()): Boolean {
        if (hasAnalyzedFirstFrame && (currentTimeMs - lastAnalyzedTimestampMs < minIntervalMs)) {
            return false
        }

        // Avoid accumulating delayed work: drop immediately if previous analysis is still ongoing
        if (!isProcessing.compareAndSet(false, true)) {
            return false
        }

        lastAnalyzedTimestampMs = currentTimeMs
        hasAnalyzedFirstFrame = true
        return true
    }

    /**
     * Releases the processing lock after frame processing completes.
     */
    fun onAnalysisComplete() {
        isProcessing.set(false)
    }

    /**
     * Resets the scheduler state when camera pipeline stops or restarts.
     */
    fun reset() {
        lastAnalyzedTimestampMs = 0L
        hasAnalyzedFirstFrame = false
        isProcessing.set(false)
    }
}
