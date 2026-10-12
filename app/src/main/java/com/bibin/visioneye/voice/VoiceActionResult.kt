package com.bibin.visioneye.voice

/**
 * Result of evaluating or executing a voice command.
 */
sealed interface VoiceActionResult {
    /**
     * Command executed successfully with spoken audio confirmation.
     *
     * @property message Feedback message spoken to the user via TTS.
     */
    data class Success(val message: String? = null) : VoiceActionResult

    /**
     * Command failed or was unavailable with an explanatory feedback message.
     *
     * @property errorMessage Clear explanation spoken to the user.
     */
    data class Error(val errorMessage: String) : VoiceActionResult

    /**
     * Command transitioned the session into dedicated single-utterance dictation mode.
     *
     * @property prompt Prompt announced before beginning dictation.
     * @property fieldType Target input field receiving the recognized text.
     */
    data class StartDictation(
        val prompt: String,
        val fieldType: DictationFieldType
    ) : VoiceActionResult

    /**
     * Handled silently without additional speech (e.g. when the executed action
     * initiates its own speech announcements or mode introductions).
     */
    data object HandledSilently : VoiceActionResult
}

/**
 * Field types supported for separate single-utterance dictation sessions.
 */
enum class DictationFieldType(val displayName: String) {
    NAVIGATION_DESTINATION("Destination"),
    PERSON_NAME("Person Name"),
    EMERGENCY_CONTACT_NAME("Contact Name"),
    EMERGENCY_CONTACT_PHONE("Phone Number")
}
