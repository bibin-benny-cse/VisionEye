package com.bibin.visioneye.emergency

import com.bibin.visioneye.core.contract.ModeAwareComponent
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.navigation.location.LocationFix
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level state representing emergency SOS status.
 */
sealed interface EmergencyState {
    /** System is ready and waiting for user command. */
    data object Idle : EmergencyState

    /** Countdown active with cancellation window. */
    data class Countdown(val secondsRemaining: Int) : EmergencyState

    /** Actively attempting to obtain a GPS fix before dispatch. */
    data object AcquiringLocation : EmergencyState

    /** Dispatching the emergency SMS via telephony. */
    data class Sending(val message: String, val locationFix: LocationFix?) : EmergencyState

    /** Emergency notification successfully submitted. */
    data class Sent(
        val message: String,
        val recipient: String,
        val timestampMs: Long = System.currentTimeMillis()
    ) : EmergencyState

    /** Dispatch failed or rejected. */
    data class Failed(val reason: String) : EmergencyState

    /** Emergency trigger aborted by the user during countdown or send. */
    data object Cancelled : EmergencyState
}

/** Legacy alias for backward compatibility. */
typealias Standby = EmergencyState.Idle

/**
 * Architectural contract for Emergency SOS assistance.
 */
interface EmergencyController : ModeAwareComponent {
    /**
     * Observable stream of current emergency state.
     */
    val state: StateFlow<EmergencyState>

    /**
     * Local storage repository for configured emergency contacts.
     */
    val contactRepository: EmergencyContactRepository

    /**
     * True if an emergency workflow is currently in progress (countdown, acquiring, sending, or sent).
     */
    val isTriggered: Boolean

    /**
     * Triggers the emergency alert pipeline (initiating countdown before dispatch).
     */
    fun triggerSos(reason: String? = null)

    /**
     * Cancels an active or pending emergency countdown and resets to Idle.
     */
    fun cancelSos()

    /**
     * Activates the emergency controller upon entering SOS mode.
     */
    fun activate()

    /**
     * Deactivates the emergency controller, halts any active jobs, and releases location resources.
     */
    fun deactivate()

    override val supportedModes: Set<VisionMode>
        get() = setOf(VisionMode.SOS)
}
