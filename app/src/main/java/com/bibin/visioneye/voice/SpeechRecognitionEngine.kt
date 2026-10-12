package com.bibin.visioneye.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Callbacks from the speech recognition provider.
 */
interface RecognitionCallback {
    fun onReadyForSpeech() {}
    fun onBeginningOfSpeech() {}
    fun onEndOfSpeech() {}
    fun onError(errorCode: Int, errorMessage: String)
    fun onResults(hypotheses: List<String>)
}

/**
 * Abstraction over Android's native [SpeechRecognizer] allowing deterministic unit testing
 * and clean lifecycle separation.
 */
interface SpeechRecognitionEngine {
    val isRecognitionAvailable: Boolean
    val isOfflineRecognitionAvailable: Boolean

    /**
     * Starts listening for a single spoken utterance in the specified [locale].
     */
    fun startListening(locale: Locale, callback: RecognitionCallback)

    /**
     * Stops listening and completes the current recognition.
     */
    fun stopListening()

    /**
     * Immediately cancels and releases active resources.
     */
    fun cancel()

    /**
     * Completely shuts down the engine and destroys the recognizer.
     */
    fun destroy()
}

/**
 * Production implementation of [SpeechRecognitionEngine] using Android's native [SpeechRecognizer].
 *
 * Guarantees all calls to [SpeechRecognizer] execute safely on the Main Looper.
 */
class AndroidSpeechRecognitionEngine(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) : SpeechRecognitionEngine {

    private var speechRecognizer: SpeechRecognizer? = null
    private var activeCallback: RecognitionCallback? = null
    private var isListeningActive = false

    override val isRecognitionAvailable: Boolean
        get() = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Throwable) {
            false
        }

    override val isOfflineRecognitionAvailable: Boolean
        get() = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }

    private fun ensureRecognizerOnMainThread(): SpeechRecognizer? {
        if (speechRecognizer == null && isRecognitionAvailable) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to create SpeechRecognizer", t)
                speechRecognizer = null
            }
        }
        return speechRecognizer
    }

    override fun startListening(locale: Locale, callback: RecognitionCallback) {
        mainHandler.post {
            activeCallback = callback
            val recognizer = ensureRecognizerOnMainThread()
            if (recognizer == null) {
                callback.onError(
                    SpeechRecognizer.ERROR_CLIENT,
                    "Speech recognition service is not available on this device"
                )
                return@post
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                // Request on-device recognition if available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isOfflineRecognitionAvailable) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            try {
                isListeningActive = true
                recognizer.startListening(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start listening", t)
                isListeningActive = false
                callback.onError(SpeechRecognizer.ERROR_CLIENT, t.message ?: "Failed to start listening")
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            if (isListeningActive) {
                try {
                    speechRecognizer?.stopListening()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error stopping SpeechRecognizer", t)
                }
            }
        }
    }

    override fun cancel() {
        mainHandler.post {
            isListeningActive = false
            try {
                speechRecognizer?.cancel()
            } catch (t: Throwable) {
                Log.w(TAG, "Error cancelling SpeechRecognizer", t)
            }
        }
    }

    override fun destroy() {
        mainHandler.post {
            isListeningActive = false
            activeCallback = null
            try {
                speechRecognizer?.destroy()
            } catch (t: Throwable) {
                Log.w(TAG, "Error destroying SpeechRecognizer", t)
            } finally {
                speechRecognizer = null
            }
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            activeCallback?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            activeCallback?.onBeginningOfSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListeningActive = false
            activeCallback?.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            isListeningActive = false
            val message = getErrorMessage(error)
            Log.w(TAG, "SpeechRecognizer error: $error ($message)")
            activeCallback?.onError(error, message)
        }

        override fun onResults(results: Bundle?) {
            isListeningActive = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList<String>()
            activeCallback?.onResults(matches)
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun getErrorMessage(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK -> "Network communication error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout during recognition"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Speech recognition error ($errorCode)"
    }

    companion object {
        private const val TAG = "AndroidSpeechEngine"
    }
}
