package com.bibin.visioneye.voice

/**
 * Screen contexts for contextual voice command routing, disambiguation,
 * and dynamic contextual help generation.
 */
enum class VoiceScreenContext(val displayName: String) {
    MAIN_DASHBOARD("Main Dashboard"),
    CAMERA_NAVIGATE("Navigate Mode"),
    CAMERA_READ("Read Text Mode"),
    CAMERA_CURRENCY("Scan Currency Mode"),
    CAMERA_PEOPLE("Recognize People Mode"),
    NAVIGATION_GPS("Outdoor GPS Navigation"),
    MANAGE_PEOPLE("Manage People"),
    ENROLL_PERSON("Enroll Person"),
    SOS_EMERGENCY("Emergency SOS")
}
