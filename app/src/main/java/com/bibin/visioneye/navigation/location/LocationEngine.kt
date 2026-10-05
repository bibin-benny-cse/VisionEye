package com.bibin.visioneye.navigation.location

import kotlinx.coroutines.flow.StateFlow

/**
 * Architectural contract for location provider engines.
 *
 * Decouples location acquisition from specific Android platform hardware and APIs,
 * enabling deterministic unit testing, mocking, and simulated pedestrian replay.
 */
interface LocationEngine {
    /**
     * Observable stream emitting the latest valid [LocationFix], or null when inactive or not yet acquired.
     */
    val locationFlow: StateFlow<LocationFix?>

    /**
     * Checks whether location permissions (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION) are granted.
     */
    fun hasPermission(): Boolean

    /**
     * Starts receiving periodic high-accuracy location updates.
     * Idempotent: safe to invoke repeatedly without side-effects.
     */
    fun startLocationUpdates()

    /**
     * Halts location updates and unregisters location listener callbacks.
     * Idempotent: safe to invoke repeatedly without side-effects.
     */
    fun stopLocationUpdates()
}
