package com.bibin.visioneye.camera

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
    data class Error(val message: String, val cause: Throwable? = null) : CameraState
}

/**
 * Architectural interface for the VisionEye camera pipeline.
 *
 * Implements [ModeAwareComponent] so that camera preview and frame
 * dispatching are paused or stopped when modes that don't require
 * camera (e.g. NAVIGATION, SOS) are active.
 *
 * (Full CameraX implementation to be added in future milestone)
 */
interface CameraController : ModeAwareComponent {
    /**
     * Observable stream of current camera state.
     */
    val state: StateFlow<CameraState>

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
     * Starts camera capture session.
     */
    fun startCamera()

    /**
     * Pauses camera capture.
     */
    fun pauseCamera()

    /**
     * Releases camera resources.
     */
    fun stopCamera()
}
