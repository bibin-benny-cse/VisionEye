package com.bibin.visioneye.camera

import com.bibin.visioneye.core.mode.VisionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraControllerTest {

    private class FakeCameraController : CameraController {
        var unbindCount = 0
        var stopCount = 0

        override val state = MutableStateFlow<CameraState>(CameraState.Idle)
        override val diagnostics = MutableStateFlow(FrameAnalysisDiagnostics())
        override val yoloState = MutableStateFlow(com.bibin.visioneye.ai.YoloDebugState())
        override val hasPermission: Boolean = true
        override fun checkCameraPermission(): Boolean = true
        override fun bindCamera(
            lifecycleOwner: androidx.lifecycle.LifecycleOwner,
            surfaceProvider: androidx.camera.core.Preview.SurfaceProvider?
        ) {}
        override fun unbindCamera() { unbindCount++ }
        override fun startCamera() {}
        override fun pauseCamera() {}
        override fun stopCamera() { stopCount++ }
        override fun setFrameAnalyzer(analyzer: FrameAnalyzer) {}
        override fun clearFrameAnalyzer() {}
        override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {
            if (!isEnabledFor(newMode)) {
                stopCamera()
            }
        }
    }

    @Test
    fun supportedModes_onlyIncludesCameraRequiredModes() {
        val controller = FakeCameraController()
        val expectedModes = setOf(
            VisionMode.NAVIGATE,
            VisionMode.READ,
            VisionMode.CURRENCY,
            VisionMode.PEOPLE
        )

        assertEquals(expectedModes, controller.supportedModes)
        assertTrue(controller.isEnabledFor(VisionMode.NAVIGATE))
        assertTrue(controller.isEnabledFor(VisionMode.READ))
        assertTrue(controller.isEnabledFor(VisionMode.CURRENCY))
        assertTrue(controller.isEnabledFor(VisionMode.PEOPLE))
        assertFalse(controller.isEnabledFor(VisionMode.NAVIGATION))
        assertFalse(controller.isEnabledFor(VisionMode.SOS))
    }

    @Test
    fun modeChange_toNonCameraMode_stopsCamera() {
        val controller = FakeCameraController()
        assertEquals(0, controller.stopCount)

        // Switch to NAVIGATION (requires GPS, not camera)
        controller.onModeChanged(VisionMode.NAVIGATION, VisionMode.NAVIGATE)
        assertEquals(1, controller.stopCount)

        // Switch to SOS (emergency, no camera)
        controller.onModeChanged(VisionMode.SOS, VisionMode.NAVIGATION)
        assertEquals(2, controller.stopCount)

        // Switch back to NAVIGATE (camera supported, so stopCamera shouldn't be called)
        controller.onModeChanged(VisionMode.NAVIGATE, VisionMode.SOS)
        assertEquals(2, controller.stopCount)
    }

    @Test
    fun cameraStates_validateTypes() {
        val idle: CameraState = CameraState.Idle
        val initializing: CameraState = CameraState.Initializing
        val streaming: CameraState = CameraState.Streaming
        val paused: CameraState = CameraState.Paused
        val permission: CameraState = CameraState.PermissionRequired()
        val error: CameraState = CameraState.Error("Test error")

        assertTrue(idle is CameraState.Idle)
        assertTrue(initializing is CameraState.Initializing)
        assertTrue(streaming is CameraState.Streaming)
        assertTrue(paused is CameraState.Paused)
        assertTrue(permission is CameraState.PermissionRequired)
        assertTrue(error is CameraState.Error)
        assertEquals("Test error", (error as CameraState.Error).message)
    }

    @Test
    fun frameAnalysisDispatcher_registrationAndClear() {
        val dispatcher = FrameAnalysisDispatcher()
        var invocationCount = 0
        val analyzer = FrameAnalyzer { invocationCount++ }

        dispatcher.addAnalyzer(analyzer)
        dispatcher.removeAnalyzer(analyzer)
        dispatcher.clearAnalyzers()
        // Ensure no exceptions thrown when modifying listeners
        assertEquals(0, invocationCount)
    }

    @Test
    fun frameAnalysisDiagnostics_initialValuesAndReset() {
        val dispatcher = FrameAnalysisDispatcher()
        val initial = dispatcher.diagnostics.value

        assertEquals(0L, initial.timestampMs)
        assertEquals(0, initial.imageWidth)
        assertEquals(0, initial.imageHeight)
        assertEquals(0L, initial.analyzedFrameCount)
        assertEquals(0.0, initial.approximateFps, 0.001)

        dispatcher.resetDiagnostics()
        assertEquals(0L, dispatcher.diagnostics.value.analyzedFrameCount)
    }
}
