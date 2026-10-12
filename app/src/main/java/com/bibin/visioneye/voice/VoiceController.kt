package com.bibin.visioneye.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Architectural contract for VisionEye button-activated voice command and dictation processing.
 */
interface VoiceController {
    /** Observable runtime state of the voice session. */
    val state: StateFlow<VoiceInputState>

    /** Current active screen context used for command scoping. */
    val currentScreenContext: StateFlow<VoiceScreenContext>

    /** Central registry containing all active UI voice actions. */
    val registry: VoiceCommandRegistry

    /** Updates the current screen context when navigating between screens. */
    fun setScreenContext(context: VoiceScreenContext)

    /** Initiates a single-utterance listening session. */
    fun startListening()

    /** Stops active listening and begins matching recognized results. */
    fun stopListening()

    /** Cancels an active or pending listening session and returns to Idle. */
    fun cancel()

    /** Checks if the controller is currently capturing audio or dictation. */
    fun isListening(): Boolean

    /** Checks if RECORD_AUDIO permission is currently granted. */
    fun checkMicrophonePermission(): Boolean

    /** Called when the runtime microphone permission request dialog resolves. */
    fun onPermissionResult(granted: Boolean)

    /** Manually injects recognized speech text (useful for testing or fallback entry). */
    fun processSpokenText(spokenText: String)

    /** Configures language locale (default: Indian English en-IN). */
    fun setLanguage(locale: Locale)

    /** Configures or registers the active dictation consumer callback. */
    fun setDictationConsumer(consumer: ((DictationFieldType, String) -> VoiceActionResult)?)

    /** Releases all resources cleanly on application teardown. */
    fun destroy()
}

/**
 * Production implementation of [VoiceController] coordinating the native speech
 * recognition engine with Text-To-Speech output and UI callbacks.
 *
 * Implements strict single-utterance semantics:
 * - Tap `Speak a command` button to listen
 * - Speaks "Listening" feedback
 * - Captures one utterance
 * - Matches canonical visible labels
 * - Executes callback and speaks result
 * - Returns to Idle
 * - Watchdog timeout to prevent stuck listening states
 *
 * @param context Android context for permission checks.
 * @param speechController Speech synthesizer for voice output feedback.
 * @param engine Speech recognition engine (defaults to native [AndroidSpeechRecognitionEngine]).
 * @param backgroundDispatcher Dispatcher for asynchronous matching and action execution.
 */
