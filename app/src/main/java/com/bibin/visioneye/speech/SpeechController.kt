package com.bibin.visioneye.speech

/**
 * Priority levels for spoken audio feedback to ensure critical safety
 * alerts preempt general descriptions.
 */
enum class SpeechPriority {
    BACKGROUND_INFO,
    NORMAL,
    HIGH,
    IMMEDIATE_SAFETY
}

/**
 * Architectural contract for Text-To-Speech (TTS) and voice feedback.
 *
 * (Full Android TextToSpeech engine integration to be added in future milestone)
 */
interface SpeechController {
    /**
     * Speaks the given [utterance] with specified [priority].
     */
    fun speak(utterance: String, priority: SpeechPriority = SpeechPriority.NORMAL)

    /**
     * Stops any currently ongoing spoken speech.
     */
    fun stop()

    /**
     * Announces mode transition to the visually impaired user.
     */
    fun announceMode(modeName: String) {
        speak(utterance = modeName, priority = SpeechPriority.HIGH)
    }
}
