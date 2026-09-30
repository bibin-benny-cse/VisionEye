package com.bibin.visioneye.navigation

import com.bibin.visioneye.core.contract.ModeAwareComponent
import com.bibin.visioneye.core.mode.VisionMode
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level state representing outdoor navigation status.
 */
sealed interface NavigationState {
    data object Inactive : NavigationState
    data object AcquiringGps : NavigationState
    data class Navigating(val destination: String, val distanceRemainingMeters: Float) : NavigationState
    data object DestinationReached : NavigationState
    data class Error(val message: String) : NavigationState
}

/**
 * Architectural contract for outdoor pedestrian GPS navigation.
 *
 * (LocationManager / FusedLocationProviderClient integration to be added in future milestone)
 */
interface NavigationController : ModeAwareComponent {
    val state: StateFlow<NavigationState>

    override val supportedModes: Set<VisionMode>
        get() = setOf(VisionMode.NAVIGATION)

    /**
     * Starts turn-by-turn guidance towards [destinationAddress].
     */
    fun startGuidance(destinationAddress: String)

    /**
     * Halts guidance.
     */
    fun stopGuidance()
}