class DefaultVoiceController(
    private val context: Context? = null,
    val speechController: SpeechController? = null,
    private val engine: SpeechRecognitionEngine = context?.let { AndroidSpeechRecognitionEngine(it) }
        ?: MockSpeechRecognitionEngine(),
    override val registry: VoiceCommandRegistry = VoiceCommandRegistry(),
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default
) : VoiceController, RecognitionCallback {

    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val lock = Any()

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    override val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val _currentScreenContext = MutableStateFlow(VoiceScreenContext.MAIN_DASHBOARD)
    override val currentScreenContext: StateFlow<VoiceScreenContext> = _currentScreenContext.asStateFlow()

    // Default locale initially en-IN (Indian English) per requirements
    private var recognitionLocale: Locale = Locale.forLanguageTag("en-IN")

    private var dictationConsumer: ((DictationFieldType, String) -> VoiceActionResult)? = null
    private var activeDictationField: DictationFieldType? = null

    private var watchdogJob: Job? = null
    private var executionJob: Job? = null

    init {
        // Register default global contextual help command
        registry.register(
            VoiceAction(
                id = "global_help",
                screenContext = null, // Global across all screens
                visibleLabel = "What can I do here?",
                canonicalCommand = "What can I do here?",
                aliases = listOf("What can I do here", "Help", "What can I do", "Commands", "Available commands"),
                description = "Speak the available controls on the current screen",
                onExecute = {
                    val helpText = registry.generateContextualHelp(_currentScreenContext.value)
                    VoiceActionResult.Success(helpText)
                }
            )
        )
    }

    override fun setScreenContext(context: VoiceScreenContext) {
        synchronized(lock) {
            _currentScreenContext.value = context
            // If screen changes during dictation, reset dictation
            if (activeDictationField != null) {
                activeDictationField = null
                cancelWatchdog()
                _state.value = VoiceInputState.Idle
            }
        }
    }

    override fun setLanguage(locale: Locale) {
        synchronized(lock) {
            recognitionLocale = locale
        }
    }

    override fun setDictationConsumer(consumer: ((DictationFieldType, String) -> VoiceActionResult)?) {
        synchronized(lock) {
            dictationConsumer = consumer
        }
    }

    override fun checkMicrophonePermission(): Boolean {
        if (context == null) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startListening()
        } else {
            speakFeedback("Microphone permission is required to use voice commands.")
            _state.value = VoiceInputState.Error("Microphone permission denied")
            resetToIdleAfterDelay(2000L)
        }
    }

    override fun isListening(): Boolean {
        return when (_state.value) {
            is VoiceInputState.Listening,
            is VoiceInputState.Dictating -> true
            else -> false
        }
    }

    override fun startListening() {
        synchronized(lock) {
            // Prevent overlapping or duplicate sessions
            if (isListening()) {
                Log.d(TAG, "Already listening; ignoring duplicate startListening()")
                return
            }

            // Check permission
            if (!checkMicrophonePermission()) {
                _state.value = VoiceInputState.RequestingPermission
                speakFeedback("Please grant microphone permission to speak commands.")
                return
            }

            // Halt ongoing non-critical speech so the mic doesn't record speaker output
            speechController?.stop()

            val isDictation = activeDictationField != null
            val prompt = if (isDictation) {
                "Listening for ${activeDictationField?.displayName}..."
            } else {
                "Listening for command..."
            }

            if (isDictation) {
                _state.value = VoiceInputState.Dictating(activeDictationField!!, prompt)
            } else {
                _state.value = VoiceInputState.Listening(prompt)
            }

            // Announce auditory readiness
            speakFeedback("Listening", SpeechPriority.NORMAL)

            // Start hardware/engine listening
            engine.startListening(recognitionLocale, this)

            // Start watchdog timer (8 seconds) to prevent stuck listening state
            startWatchdog(8000L)
        }
    }

    override fun stopListening() {
        synchronized(lock) {
            cancelWatchdog()
            engine.stopListening()
        }
    }

    override fun cancel() {
        synchronized(lock) {
            cancelWatchdog()
            executionJob?.cancel()
            executionJob = null
            engine.cancel()
            activeDictationField = null
            _state.value = VoiceInputState.Idle
            Log.d(TAG, "Voice session cancelled and returned to Idle.")
        }
    }

    override fun destroy() {
        cancel()
        engine.destroy()
    }

    // --- RecognitionCallback Implementation ---

    override fun onReadyForSpeech() {
        Log.d(TAG, "RecognitionCallback: onReadyForSpeech")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "RecognitionCallback: onBeginningOfSpeech")
        // User started speaking; reset watchdog to allow utterance completion
        startWatchdog(6000L)
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "RecognitionCallback: onEndOfSpeech")
        cancelWatchdog()
        _state.value = VoiceInputState.Processing()
    }

    override fun onError(errorCode: Int, errorMessage: String) {
        cancelWatchdog()
        synchronized(lock) {
            Log.w(TAG, "RecognitionCallback onError: $errorCode ($errorMessage)")
            activeDictationField = null

            val userFeedback = when {
                errorMessage.contains("No speech", ignoreCase = true) -> "No speech detected. Tap Speak a command to try again."
                errorMessage.contains("permission", ignoreCase = true) -> "Microphone permission is required."
                errorMessage.contains("Network", ignoreCase = true) -> "Network issue during speech recognition."
                else -> "Speech recognition error. Please try again."
            }

            _state.value = VoiceInputState.Error(errorMessage)
            speakFeedback(userFeedback)
            resetToIdleAfterDelay(2500L)
        }
    }

    override fun onResults(hypotheses: List<String>) {
        cancelWatchdog()
        synchronized(lock) {
            val cleanHypotheses = hypotheses.map { it.trim() }.filter { it.isNotBlank() }
            if (cleanHypotheses.isEmpty()) {
                onError(0, "No speech detected")
                return
            }

            val targetDictationField = activeDictationField
            if (targetDictationField != null) {
                // Separate Dictation State handling
                handleDictationUtterance(cleanHypotheses.first(), targetDictationField)
            } else {
                // Command Matching State handling
                handleCommandUtterances(cleanHypotheses)
            }
        }
    }

    override fun processSpokenText(spokenText: String) {
        onResults(listOf(spokenText))
    }

    private fun handleCommandUtterances(hypotheses: List<String>) {
        val primary = hypotheses.first()
        _state.value = VoiceInputState.Processing(primary)

        executionJob?.cancel()
        executionJob = scope.launch {
            val context = _currentScreenContext.value
            val matchResult = registry.match(hypotheses, context)

            when (matchResult) {
                is MatchResult.Matched -> {
                    val action = matchResult.action
                    Log.d(TAG, "Matched action: ${action.visibleLabel} (${action.id}) from utterance: ${matchResult.matchedPhrase}")

                    try {
                        val result = action.onExecute()
                        handleActionResult(result)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error executing voice action ${action.id}", t)
                        val err = "Error executing ${action.canonicalCommand}"
                        _state.value = VoiceInputState.SpeakingResult(err)
                        speakFeedback(err)
                        resetToIdleAfterDelay(2000L)
                    }
                }
                is MatchResult.NoMatch -> {
                    Log.d(TAG, "No match for hypotheses: $hypotheses on screen: ${context.displayName}")
                    _state.value = VoiceInputState.SpeakingResult(matchResult.feedbackMessage)
                    speakFeedback(matchResult.feedbackMessage)
                    resetToIdleAfterDelay(3500L)
                }
                is MatchResult.EmptyInput -> {
                    onError(0, "No speech detected")
                }
            }
        }
    }

    private fun handleDictationUtterance(dictatedText: String, fieldType: DictationFieldType) {
        _state.value = VoiceInputState.Processing(dictatedText)
        activeDictationField = null // Dictation finished

        executionJob?.cancel()
        executionJob = scope.launch {
            val consumer = dictationConsumer
            if (consumer != null) {
                val result = consumer.invoke(fieldType, dictatedText)
                handleActionResult(result)
            } else {
                val fallbackMsg = "${fieldType.displayName} set to \"$dictatedText\"."
                _state.value = VoiceInputState.SpeakingResult(fallbackMsg)
                speakFeedback(fallbackMsg)
                resetToIdleAfterDelay(2000L)
            }
        }
    }

    private fun handleActionResult(result: VoiceActionResult) {
        when (result) {
            is VoiceActionResult.Success -> {
                if (result.message != null) {
                    _state.value = VoiceInputState.SpeakingResult(result.message)
                    speakFeedback(result.message)
                    resetToIdleAfterDelay(2000L)
                } else {
                    _state.value = VoiceInputState.Idle
                }
            }
            is VoiceActionResult.Error -> {
                _state.value = VoiceInputState.SpeakingResult(result.errorMessage)
                speakFeedback(result.errorMessage)
                resetToIdleAfterDelay(2500L)
            }
            is VoiceActionResult.StartDictation -> {
                // Transition to dedicated dictation session
                activeDictationField = result.fieldType
                _state.value = VoiceInputState.Dictating(result.fieldType, result.prompt)
                speakFeedback(result.prompt)

                // Wait briefly for TTS prompt to vocalize, then automatically open microphone
                scope.launch {
                    delay(2200L)
                    synchronized(lock) {
                        if (activeDictationField != null) {
                            engine.startListening(recognitionLocale, this@DefaultVoiceController)
                            startWatchdog(8000L)
                        }
                    }
                }
            }
            is VoiceActionResult.HandledSilently -> {
                _state.value = VoiceInputState.Idle
            }
        }
    }

    private fun speakFeedback(text: String, priority: SpeechPriority = SpeechPriority.NORMAL) {
        speechController?.speak(text, priority)
    }

    private fun startWatchdog(timeoutMs: Long) {
        cancelWatchdog()
        watchdogJob = scope.launch {
            delay(timeoutMs)
            synchronized(lock) {
                if (isListening()) {
                    Log.w(TAG, "Watchdog timeout reached; aborting stuck listening session.")
                    engine.cancel()
                    activeDictationField = null
                    _state.value = VoiceInputState.Error("Listening timed out")
                    speakFeedback("No speech detected. Tap Speak a command to try again.")
                    resetToIdleAfterDelay(2000L)
                }
            }
        }
    }

    private fun cancelWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private fun resetToIdleAfterDelay(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            synchronized(lock) {
                _state.value = VoiceInputState.Idle
            }
        }
    }

    companion object {
        private const val TAG = "DefaultVoiceController"
    }
}

/**
 * Mock speech recognition engine used when no Android context is provided (e.g. unit tests).
 */
class MockSpeechRecognitionEngine : SpeechRecognitionEngine {
    override val isRecognitionAvailable: Boolean = true
    override val isOfflineRecognitionAvailable: Boolean = true
    private var callback: RecognitionCallback? = null

    override fun startListening(locale: Locale, callback: RecognitionCallback) {
        this.callback = callback
        callback.onReadyForSpeech()
    }

    override fun stopListening() {
        callback?.onEndOfSpeech()
    }

    override fun cancel() {
        callback = null
    }

    override fun destroy() {
        callback = null
    }

    fun simulateResults(hypotheses: List<String>) {
        callback?.onResults(hypotheses)
    }

    fun simulateError(errorCode: Int, message: String) {
        callback?.onError(errorCode, message)
    }
}
