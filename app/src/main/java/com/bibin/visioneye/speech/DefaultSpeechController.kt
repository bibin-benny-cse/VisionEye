package com.bibin.visioneye.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Observable runtime status of the on-device Text-To-Speech engine.
 */
enum class TtsStatus {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    SPEAKING,
    ERROR,
    DISABLED
}

/**
 * Production implementation of [SpeechController] using the Android [TextToSpeech] engine.
 *
 * Implements a controlled speech policy to prevent audio flooding:
 * - Uses QUEUE_FLUSH for immediate safety/high-priority guidance, replacing pending speech.
 * - Suppresses rapid duplicate utterances.
 * - Manages clean lifecycle shutdown.
 *
 * @param context Application context used to bind the TTS engine.
 * @param speechRate Configurable speech rate multiplier (default: 1.05f for snappy guidance).
 * @param speechPitch Configurable speech pitch (default: 1.0f).
 */
class DefaultSpeechController(
    private val context: Context,
    var speechRate: Float = 1.05f,
    var speechPitch: Float = 1.0f
) : SpeechController, TextToSpeech.OnInitListener {

    private val _ttsStatus = MutableStateFlow(TtsStatus.UNINITIALIZED)
    val ttsStatus: StateFlow<TtsStatus> = _ttsStatus.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private val lock = Any()
    private var isInitialized = false

    // Track last spoken utterance to prevent back-to-back duplicate acoustic output
    private var lastSpokenText: String? = null
    private var lastSpokenTimeMs: Long = 0L

    private val utteranceListeners = java.util.concurrent.CopyOnWriteArrayList<SpeechUtteranceListener>()

    init {
        initializeTts()
    }

    override fun addUtteranceListener(listener: SpeechUtteranceListener) {
        utteranceListeners.add(listener)
    }

    override fun removeUtteranceListener(listener: SpeechUtteranceListener) {
        utteranceListeners.remove(listener)
    }

    private fun initializeTts() {
        synchronized(lock) {
            _ttsStatus.value = TtsStatus.INITIALIZING
            try {
                textToSpeech = TextToSpeech(context.applicationContext, this)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to instantiate Android TextToSpeech", t)
                _ttsStatus.value = TtsStatus.ERROR
            }
        }
    }

    override fun onInit(status: Int) {
        synchronized(lock) {
            if (status == TextToSpeech.SUCCESS) {
                val tts = textToSpeech
                if (tts != null) {
                    val langResult = tts.setLanguage(Locale.US)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "Locale.US not supported by TTS; falling back to default language.")
                        tts.language = Locale.getDefault()
                    }
                    tts.setSpeechRate(speechRate)
                    tts.setPitch(speechPitch)

                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _ttsStatus.value = TtsStatus.SPEAKING
                            val id = utteranceId ?: ""
                            for (listener in utteranceListeners) {
                                try {
                                    listener.onUtteranceStarted(id)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Error in utterance listener onStart", t)
                                }
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            _ttsStatus.value = TtsStatus.READY
                            val id = utteranceId ?: ""
                            for (listener in utteranceListeners) {
                                try {
                                    listener.onUtteranceCompleted(id)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Error in utterance listener onDone", t)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _ttsStatus.value = TtsStatus.READY
                            val id = utteranceId ?: ""
                            for (listener in utteranceListeners) {
                                try {
                                    listener.onUtteranceError(id)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Error in utterance listener onError", t)
                                }
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.w(TAG, "TTS utterance error: $errorCode for utteranceId: $utteranceId")
                            _ttsStatus.value = TtsStatus.READY
                            val id = utteranceId ?: ""
                            for (listener in utteranceListeners) {
                                try {
                                    listener.onUtteranceError(id, errorCode)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Error in utterance listener onError", t)
                                }
                            }
                        }
                    })

                    isInitialized = true
                    _ttsStatus.value = TtsStatus.READY
                    Log.d(TAG, "TextToSpeech successfully initialized and ready.")
                } else {
                    _ttsStatus.value = TtsStatus.ERROR
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status: $status")
                _ttsStatus.value = TtsStatus.ERROR
            }
        }
    }

    override fun speak(utterance: String, priority: SpeechPriority) {
        val utteranceId = "visioneye_utt_${System.currentTimeMillis()}"
        speak(utterance, priority, utteranceId)
    }

    override fun speak(utterance: String, priority: SpeechPriority, utteranceId: String) {
        val cleanText = utterance.trim()
        if (cleanText.isEmpty()) return

        synchronized(lock) {
            if (!isInitialized || textToSpeech == null) {
                Log.d(TAG, "Speech requested before TTS initialized; dropping: $cleanText")
                return
            }

            // Suppress rapid identical acoustic repetitions within 1.5 seconds
            val now = System.currentTimeMillis()
            if (cleanText.equals(lastSpokenText, ignoreCase = true) && (now - lastSpokenTimeMs) < 1500L) {
                return
            }

            // Controlled sequential queueing policy:
            // Use QUEUE_ADD for controlled sequential queueing; only IMMEDIATE_SAFETY flushes active speech.
            val queueMode = when (priority) {
                SpeechPriority.IMMEDIATE_SAFETY -> TextToSpeech.QUEUE_FLUSH
                SpeechPriority.HIGH,
                SpeechPriority.NORMAL,
                SpeechPriority.BACKGROUND_INFO -> TextToSpeech.QUEUE_ADD
            }

            lastSpokenText = cleanText
            lastSpokenTimeMs = now
            _ttsStatus.value = TtsStatus.SPEAKING

            textToSpeech?.speak(cleanText, queueMode, null, utteranceId)
        }
    }

    override fun stop() {
        synchronized(lock) {
            try {
                textToSpeech?.stop()
                lastSpokenText = null
                lastSpokenTimeMs = 0L
                if (isInitialized) {
                    _ttsStatus.value = TtsStatus.READY
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error while stopping speech", t)
            }
        }
    }

    /**
     * Shuts down the TextToSpeech engine and releases native audio resources.
     */
    fun shutdown() {
        synchronized(lock) {
            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                textToSpeech = null
                isInitialized = false
                _ttsStatus.value = TtsStatus.DISABLED
                Log.d(TAG, "TextToSpeech cleanly shut down.")
            } catch (t: Throwable) {
                Log.w(TAG, "Error shutting down TextToSpeech", t)
            }
        }
    }

    companion object {
        private const val TAG = "DefaultSpeechController"
    }
}
