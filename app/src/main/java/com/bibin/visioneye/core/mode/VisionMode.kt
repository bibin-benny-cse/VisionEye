package com.bibin.visioneye.core.mode

/**
 * Defines the operational modes of the VisionEye assistance system.
 *
 * VisionEye follows a strict mode-based architecture:
 * Only models and sensor pipelines required by the active mode are initialized
 * and executed to preserve battery, thermal budget, and compute resources.
 */
enum class VisionMode(
    val displayName: String,
    val description: String,
    val announcement: String,
    val requiresCamera: Boolean,
    val requiresLocation: Boolean,
    val requiresHighFrequencyAi: Boolean
) {
    /**
     * Default mode for real-time obstacle detection, depth estimation, and path clearance.
     */
    NAVIGATE(
        displayName = "Navigate",
        description = "Obstacle detection, depth estimation, and path analysis",
        announcement = "Navigate mode active. Scanning path for obstacles.",
        requiresCamera = true,
        requiresLocation = false,
        requiresHighFrequencyAi = true
    ),

    /**
     * Optical Character Recognition (OCR) mode for reading text, signs, and documents.
     */
    READ(
        displayName = "Read",
        description = "Text recognition and document reading (OCR)",
        announcement = "Read mode active. Align text within camera view.",
        requiresCamera = true,
        requiresLocation = false,
        requiresHighFrequencyAi = false
    ),

    /**
     * Indian currency recognition mode for denomination identification.
     */
    CURRENCY(
        displayName = "Currency",
        description = "Indian banknote currency recognition",
        announcement = "Currency mode active. Hold banknote flat in front of camera.",
        requiresCamera = true,
        requiresLocation = false,
        requiresHighFrequencyAi = false
    ),

    /**
     * Facial recognition mode for identifying registered contacts and familiar faces.
     */
    PEOPLE(
        displayName = "People",
        description = "Registered face recognition",
        announcement = "People mode active. Scanning for familiar faces.",
        requiresCamera = true,
        requiresLocation = false,
        requiresHighFrequencyAi = true
    ),

    /**
     * Turn-by-turn pedestrian GPS navigation and outdoor wayfinding.
     */
    NAVIGATION(
        displayName = "Navigation",
        description = "GPS navigation and wayfinding",
        announcement = "Navigation mode active. Acquiring GPS location.",
        requiresCamera = false,
        requiresLocation = true,
        requiresHighFrequencyAi = false
    ),

    /**
     * Emergency assistance mode for broadcasting location and alerting emergency contacts.
     */
    SOS(
        displayName = "SOS",
        description = "Emergency alert and location sharing",
        announcement = "Emergency SOS mode active. Alerting emergency contacts.",
        requiresCamera = false,
        requiresLocation = true,
        requiresHighFrequencyAi = false
    );

    companion object {
        /**
         * The default mode when VisionEye starts.
         */
        val DEFAULT: VisionMode = NAVIGATE

        /**
         * Resolves a mode from string or returns default if unrecognized.
         */
        fun fromStringOrDefault(name: String?): VisionMode {
            if (name == null) return DEFAULT
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
        }
    }
}
