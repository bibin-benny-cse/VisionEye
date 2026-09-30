package com.bibin.visioneye.data.model

import com.bibin.visioneye.core.mode.VisionMode

/**
 * Domain entity representing user configuration and accessibility preferences.
 */
data class UserPreferences(
    val defaultMode: VisionMode = VisionMode.NAVIGATE,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val hapticFeedbackEnabled: Boolean = true,
    val highContrastTheme: Boolean = true,
    val obstacleAlertDistanceMeters: Float = 2.5f
)
