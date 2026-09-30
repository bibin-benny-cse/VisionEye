package com.bibin.visioneye.fusion

import com.bibin.visioneye.core.contract.ModeAwareComponent
import com.bibin.visioneye.core.mode.VisionMode
import kotlinx.coroutines.flow.SharedFlow

/**
 * Prioritized obstacle or perception insight produced by sensor fusion.
 */
data class PerceptionEvent(
    val id: String,
    val title: String,
    val distanceMeters: Float? = null,
    val directionClockPosition: Int? = null, // e.g. 12 o'clock, 2 o'clock
    val urgencyLevel: UrgencyLevel = UrgencyLevel.INFO,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class UrgencyLevel {
    INFO,
    WARNING,
    CRITICAL
}

/**
 * Architectural coordinator for fusing sensory input (camera, depth, IMU).
 *
 * (Fusion logic to be implemented in future milestone)
 */
interface SensorFusionCoordinator : ModeAwareComponent {
    /**
     * Shared stream of fused perception alerts for audio/haptic rendering.
     */
    val perceptionStream: SharedFlow<PerceptionEvent>

    override val supportedModes: Set<VisionMode>
        get() = setOf(VisionMode.NAVIGATE)
}
