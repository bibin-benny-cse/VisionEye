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
 * Listener interface for Text-To-Speech utterance playback lifecycle events.
 */
interface SpeechUtteranceListener {
    /**
     * Called when an utterance begins audio playback.
     */
    fun onUtteranceStarted(utteranceId: String) {}

    /**
     * Called when an utterance completes audio playback successfully.
     */
    fun onUtteranceCompleted(utteranceId: String) {}

    /**
     * Called when an utterance playback encounters an error.
     */
    fun onUtteranceError(utteranceId: String, errorCode: Int? = null) {}
}

/**
 * Architectural contract for Text-To-Speech (TTS) and voice feedback.
 */
interface SpeechController {
    /**
     * Speaks the given [utterance] with specified [priority].
     */
    fun speak(utterance: String, priority: SpeechPriority = SpeechPriority.NORMAL)

    /**
     * Speaks the given [utterance] with specified [priority] and custom [utteranceId].
     */
    fun speak(utterance: String, priority: SpeechPriority, utteranceId: String) {
        speak(utterance, priority)
    }

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

    /**
     * Registers an utterance playback lifecycle listener.
     */
    fun addUtteranceListener(listener: SpeechUtteranceListener) {}

    /**
     * Unregisters an utterance playback lifecycle listener.
     */
    fun removeUtteranceListener(listener: SpeechUtteranceListener) {}
}
