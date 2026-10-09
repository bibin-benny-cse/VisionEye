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
    val requiresHighFrequencyAi: Boolean,
    val voiceCommands: List<String> = emptyList(),
    val sampleAlerts: List<String> = emptyList(),
    val pipelineDescription: String = ""
) {
    /**
     * Default mode for real-time obstacle detection, depth estimation, and path clearance.
     * Input: Camera frames
     * Output: Short prioritized voice alerts
     */
    NAVIGATE(
        displayName = "Navigate",
        description = "Obstacle detection, depth estimation, and path analysis",
        announcement = "Navigate mode active. Scanning path for obstacles.",
        requiresCamera = true,
        requiresLocation = false,
        requiresHighFrequencyAi = true,
        voiceCommands = listOf("Navigate", "Explore"),
        sampleAlerts = listOf("Person ahead.", "Vehicle on your left.", "Obstacle close."),
        pipelineDescription = "Camera frames -> Object detection + Depth estimation + Path analysis -> Short prioritized voice alerts"
    ),

    /**
     * Optical Character Recognition (OCR) mode for reading text, signs, and documents.
     * Voice command: "Read"
     * Pipeline: Camera -> OCR -> text -> Text-to-Speech
     */
    READ(
        displayName = "Read",
        description = "Text recognition and document reading (OCR)",
        announcement = "Read mode active. Align text within camera view.",
        requiresCamera = true,
        requiresLocation = false,
        requiresHighFrequencyAi = false,
        voiceCommands = listOf("Read"),
        pipelineDescription = "Camera -> OCR -> text -> Text-to-Speech"
    ),

    /**
     * Indian banknote denomination recognition mode.
     * Voice command: "Currency"
     * Pipeline: Camera -> Currency Recognition Model -> Denomination -> Text-to-Speech
     */
    CURRENCY(
        displayName = "Currency",
        description = "Indian banknote denomination recognition",
        announcement = "Currency mode active. Hold banknote in camera view.",
        requiresCamera = true,
        requiresLocation = false,
        requiresHighFrequencyAi = true,
        voiceCommands = listOf("Currency"),
        sampleAlerts = listOf("10 rupee note.", "500 rupee note."),
        pipelineDescription = "Camera -> Currency Recognition Model -> Denomination -> Text-to-Speech"
    ),

    /**
     * Facial recognition mode for identifying registered contacts and familiar faces.
     * Voice command: "Who is this?"
     * Pipeline: Camera -> face detection -> face embedding -> local registered-person comparison -> Text-to-Speech
     */
    PEOPLE(
        displayName = "People",
        description = "Registered face recognition",
        announcement = "People mode active. Scanning for familiar faces.",
        requiresCamera = true,
        requiresLocation = false,
        requiresHighFrequencyAi = true,
        voiceCommands = listOf("Who is this?"),
        pipelineDescription = "Camera -> face detection -> face embedding -> local registered-person comparison -> Text-to-Speech"
    ),

    /**
     * Turn-by-turn pedestrian GPS navigation and outdoor wayfinding.
     * Voice command: "Go to <destination>"
     * Pipeline: Voice command -> destination -> route -> spoken navigation.
     * Camera-based obstacle detection remains independent.
     */
    NAVIGATION(
        displayName = "Navigation",
        description = "GPS navigation and wayfinding",
        announcement = "Navigation mode active. Acquiring GPS location.",
        requiresCamera = false,
        requiresLocation = true,
        requiresHighFrequencyAi = false,
        voiceCommands = listOf("Go to <destination>"),
        pipelineDescription = "Voice command -> destination -> route -> spoken navigation"
    ),

    /**
     * Emergency assistance mode for broadcasting location and alerting emergency contacts.
     * Triggers: Voice command, Long press
     * Pipeline: Trigger -> current location -> emergency message -> emergency contact
     */
    SOS(
        displayName = "SOS",
        description = "Emergency alert and location sharing",
        announcement = "Emergency SOS mode active. Alerting emergency contacts.",
        requiresCamera = false,
        requiresLocation = true,
        requiresHighFrequencyAi = false,
        voiceCommands = listOf("SOS", "Emergency"),
        pipelineDescription = "Trigger (Voice / Long press) -> current location -> emergency message -> emergency contact"
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
