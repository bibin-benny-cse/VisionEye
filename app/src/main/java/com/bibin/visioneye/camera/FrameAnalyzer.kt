package com.bibin.visioneye.camera

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic metrics captured for each analyzed camera frame.
 */
data class FrameAnalysisDiagnostics(
    val timestampMs: Long = 0L,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val analyzedFrameCount: Long = 0L,
    val approximateFps: Double = 0.0
)

/**
 * Interface for consuming raw camera frames for AI inference.
 *
 * Designed for future AI pipelines (e.g. YOLOv8n object detection, depth estimation).
 * Frame processing runs on a dedicated background analysis executor to ensure the UI
 * thread is never blocked.
 */
fun interface FrameAnalyzer {
    /**
     * Called on a background executor when a scheduled camera frame is available.
     *
     * @param imageProxy The raw CameraX image buffer. Do NOT manually close
     *                   this instance; [FrameAnalysisDispatcher] automatically
     *                   closes it in a finally block to prevent buffer starvation.
     */
    fun analyze(imageProxy: ImageProxy)
}

/**
 * Central CameraX [ImageAnalysis.Analyzer] that throttles incoming frames using
 * [FrameScheduler], delegates admitted frames to registered [FrameAnalyzer]s,
 * computes live diagnostic metrics, and safely closes every [ImageProxy] in a finally block.
 */
class FrameAnalysisDispatcher(
    val scheduler: FrameScheduler = FrameScheduler(targetFps = 5.0)
) : ImageAnalysis.Analyzer {

    private val analyzers = CopyOnWriteArrayList<FrameAnalyzer>()

    private val _diagnostics = MutableStateFlow(FrameAnalysisDiagnostics())
    val diagnostics: StateFlow<FrameAnalysisDiagnostics> = _diagnostics.asStateFlow()

    private val frameCount = AtomicLong(0L)
    private var lastAnalyzedTimeMs: Long = 0L
    private var currentEstimatedFps: Double = 0.0

    /**
     * Registers a new frame analyzer listener for downstream inference.
     */
    fun addAnalyzer(analyzer: FrameAnalyzer) {
        analyzers.addIfAbsent(analyzer)
    }

    /**
     * Removes an existing frame analyzer listener.
     */
    fun removeAnalyzer(analyzer: FrameAnalyzer) {
        analyzers.remove(analyzer)
    }

    /**
     * Clears all registered frame analyzers.
     */
    fun clearAnalyzers() {
        analyzers.clear()
    }

    /**
     * Resets diagnostic counters and FPS tracking.
     */
    fun resetDiagnostics() {
        frameCount.set(0L)
        lastAnalyzedTimeMs = 0L
        currentEstimatedFps = 0.0
        _diagnostics.value = FrameAnalysisDiagnostics()
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val now = SystemClock.uptimeMillis()
            if (scheduler.shouldProcess(now)) {
                try {
                    recordFrameDiagnostics(imageProxy.width, imageProxy.height, now)
                    if (analyzers.isNotEmpty()) {
                        for (analyzer in analyzers) {
                            try {
                                analyzer.analyze(imageProxy)
                            } catch (t: Throwable) {
                                Log.e(TAG, "Frame analyzer threw an exception during processing", t)
                            }
                        }
                    }
                } finally {
                    scheduler.onAnalysisComplete()
                }
            }
        } finally {
            // CRITICAL: Guaranteed closure on ALL execution paths (scheduled, skipped, or failed)
            imageProxy.close()
        }
    }

    private fun recordFrameDiagnostics(width: Int, height: Int, currentTimeMs: Long) {
        val totalCount = frameCount.incrementAndGet()

        if (lastAnalyzedTimeMs > 0L) {
            val dtSeconds = (currentTimeMs - lastAnalyzedTimeMs) / 1000.0
            if (dtSeconds > 0.0) {
                val instantFps = 1.0 / dtSeconds
                currentEstimatedFps = if (currentEstimatedFps == 0.0) {
                    instantFps
                } else {
                    // Exponential moving average for smooth FPS representation
                    0.25 * instantFps + 0.75 * currentEstimatedFps
                }
            }
        }
        lastAnalyzedTimeMs = currentTimeMs

        _diagnostics.value = FrameAnalysisDiagnostics(
            timestampMs = currentTimeMs,
            imageWidth = width,
            imageHeight = height,
            analyzedFrameCount = totalCount,
            approximateFps = currentEstimatedFps
        )
    }

    companion object {
        private const val TAG = "FrameDispatcher"
    }
}
