package com.bibin.visioneye.speech

import android.util.Log
import com.bibin.visioneye.fusion.decision.AlertCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Representation of a speech alert queued for sequential vocalization.
 *
 * @property alert The candidate navigation alert.
 * @property semanticKey Stable unique deduplication key (e.g. "chair|LEFT").
 * @property enqueuedTimestampMs Epoch millisecond when the alert was enqueued.
 */
data class QueuedSpeechAlert(
    val alert: AlertCandidate,
    val semanticKey: String,
    val enqueuedTimestampMs: Long = System.currentTimeMillis()
)

/**
 * Observable runtime diagnostic state of the [SpeechEventArbitrator].
 *
 * @property isSpeaking True if an utterance is currently being vocalized by TTS.
 * @property activeAlertMessage The message text of the currently active alert, if any.
 * @property queuedCount Number of pending alerts waiting in the sequential queue.
 * @property lastCompletedMessage Message text of the most recently finished utterance.
 */
data class SpeechArbitratorState(
    val isSpeaking: Boolean = false,
    val activeAlertMessage: String? = null,
    val queuedCount: Int = 0,
    val lastCompletedMessage: String? = null
)

/**
 * Authoritative single-channel speech arbitrator.
 *
 * Ensures only ONE navigation message is vocalized at any time:
 * - Arbitrates multiple selected alerts by enqueueing them and speaking sequentially
 *   upon utterance completion callbacks from [SpeechUtteranceListener].
 * - Deduplicates incoming alerts against active and pending semantic keys ("className|position").
 * - Drops stale queued alerts that exceed [pendingAlertMaxAgeMs] before they reach playback.
 * - Cleanses pending queues and cancels active speech on lifecycle actions (camera stop/mode change).
 */
interface SpeechEventArbitrator {
    /**
     * Submits an alert candidate to the arbitrator.
     * Returns true if accepted (spoken immediately or queued), false if rejected as duplicate.
     */
    fun submit(alert: AlertCandidate): Boolean

    /**
     * Submits multiple alert candidates in prioritized order.
     */
    fun submit(alerts: List<AlertCandidate>) {
        for (alert in alerts) {
            submit(alert)
        }
    }

    /**
     * Number of alerts currently waiting in the pending queue.
     */
    val queuedCount: Int

    /**
     * True if an utterance is currently being spoken.
     */
    val isSpeaking: Boolean

    /**
     * The alert currently being spoken, if any.
     */
    val activeAlert: AlertCandidate?

    /**
     * Observable state for HUD diagnostics and status inspection.
     */
    val state: StateFlow<SpeechArbitratorState>

    /**
     * Clears all pending queued alerts and cancels active speech if [stopActiveSpeech] is true.
     */
    fun clear(stopActiveSpeech: Boolean = true)
}

/**
 * Production implementation of [SpeechEventArbitrator].
 *
 * Thread-safe, single-channel speech queue coordinated with TextToSpeech utterance completion.
 *
 * @param speechController Underlying speech controller responsible for native TTS.
 * @param pendingAlertMaxAgeMs Maximum duration an alert may wait in queue before being dropped as stale (default 4000ms).
 * @param clock Time provider for deterministic testing.
 */
