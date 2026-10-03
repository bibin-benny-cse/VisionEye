package com.bibin.visioneye.ai

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.bibin.visioneye.camera.FrameAnalyzer
import com.bibin.visioneye.fusion.decision.AlertCandidate
import com.bibin.visioneye.fusion.decision.DecisionEngine
import com.bibin.visioneye.fusion.decision.DefaultDecisionEngine
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Diagnostic and development output state for YOLO object detection and alert selection.
 *
 * Clearly demarcated as development information to verify model inference,
 * schema validation, and frame delivery during testing.
 *
 * @property isReady True if model is loaded and ready to process incoming frames.
 * @property statusCode Distinct diagnostic status identifier.
 * @property statusMessage Clean, non-technical status summary suitable for UI display.
 * @property diagnosticDetails Optional detailed diagnostic or recovery information for developers.
 * @property inferenceTimeMs Latency in milliseconds for the most recent frame analysis.
 * @property detections Immutable list of detected objects with normalized bounding boxes.
 * @property lastInferenceTimestamp Epoch timestamp when the last frame was processed.
 * @property selectedAlerts Context-aware prioritized alerts chosen by the [DecisionEngine].
 * @property suppressedAlertCount Count of detections suppressed by cooldown or capacity limit.
 * @property primaryAlert The top-ranked active alert candidate, if any.
 * @property alertStatus High-level alert status indicator ("ACTIVE", "COOLDOWN", "IDLE").
 * @property ttsStatus Current TextToSpeech status string.
 */
data class YoloDebugState(
    val isReady: Boolean = false,
    val statusCode: DetectorStatusCode = DetectorStatusCode.INITIALIZING,
    val statusMessage: String = "INITIALIZING",
    val diagnosticDetails: String? = null,
    val inferenceTimeMs: Long = 0L,
    val detections: List<Detection> = emptyList(),
    val lastInferenceTimestamp: Long = 0L,
    val selectedAlerts: List<AlertCandidate> = emptyList(),
    val suppressedAlertCount: Int = 0,
    val primaryAlert: AlertCandidate? = selectedAlerts.firstOrNull(),
    val alertStatus: String = if (selectedAlerts.isNotEmpty()) "ACTIVE" else if (detections.isNotEmpty()) "COOLDOWN" else "IDLE",
    val ttsStatus: String = "READY"
)

/**
 * Connects the CameraX frame analysis pipeline to [ObjectDetector] and [DecisionEngine].
 *
 * Implements [FrameAnalyzer] to consume scheduled frames at the configured rate (~5 FPS),
 * offloaded to a background thread, arbitrates alerts via [decisionEngine], triggers
 * acoustic feedback via [speechController], and streams detection results to [yoloState].
 */
class YoloFrameAnalyzer(
    private val detector: ObjectDetector,
    val decisionEngine: DecisionEngine = DefaultDecisionEngine(),
    val speechController: SpeechController? = null
) : FrameAnalyzer {

    private val _yoloState = MutableStateFlow(YoloDebugState())
    val yoloState: StateFlow<YoloDebugState> = _yoloState.asStateFlow()

    init {
        // Initialize detector when analyzer is created
        val success = detector.initialize()
        updateStatusFromDetectorState(success)
    }

    override fun analyze(imageProxy: ImageProxy) {
        val detectorState = detector.state.value
        if (detectorState !is DetectorState.Ready) {
            updateStatusFromDetectorState(false)
            return
        }

        val startTime = SystemClock.uptimeMillis()
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()

            val detections = detector.detect(bitmap, rotationDegrees)
            val elapsedMs = SystemClock.uptimeMillis() - startTime

            // Execute rule-based context-aware decision engine with temporal stabilization
            val decisionResult = decisionEngine.process(detections)

            // Trigger speech for newly selected active guidance alerts
            if (decisionResult.selectedAlerts.isNotEmpty()) {
                val primaryAlert = decisionResult.selectedAlerts.first()
                speechController?.speak(primaryAlert.message, SpeechPriority.NORMAL)
            }

            // Check if detector encountered an inference error
            val currentDetectorState = detector.state.value
            if (currentDetectorState is DetectorState.InferenceError) {
                _yoloState.value = YoloDebugState(
                    isReady = false,
                    statusCode = DetectorStatusCode.INFERENCE_ERROR,
                    statusMessage = currentDetectorState.userStatusMessage,
                    diagnosticDetails = currentDetectorState.diagnosticDetails,
                    inferenceTimeMs = elapsedMs
                )
                return
            }

            _yoloState.value = YoloDebugState(
                isReady = true,
                statusCode = DetectorStatusCode.MODEL_READY,
                statusMessage = "READY",
                diagnosticDetails = null,
                inferenceTimeMs = elapsedMs,
                detections = detections,
                lastInferenceTimestamp = System.currentTimeMillis(),
                selectedAlerts = decisionResult.selectedAlerts,
                suppressedAlertCount = decisionResult.suppressedCount
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error executing YOLO frame analysis", t)
            _yoloState.value = _yoloState.value.copy(
                isReady = false,
                statusCode = DetectorStatusCode.INFERENCE_ERROR,
                statusMessage = "INFERENCE ERROR",
                diagnosticDetails = t.localizedMessage ?: "Unknown analysis failure"
            )
        }
    }

    private fun updateStatusFromDetectorState(initSuccess: Boolean) {
        val state = detector.state.value
        _yoloState.value = YoloDebugState(
            isReady = state is DetectorState.Ready,
            statusCode = state.statusCode,
            statusMessage = state.userStatusMessage,
            diagnosticDetails = state.diagnosticDetails
        )
    }

    /**
     * Resets detection output state when camera stops or mode changes.
     */
    fun reset() {
        decisionEngine.resetCooldowns()
        speechController?.stop()
        val state = detector.state.value
        _yoloState.value = YoloDebugState(
            isReady = state is DetectorState.Ready,
            statusCode = state.statusCode,
            statusMessage = state.userStatusMessage,
            diagnosticDetails = state.diagnosticDetails
        )
    }

    companion object {
        private const val TAG = "YoloFrameAnalyzer"
    }
}

