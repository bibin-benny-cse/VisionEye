package com.bibin.visioneye.data.model

import com.bibin.visioneye.core.mode.VisionMode

/**
 * Domain entity representing user configuration, accessibility preferences,
 * and safety/privacy consent policies.
 */
data class UserPreferences(
    val defaultMode: VisionMode = VisionMode.NAVIGATE,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val hapticFeedbackEnabled: Boolean = true,
    val highContrastTheme: Boolean = true,
    val obstacleAlertDistanceMeters: Float = 2.5f,

    // Safety and ethical compliance policies
    val hasAcknowledgedSafetyDisclaimer: Boolean = false,
    val requireFaceEnrollmentConsent: Boolean = true,
    val storeBiometricsLocallyOnly: Boolean = true
)
