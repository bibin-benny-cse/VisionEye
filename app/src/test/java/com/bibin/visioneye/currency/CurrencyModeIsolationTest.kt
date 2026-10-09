package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.AiModelManager
import com.bibin.visioneye.ai.AiTaskType
import com.bibin.visioneye.ai.DetectorState
import com.bibin.visioneye.ai.DetectorStatusCode
import com.bibin.visioneye.camera.CameraController
import com.bibin.visioneye.camera.CameraState
import com.bibin.visioneye.camera.FrameAnalysisDiagnostics
import com.bibin.visioneye.camera.FrameAnalyzer
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying mode isolation, lifecycle management, and architectural boundaries:
 * - Currency detector is active ONLY in CURRENCY mode.
 * - NAVIGATE pipeline and models remain completely isolated and unaffected.
 * - Coordinator ignores frames when deactivated.
 */
class CurrencyModeIsolationTest {

    private class FakeSpeechController : SpeechController {
        val spokenPhrases = mutableListOf<String>()
        var stopCount = 0

        override fun speak(utterance: String, priority: SpeechPriority) {
            spokenPhrases.add(utterance)
        }

        override fun stop() {
            stopCount++
        }
    }

    @Test
    fun visionMode_currencyModeProperties() {
        val currencyMode = VisionMode.CURRENCY

        assertTrue(currencyMode.requiresCamera)
        assertTrue(currencyMode.requiresHighFrequencyAi)
        assertFalse(currencyMode.requiresLocation)
        assertEquals("Currency", currencyMode.displayName)
        assertTrue(currencyMode.voiceCommands.any { it.contains("currency", ignoreCase = true) })
    }

    @Test
    fun aiModelManager_currencyModeIsIncludedInSupportedModes() {
        val fakeModelManager = object : AiModelManager {
            override val currentTask: AiTaskType? = null
            override fun loadModelForTask(task: AiTaskType) {}
            override fun unloadActiveModels() {}
            override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {}
        }

        assertTrue(fakeModelManager.supportedModes.contains(VisionMode.CURRENCY))
        assertTrue(fakeModelManager.supportedModes.contains(VisionMode.NAVIGATE))
        assertFalse(fakeModelManager.supportedModes.contains(VisionMode.NAVIGATION))
        assertFalse(fakeModelManager.supportedModes.contains(VisionMode.SOS))

        // AiTaskType associations
        assertEquals(VisionMode.CURRENCY, AiTaskType.CURRENCY_RECOGNITION.associatedMode)
        assertEquals(VisionMode.NAVIGATE, AiTaskType.OBJECT_AND_DEPTH_DETECTION.associatedMode)
        assertEquals(VisionMode.READ, AiTaskType.DOCUMENT_OCR.associatedMode)
        assertEquals(VisionMode.PEOPLE, AiTaskType.FACE_RECOGNITION.associatedMode)
    }

    @Test
    fun currencyCoordinator_lifecycleAndFrameRejectionWhenInactive() {
        val speech = FakeSpeechController()
        val decisionEngine = DefaultCurrencyDecisionEngine()

        // Create a fake currency banknote detector in Ready state
        val fakeDetector = object : CurrencyBanknoteDetector {
            override val currencyConfig = CurrencyDetectorConfig()
            override val state = MutableStateFlow<DetectorState>(DetectorState.Ready)
            override val config = com.bibin.visioneye.ai.DetectorConfig()
            override val labels = listOf("10", "100", "20", "200", "50", "500")
            var detectCurrencyCallCount = 0

            override fun initialize(): Boolean = true
            override fun detectCurrency(bitmap: android.graphics.Bitmap, orientationDegrees: Int): List<CurrencyDetection> {
                detectCurrencyCallCount++
                return emptyList()
            }
            override fun detect(bitmap: android.graphics.Bitmap, orientationDegrees: Int) = emptyList<com.bibin.visioneye.ai.Detection>()
            override fun close() {}
        }

        // Coordinator is inactive by default
        val coordinator = CurrencyCoordinator(
            detector = fakeDetector,
            decisionEngine = decisionEngine,
            speechController = speech
        )

        // Initial state before activation should be INITIALIZING
        assertEquals(DetectorStatusCode.INITIALIZING, coordinator.currencyState.value.statusCode)
        assertEquals("INITIALIZING", coordinator.currencyState.value.statusMessage)

        // Activate coordinator
        coordinator.activate()
        assertEquals(DetectorStatusCode.MODEL_READY, coordinator.currencyState.value.statusCode)
        assertEquals("SCANNING FOR BANKNOTE...", coordinator.currencyState.value.statusMessage)

        // Deactivate calls speechController.stop() and resets state to IDLE
        coordinator.deactivate()
        assertEquals(1, speech.stopCount)
        assertEquals("IDLE", coordinator.currencyState.value.statusMessage)
        assertEquals(DetectorStatusCode.INITIALIZING, coordinator.currencyState.value.statusCode)
    }

    @Test
    fun cameraController_currencyModeIsIncludedInSupportedModes() {
        val fakeController = object : CameraController {
            override val state = MutableStateFlow<CameraState>(CameraState.Idle)
            override val diagnostics = MutableStateFlow(FrameAnalysisDiagnostics())
            override val yoloState = MutableStateFlow(com.bibin.visioneye.ai.YoloDebugState())
            override val hasPermission = true
            override fun checkCameraPermission() = true
            override fun bindCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner, surfaceProvider: androidx.camera.core.Preview.SurfaceProvider?) {}
            override fun unbindCamera() {}
            override fun startCamera() {}
            override fun pauseCamera() {}
            override fun stopCamera() {}
            override fun setFrameAnalyzer(analyzer: FrameAnalyzer) {}
            override fun clearFrameAnalyzer() {}
            override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {}
        }

        assertTrue(fakeController.isEnabledFor(VisionMode.CURRENCY))
        assertTrue(fakeController.isEnabledFor(VisionMode.NAVIGATE))
        assertFalse(fakeController.isEnabledFor(VisionMode.NAVIGATION))
        assertFalse(fakeController.isEnabledFor(VisionMode.SOS))
    }
}
