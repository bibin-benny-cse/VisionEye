package com.bibin.visioneye.emergency

import com.bibin.visioneye.core.contract.ModeAwareComponent
import com.bibin.visioneye.core.mode.VisionMode
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level state representing emergency SOS status.
 */
sealed interface EmergencyState {
    data object Standby : EmergencyState
    data class Countdown(val secondsRemaining: Int) : EmergencyState
    data object AlertDispatched : EmergencyState
    data object Cancelled : EmergencyState
}

/**
 * Architectural contract for Emergency SOS assistance.
 *
 * (SMS dispatch / emergency contact dialing to be added in future milestone)
 */
interface EmergencyController : ModeAwareComponent {
    val state: StateFlow<EmergencyState>

    override val supportedModes: Set<VisionMode>
        get() = setOf(VisionMode.SOS)

    /**
     * Triggers the emergency alert pipeline (initiating countdown before dispatch).
     */
    fun triggerSos(reason: String? = null)

    /**
     * Cancels an active or pending emergency countdown.
     */
    fun cancelSos()
}
