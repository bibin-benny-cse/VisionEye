package com.bibin.visioneye.people

/**
 * Dedicated configuration for VisionEye PEOPLE mode face recognition.
 *
 * All recognition thresholds, temporal confirmation counts, quality gates,
 * and unknown-person policies are centralized here for maintainability and tuning.
 *
 * @property minFaceSizeRatio Minimum ratio of face bounding box size relative to the smaller frame dimension.
 * @property maxEulerY Maximum allowable head yaw angle in degrees (facing camera).
 * @property maxEulerZ Maximum allowable head roll angle in degrees.
 * @property maxEulerX Maximum allowable head pitch angle in degrees.
 * @property similarityThreshold Cosine similarity threshold for candidate identification (default 0.70f for MobileFaceNet).
 * @property temporalConfirmationCount Number of consecutive qualifying frames required before confirming an identity.
 * @property recognitionCooldownMs Minimum cooldown period between differing spoken alerts.
 * @property absenceTimeoutMs Inactivity interval after which continuous visibility state resets.
 * @property speakUnknownPerson Whether to announce "Unknown person" when an unidentified face is confirmed.
 * @property requiredEnrollmentSamples Number of high-quality face samples required to enroll a person.
 */
data class PeopleRecognitionConfig(
    val minFaceSizeRatio: Float = 0.12f,
    val maxEulerY: Float = 35.0f,
    val maxEulerZ: Float = 30.0f,
    val maxEulerX: Float = 35.0f,
    val similarityThreshold: Float = 0.70f,
    val temporalConfirmationCount: Int = 2,
    val recognitionCooldownMs: Long = 3000L,
    val absenceTimeoutMs: Long = 1200L,
    val speakUnknownPerson: Boolean = false,
    val requiredEnrollmentSamples: Int = 5,
    val sampleSpacingMs: Long = 400L
)