class DefaultSpeechEventArbitrator(
    private val speechController: SpeechController,
    val pendingAlertMaxAgeMs: Long = 4000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : SpeechEventArbitrator, SpeechUtteranceListener {

    private val lock = Any()
    private val pendingQueue = ArrayDeque<QueuedSpeechAlert>()
    private val activeOrPendingKeys = mutableSetOf<String>()
    private val spokenSemanticKeys = mutableSetOf<String>()
    private var _activeAlert: AlertCandidate? = null
    private var activeUtteranceId: String? = null
    private var lastCompletedMessage: String? = null

    private val _state = MutableStateFlow(SpeechArbitratorState())
    override val state: StateFlow<SpeechArbitratorState> = _state.asStateFlow()

    override val queuedCount: Int
        get() = synchronized(lock) { pendingQueue.size }

    override val isSpeaking: Boolean
        get() = synchronized(lock) { _activeAlert != null }

    override val activeAlert: AlertCandidate?
        get() = synchronized(lock) { _activeAlert }

    init {
        speechController.addUtteranceListener(this)
    }

    override fun submit(alert: AlertCandidate): Boolean = synchronized(lock) {
        val key = getSemanticKey(alert)

        // Deduplication:
        // Do not add if identical semantic alert is currently active, pending in queue,
        // or already spoken in this continuous session
        if (activeOrPendingKeys.contains(key) || spokenSemanticKeys.contains(key)) {
            Log.d(TAG, "Duplicate alert rejected (already active, queued, or spoken): $key")
            return false
        }

        // If an object moves to a new position, clear earlier spoken positions of this class
        // only if they are not currently active or pending in the queue
        val otherPositions = spokenSemanticKeys.filter {
            it.startsWith("${alert.className}|", ignoreCase = true) && !it.equals(key, ignoreCase = true)
        }
        for (otherKey in otherPositions) {
            if (!activeOrPendingKeys.contains(otherKey)) {
                spokenSemanticKeys.remove(otherKey)
            }
        }

        val now = clock()
        val queued = QueuedSpeechAlert(
            alert = alert,
            semanticKey = key,
            enqueuedTimestampMs = now
        )

        activeOrPendingKeys.add(key)

        if (_activeAlert == null) {
            // Channel is idle -> speak immediately
            startSpeaking(queued)
        } else {
            // Channel is busy -> enqueue for sequential delivery
            pendingQueue.addLast(queued)
            updateState()
            Log.d(TAG, "Alert queued ($key). Queue size: ${pendingQueue.size}")
        }
        return true
    }

    private fun startSpeaking(item: QueuedSpeechAlert) {
        _activeAlert = item.alert
        val now = clock()
        val utteranceId = "visioneye_arb_${now}_${item.semanticKey}"
        activeUtteranceId = utteranceId
        spokenSemanticKeys.add(item.semanticKey)
        updateState()

        Log.d(TAG, "Speaking alert: ${item.alert.message} (utteranceId: $utteranceId)")
        speechController.speak(item.alert.message, SpeechPriority.NORMAL, utteranceId)
    }

    override fun onUtteranceStarted(utteranceId: String) {
        // State updated during startSpeaking
    }

    override fun onUtteranceCompleted(utteranceId: String) {
        synchronized(lock) {
            Log.d(TAG, "Utterance completed: $utteranceId")
            handleUtteranceFinished()
        }
    }

    override fun onUtteranceError(utteranceId: String, errorCode: Int?) {
        synchronized(lock) {
            Log.w(TAG, "Utterance error: $errorCode for utteranceId: $utteranceId")
            handleUtteranceFinished()
        }
    }

    private fun handleUtteranceFinished() {
        val completed = _activeAlert
        lastCompletedMessage = completed?.message
        completed?.let {
            val key = getSemanticKey(it)
            activeOrPendingKeys.remove(key)
            spokenSemanticKeys.add(key)
        }
        _activeAlert = null
        activeUtteranceId = null

        // Process next valid, non-stale queued alert
        val now = clock()
        while (pendingQueue.isNotEmpty()) {
            val candidate = pendingQueue.removeFirst()
            val ageMs = now - candidate.enqueuedTimestampMs
            if (ageMs <= pendingAlertMaxAgeMs) {
                // Alert is fresh -> speak sequentially
                startSpeaking(candidate)
                return
            } else {
                // Stale alert -> drop without speaking
                Log.d(TAG, "Dropping stale alert: ${candidate.alert.message} (age: ${ageMs}ms > ${pendingAlertMaxAgeMs}ms)")
                activeOrPendingKeys.remove(candidate.semanticKey)
            }
        }

        updateState()
    }

    override fun clear(stopActiveSpeech: Boolean) {
        synchronized(lock) {
            pendingQueue.clear()
            activeOrPendingKeys.clear()
            spokenSemanticKeys.clear()
            if (stopActiveSpeech && _activeAlert != null) {
                speechController.stop()
            }
            _activeAlert = null
            activeUtteranceId = null
            updateState()
            Log.d(TAG, "SpeechEventArbitrator cleared.")
        }
    }

    /**
     * Removes an object from session tracking when it has disappeared from view.
     */
    fun onObjectDisappeared(className: String) {
        synchronized(lock) {
            spokenSemanticKeys.removeAll { it.startsWith("$className|", ignoreCase = true) }
        }
    }

    private fun updateState() {
        _state.value = SpeechArbitratorState(
            isSpeaking = _activeAlert != null,
            activeAlertMessage = _activeAlert?.message,
            queuedCount = pendingQueue.size,
            lastCompletedMessage = lastCompletedMessage
        )
    }

    private fun getSemanticKey(alert: AlertCandidate): String =
        "${alert.className}|${alert.position.name}|${alert.proximity.name}"

    companion object {
        private const val TAG = "SpeechEventArbitrator"
    }
}
