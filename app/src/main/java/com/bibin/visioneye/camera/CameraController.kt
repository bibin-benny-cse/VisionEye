package com.bibin.visioneye.camera

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.bibin.visioneye.core.contract.ModeAwareComponent
import com.bibin.visioneye.core.mode.VisionMode
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level state representing the camera subsystem.
 */
sealed interface CameraState {
    data object Idle : CameraState
    data object Initializing : CameraState
    data object Streaming : CameraState
    data object Paused : CameraState
    data class PermissionRequired(val rationaleNeeded: Boolean = false) : CameraState
    data class Error(val message: String, val cause: Throwable? = null) : CameraState
}

/**
 * Architectural interface for the VisionEye camera pipeline.
 *
 * Implements [ModeAwareComponent] so that camera preview and frame
 * dispatching are paused or stopped when modes that don't require
 * camera (e.g. NAVIGATION, SOS) are active.
 */
interface CameraController : ModeAwareComponent {
    /**
     * Observable stream of current camera state.
     */
    val state: StateFlow<CameraState>

    /**
     * Observable stream of live frame analysis diagnostics (FPS, dimensions, frame count).
     */
    val diagnostics: StateFlow<FrameAnalysisDiagnostics>

    /**
     * Observable stream of YOLO object detection development metrics and results.
     */
    val yoloState: StateFlow<com.bibin.visioneye.ai.YoloDebugState>

    /**
     * True if CAMERA runtime permission is currently granted.
     */
    val hasPermission: Boolean

    /**
     * Checks if CAMERA permission is granted by querying the system.
     */
    fun checkCameraPermission(): Boolean

    /**
     * Modes that require camera frames: NAVIGATE, READ, CURRENCY, PEOPLE.
     */
    override val supportedModes: Set<VisionMode>
        get() = setOf(
            VisionMode.NAVIGATE,
            VisionMode.READ,
            VisionMode.CURRENCY,
            VisionMode.PEOPLE
        )

    /**
     * Binds the camera to the specified [lifecycleOwner] and attaches [surfaceProvider]
     * for live preview rendering.
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider? = null
    )

    /**
     * Explicitly unbinds all active use cases and releases camera hardware bindings.
     */
    fun unbindCamera()

    /**
     * Starts or resumes camera capture session.
     */
    fun startCamera()

    /**
     * Pauses camera capture without destroying permanent controller resources.
     */
    fun pauseCamera()

    /**
     * Completely releases camera resources and resets state to Idle.
     */
    fun stopCamera()

    /**
     * Registers a frame analyzer for downstream AI inference (YOLO, depth, OCR).
     */
    fun setFrameAnalyzer(analyzer: FrameAnalyzer)

    /**
     * Removes the currently registered frame analyzer.
     */
    fun clearFrameAnalyzer()
}
