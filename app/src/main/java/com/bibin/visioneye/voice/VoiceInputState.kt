package com.bibin.visioneye.voice

/**
 * Observable runtime state machine for single-utterance voice input.
 */
sealed interface VoiceInputState {
    /** Controller is idle and ready for user activation. */
    data object Idle : VoiceInputState

    /** Waiting for microphone runtime permission grant from system dialog. */
    data object RequestingPermission : VoiceInputState

    /**
     * Microphone is actively capturing an utterance.
     *
     * @property message Guidance message displayed/announced.
     */
    data class Listening(val message: String = "Listening for command...") : VoiceInputState

    /**
     * Dedicated dictation session for a specific input field.
     *
     * @property fieldType Target input field being dictated.
     * @property promptMessage Spoken prompt guiding the dictation.
     */
    data class Dictating(
        val fieldType: DictationFieldType,
        val promptMessage: String = "Listening for text..."
    ) : VoiceInputState

    /**
     * Processing recognized speech through the matching engine.
     *
     * @property utterance Recognized text undergoing matching.
     */
    data class Processing(val utterance: String? = null) : VoiceInputState

    /**
     * Speaking the command execution result or feedback via TTS.
     *
     * @property resultText Feedback message being spoken.
     */
    data class SpeakingResult(val resultText: String) : VoiceInputState

    /**
     * Error state encountered during recognition or matching.
     *
     * @property reason Human-readable description of the error.
     */
    data class Error(val reason: String) : VoiceInputState
}
