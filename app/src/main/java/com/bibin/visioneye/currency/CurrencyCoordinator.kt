package com.bibin.visioneye.currency

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.bibin.visioneye.ai.DetectorState
import com.bibin.visioneye.ai.DetectorStatusCode
import com.bibin.visioneye.camera.FrameAnalyzer
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central coordinator for VisionEye Currency Mode.
 *
 * Implements the on-device Indian banknote recognition pipeline:
 * [Camera Frame] -> [Preprocessing 640x640 NCHW] -> [YOLO11n TFLite Inference] -> [Class-Agnostic NMS]
 * -> [Temporal Confirmation across 2 qualifying frames] -> [Vocalized Utterance via SpeechController].
 *
 * @param detector Dedicated on-device currency detector.
 * @param decisionEngine Temporal arbitrator and speech deduplication engine.
 * @param speechController Audio feedback synthesizer.
 */
class CurrencyCoordinator(
    val detector: CurrencyBanknoteDetector,
    val decisionEngine: CurrencyDecisionEngine = DefaultCurrencyDecisionEngine(),
    private val speechController: SpeechController? = null
) {
    constructor(
        context: Context,
        speechController: SpeechController? = null,
        decisionEngine: CurrencyDecisionEngine = DefaultCurrencyDecisionEngine()
    ) : this(
        detector = CurrencyDetector(context),
        decisionEngine = decisionEngine,
        speechController = speechController
    )

    private val _currencyState = MutableStateFlow(CurrencyState())
    val currencyState: StateFlow<CurrencyState> = _currencyState.asStateFlow()

    private var isCoordinatorActive = false
    private val lock = Any()

    /**
     * Frame analyzer registered with CameraX [com.bibin.visioneye.camera.FrameAnalysisDispatcher]
     * active only while in [com.bibin.visioneye.core.mode.VisionMode.CURRENCY].
     */
    val frameAnalyzer = FrameAnalyzer { imageProxy ->
        synchronized(lock) {
            if (!isCoordinatorActive) return@FrameAnalyzer
        }

        val detectorState = detector.state.value
        if (detectorState !is DetectorState.Ready) {
            updateStatusFromDetector(false)
            return@FrameAnalyzer
        }

        val startTime = SystemClock.uptimeMillis()
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()

            // 1. Detect banknotes in frame
            val detections = detector.detectCurrency(bitmap, rotationDegrees)
            val elapsedMs = SystemClock.uptimeMillis() - startTime

            // 2. Evaluate temporal confirmation and absence reset
            val decisionResult = decisionEngine.process(detections, System.currentTimeMillis())

            // 3. Trigger voice announcement when a new confirmed state is reached
            decisionResult.spokenAlert?.let { alertText ->
                Log.i(TAG, "Vocalizing confirmed currency announcement: '$alertText'")
                speechController?.speak(alertText, SpeechPriority.NORMAL)
            }

            // 4. Update observable UI state
            _currencyState.value = CurrencyState(
                isReady = true,
                statusCode = DetectorStatusCode.MODEL_READY,
                statusMessage = decisionResult.statusMessage,
                diagnosticDetails = null,
                detections = detections,
                confirmedDenominations = decisionResult.confirmedDenominations,
                isConfirmed = decisionResult.isConfirmed,
                lastSpokenAnnouncement = decisionResult.spokenAlert ?: _currencyState.value.lastSpokenAnnouncement,
                inferenceTimeMs = elapsedMs,
                lastInferenceTimestamp = System.currentTimeMillis()
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error executing currency frame analysis", t)
            _currencyState.value = _currencyState.value.copy(
                isReady = false,
                statusCode = DetectorStatusCode.INFERENCE_ERROR,
                statusMessage = "INFERENCE ERROR",
                diagnosticDetails = t.localizedMessage ?: "Unknown currency analysis failure"
            )
        }
    }

    /**
     * Activates currency scanning when user transitions into Currency Mode.
     */
    fun activate() {
        synchronized(lock) {
            isCoordinatorActive = true
            decisionEngine.reset()
            val initSuccess = detector.initialize()
            updateStatusFromDetector(initSuccess)
            Log.d(TAG, "CurrencyCoordinator activated.")
        }
    }

    /**
     * Deactivates currency scanning, cancels active speech, and resets state.
     */
    fun deactivate() {
        synchronized(lock) {
            isCoordinatorActive = false
            decisionEngine.reset()
            speechController?.stop()
            _currencyState.value = CurrencyState(
                isReady = false,
                statusCode = DetectorStatusCode.INITIALIZING,
                statusMessage = "IDLE",
                diagnosticDetails = null
            )
            Log.d(TAG, "CurrencyCoordinator deactivated.")
        }
    }

    /**
     * Resets temporal confirmation and speech history without tearing down model.
     */
    fun reset() {
        synchronized(lock) {
            decisionEngine.reset()
            speechController?.stop()
            updateStatusFromDetector(detector.state.value is DetectorState.Ready)
        }
    }

    /**
     * Completely releases native detector resources upon app shutdown.
     */
    fun release() {
        deactivate()
        detector.close()
    }

    private fun updateStatusFromDetector(ready: Boolean) {
        val state = detector.state.value
        _currencyState.value = CurrencyState(
            isReady = state is DetectorState.Ready,
            statusCode = state.statusCode,
            statusMessage = if (state is DetectorState.Ready) "SCANNING FOR BANKNOTE..." else state.userStatusMessage,
            diagnosticDetails = state.diagnosticDetails
        )
    }

    companion object {
        private const val TAG = "CurrencyCoordinator"
    }
}
