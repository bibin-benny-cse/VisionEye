package com.bibin.visioneye.speech

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.DetectorState
import com.bibin.visioneye.ai.HorizontalPosition
import com.bibin.visioneye.ai.ObjectDetector
import com.bibin.visioneye.ai.YoloFrameAnalyzer
import com.bibin.visioneye.fusion.decision.AlertCandidate
import com.bibin.visioneye.fusion.decision.AlertPriority
import com.bibin.visioneye.fusion.decision.DecisionConfig
import com.bibin.visioneye.fusion.decision.DefaultDecisionEngine
import com.bibin.visioneye.fusion.decision.NavigationAlertType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests for [SpeechEventArbitrator] single-channel speech queue.
 *
 * Verifies that:
 * 1. multipleSelectedAlertsAreSpokenSequentially()
 * 2. duplicatePendingAlertIsNotQueued()
 * 3. pendingAlertIsSpokenAfterCurrentSpeechCompletes()
 * 4. stalePendingAlertIsDropped()
 * 5. cameraStopClearsPendingAlerts()
 * 6. modeChangeClearsPendingAlerts()
 * 7. composeRecompositionDoesNotSpeak()
 * 8. rawDetectionsDoNotEnterSpeechQueue()
 * 9. onlyDecisionEngineSelectedAlertsEnterSpeechQueue()
 * 10. speechCompletionTriggersNextQueuedAlert()
 */
class SpeechEventArbitratorTest {

    private lateinit var fakeSpeech: FakeSpeechController
    private lateinit var arbitrator: DefaultSpeechEventArbitrator
    private var currentTimeMs: Long = 1000L

    @Before
    fun setup() {
        currentTimeMs = 1000L
        fakeSpeech = FakeSpeechController()
        arbitrator = DefaultSpeechEventArbitrator(
            speechController = fakeSpeech,
            pendingAlertMaxAgeMs = 4000L,
            clock = { currentTimeMs }
        )
    }

    private fun createAlert(
        className: String,
        position: HorizontalPosition,
        message: String,
        priority: AlertPriority = AlertPriority.MEDIUM,
        confidence: Float = 0.85f
    ): AlertCandidate {
        return AlertCandidate(
            className = className,
            confidence = confidence,
            position = position,
            priority = priority,
            priorityRank = priority.rank,
            message = message,
            timestampMs = currentTimeMs,
            alertType = NavigationAlertType.fromPosition(position),
            reason = "Test candidate"
        )
    }

    // 1. Multiple selected alerts: chair LEFT, chair CENTER, book RIGHT.
    // Expected: first speech = chair LEFT, second = chair CENTER, third = book RIGHT, NEVER overlapping.
    @Test
    fun multipleSelectedAlertsAreSpokenSequentially() {
        val chairLeft = createAlert("chair", HorizontalPosition.LEFT, "Chair on your left")
        val chairCenter = createAlert("chair", HorizontalPosition.CENTER, "Chair ahead")
        val bookRight = createAlert("book", HorizontalPosition.RIGHT, "Book on your right")

        // Submit all three selected alerts
        arbitrator.submit(listOf(chairLeft, chairCenter, bookRight))

        // First alert immediately begins speaking; remaining 2 are queued
        assertEquals("First alert speaks immediately", 1, fakeSpeech.spokenUtterances.size)
        assertEquals("Chair on your left", fakeSpeech.spokenUtterances[0])
        assertTrue("Arbitrator is in speaking state", arbitrator.isSpeaking)
        assertEquals("Remaining 2 alerts queued", 2, arbitrator.queuedCount)

        // First speech completes -> triggers second alert
        fakeSpeech.completeCurrentUtterance()
        assertEquals("Second alert speaks after first completes", 2, fakeSpeech.spokenUtterances.size)
        assertEquals("Chair ahead", fakeSpeech.spokenUtterances[1])
        assertEquals("1 alert remains queued", 1, arbitrator.queuedCount)

        // Second speech completes -> triggers third alert
        fakeSpeech.completeCurrentUtterance()
        assertEquals("Third alert speaks after second completes", 3, fakeSpeech.spokenUtterances.size)
        assertEquals("Book on your right", fakeSpeech.spokenUtterances[2])
        assertEquals("Queue now empty", 0, arbitrator.queuedCount)

        // Third speech completes -> idle
        fakeSpeech.completeCurrentUtterance()
        assertFalse("Arbitrator is now idle", arbitrator.isSpeaking)
        assertEquals("Queue remains empty", 0, arbitrator.queuedCount)

        // Sequential speech verified with zero overlap
        assertEquals(
            listOf("Chair on your left", "Chair ahead", "Book on your right"),
            fakeSpeech.spokenUtterances
        )
    }

    // 2. Duplicate alert while first is speaking: chair LEFT, chair LEFT, chair LEFT.
    // Expected: exactly one speech event.
    @Test
    fun duplicatePendingAlertIsNotQueued() {
        val chairLeft = createAlert("chair", HorizontalPosition.LEFT, "Chair on your left")

        val accepted1 = arbitrator.submit(chairLeft)
        assertTrue("First alert accepted", accepted1)
        assertEquals("First alert spoken", 1, fakeSpeech.spokenUtterances.size)

        // Submit duplicates while first is actively speaking
        val accepted2 = arbitrator.submit(chairLeft)
        val accepted3 = arbitrator.submit(chairLeft)

        assertFalse("Duplicate alert 2 rejected", accepted2)
        assertFalse("Duplicate alert 3 rejected", accepted3)
        assertEquals("Queue size remains 0", 0, arbitrator.queuedCount)

        // Complete the first utterance
        fakeSpeech.completeCurrentUtterance()

        // Verify no second speech was triggered
        assertEquals("Exactly one speech event occurred", 1, fakeSpeech.spokenUtterances.size)
        assertFalse("Arbitrator is idle", arbitrator.isSpeaking)
    }

    // 3. Pending alert is spoken after current speech completes.
    @Test
    fun pendingAlertIsSpokenAfterCurrentSpeechCompletes() {
        val chairLeft = createAlert("chair", HorizontalPosition.LEFT, "Chair on your left")
        val bookRight = createAlert("book", HorizontalPosition.RIGHT, "Book on your right")

        arbitrator.submit(chairLeft)
        arbitrator.submit(bookRight)

        assertEquals("Only first alert is spoken initially", 1, fakeSpeech.spokenUtterances.size)
        assertEquals("Chair on your left", fakeSpeech.spokenUtterances[0])
        assertEquals("Book is queued", 1, arbitrator.queuedCount)

        // Complete first utterance
        fakeSpeech.completeCurrentUtterance()

        assertEquals("Second alert is spoken upon first completion", 2, fakeSpeech.spokenUtterances.size)
        assertEquals("Book on your right", fakeSpeech.spokenUtterances[1])
        assertEquals("Queue is drained", 0, arbitrator.queuedCount)
    }

    // 4. Stale pending alert is dropped without being spoken.
    @Test
    fun stalePendingAlertIsDropped() {
        val chairLeft = createAlert("chair", HorizontalPosition.LEFT, "Chair on your left")
        val bookRight = createAlert("book", HorizontalPosition.RIGHT, "Book on your right")

        // Enqueued at t = 1000ms
        currentTimeMs = 1000L
        arbitrator.submit(chairLeft)
        arbitrator.submit(bookRight)

        assertEquals(1, fakeSpeech.spokenUtterances.size)
        assertEquals(1, arbitrator.queuedCount)

        // Advance simulated time past PENDING_ALERT_MAX_AGE_MS (4000ms):
        // t = 6000ms -> age is 5000ms > 4000ms
        currentTimeMs = 6000L

        // Complete first utterance
        fakeSpeech.completeCurrentUtterance()

        // Stale book alert must be dropped, NOT spoken
        assertEquals("Stale alert was dropped; total speeches remains 1", 1, fakeSpeech.spokenUtterances.size)
        assertEquals("Queue drained", 0, arbitrator.queuedCount)
        assertFalse("Arbitrator returned to idle", arbitrator.isSpeaking)
    }

    // 5. Stopping camera clears pending alerts and stops active speech.
    @Test
    fun cameraStopClearsPendingAlerts() {
        val chairLeft = createAlert("chair", HorizontalPosition.LEFT, "Chair on your left")
        val bookRight = createAlert("book", HorizontalPosition.RIGHT, "Book on your right")

        arbitrator.submit(chairLeft)
        arbitrator.submit(bookRight)

        assertEquals(1, fakeSpeech.spokenUtterances.size)
        assertEquals(1, arbitrator.queuedCount)
        assertTrue(arbitrator.isSpeaking)

        // Camera stops -> lifecycle clear
        arbitrator.clear(stopActiveSpeech = true)

        assertEquals("Pending queue cleared", 0, arbitrator.queuedCount)
        assertFalse("Active speech state cleared", arbitrator.isSpeaking)
        assertEquals("TTS stop called", 1, fakeSpeech.stopCallCount)

        // Completing or advancing time must not trigger the cancelled book alert
        fakeSpeech.completeCurrentUtterance()
        assertEquals("No pending alerts spoken after camera stop", 1, fakeSpeech.spokenUtterances.size)
    }

    // 6. Mode change clears pending alerts and resets speech state.
    @Test
    fun modeChangeClearsPendingAlerts() {
        val chairLeft = createAlert("chair", HorizontalPosition.LEFT, "Chair on your left")
        val bookRight = createAlert("book", HorizontalPosition.RIGHT, "Book on your right")

        arbitrator.submit(chairLeft)
        arbitrator.submit(bookRight)

        // Mode change triggers analyzer.reset()
        arbitrator.clear(stopActiveSpeech = true)

        assertEquals("Queue empty after mode change", 0, arbitrator.queuedCount)
        assertFalse("Not speaking after mode change", arbitrator.isSpeaking)
        assertNull("Active alert null after mode change", arbitrator.activeAlert)
    }

    // 7. Compose recomposition / state reads must NEVER invoke speech.
    @Test
    fun composeRecompositionDoesNotSpeak() {
        val stateFlow = arbitrator.state

        // Simulate 100 UI recompositions reading StateFlow value
        for (i in 0 until 100) {
            val s = stateFlow.value
            assertFalse(s.isSpeaking)
            assertEquals(0, s.queuedCount)
            val q = arbitrator.queuedCount
            val speaking = arbitrator.isSpeaking
            assertFalse(speaking)
            assertEquals(0, q)
        }

        assertEquals("Reading state in recompositions must never trigger speech", 0, fakeSpeech.spokenUtterances.size)
    }

    // 8. Raw detections that are unstabilized do not enter speech queue.
    @Test
    fun rawDetectionsDoNotEnterSpeechQueue() {
        val engine = DefaultDecisionEngine(
            config = DecisionConfig(minimumStableObservations = 2)
        )
        val rawDetection = Detection(
            classId = 0,
            label = "chair",
            confidence = 0.85f,
            boundingBox = BoundingBox(0.1f, 0.1f, 0.3f, 0.4f)
        )

        // Frame 1: Single raw detection (unstabilized, observationCount = 1 < 2)
        val result = engine.process(listOf(rawDetection), timestampMs = 1000L)
        assertTrue("Selected alerts must be empty for unstabilized raw detection", result.selectedAlerts.isEmpty())

        arbitrator.submit(result.selectedAlerts)

        assertEquals("Raw unstabilized detection does not enter queue", 0, arbitrator.queuedCount)
        assertFalse(arbitrator.isSpeaking)
        assertEquals(0, fakeSpeech.spokenUtterances.size)
    }

    // 9. Only alerts selected by DecisionEngine enter speech queue.
    @Test
    fun onlyDecisionEngineSelectedAlertsEnterSpeechQueue() {
        val engine = DefaultDecisionEngine(
            config = DecisionConfig(
                maxSelectedAlerts = 1,
                minimumStableObservations = 1
            )
        )

        val personCenter = Detection(
            classId = 0,
            label = "person",
            confidence = 0.90f,
            boundingBox = BoundingBox(0.4f, 0.1f, 0.6f, 0.9f)
        )
        val cupRight = Detection(
            classId = 1,
            label = "cup",
            confidence = 0.70f,
            boundingBox = BoundingBox(0.7f, 0.5f, 0.9f, 0.8f)
        )

        // Process frame with both person and cup
        val result = engine.process(listOf(personCenter, cupRight), timestampMs = 1000L)

        // Capped at maxSelectedAlerts = 1; person is higher priority than cup
        assertEquals(1, result.selectedAlerts.size)
        assertEquals("person", result.selectedAlerts[0].className)

        // Submit only the decision engine result
        arbitrator.submit(result.selectedAlerts)

        assertEquals("Only selected alert entered speech", 1, fakeSpeech.spokenUtterances.size)
        assertTrue(
            "Person alert spoken",
            fakeSpeech.spokenUtterances[0].contains("person", ignoreCase = true)
        )
        assertFalse(
            "Cup was suppressed and must never be spoken",
            fakeSpeech.spokenUtterances.any { it.contains("cup", ignoreCase = true) }
        )
    }

    // 10. Speech completion triggers next queued alert.
    @Test
    fun speechCompletionTriggersNextQueuedAlert() {
        val chairLeft = createAlert("chair", HorizontalPosition.LEFT, "Chair on your left")
        val chairCenter = createAlert("chair", HorizontalPosition.CENTER, "Chair ahead")

        arbitrator.submit(chairLeft)
        arbitrator.submit(chairCenter)

        assertEquals(1, fakeSpeech.spokenUtterances.size)
        assertEquals("Chair on your left", fakeSpeech.spokenUtterances[0])
        assertEquals(1, arbitrator.queuedCount)

        // Trigger utterance completion
        fakeSpeech.completeCurrentUtterance()

        // Next queued alert is automatically triggered
        assertEquals(2, fakeSpeech.spokenUtterances.size)
        assertEquals("Chair ahead", fakeSpeech.spokenUtterances[1])
        assertEquals(0, arbitrator.queuedCount)
    }

    /**
     * Fake [SpeechController] tracking playback state and firing utterance listener callbacks.
     */
    private class FakeSpeechController : SpeechController {
        val spokenUtterances = mutableListOf<String>()
        val spokenUtteranceIds = mutableListOf<String>()
        var stopCallCount = 0
        private val listeners = mutableListOf<SpeechUtteranceListener>()
        var activeUtteranceId: String? = null

        override fun addUtteranceListener(listener: SpeechUtteranceListener) {
            listeners.add(listener)
        }

        override fun removeUtteranceListener(listener: SpeechUtteranceListener) {
            listeners.remove(listener)
        }

        override fun speak(utterance: String, priority: SpeechPriority) {
            val id = "fake_utt_${spokenUtterances.size + 1}"
            speak(utterance, priority, id)
        }

        override fun speak(utterance: String, priority: SpeechPriority, utteranceId: String) {
            spokenUtterances.add(utterance)
            spokenUtteranceIds.add(utteranceId)
            activeUtteranceId = utteranceId
            for (l in listeners) {
                l.onUtteranceStarted(utteranceId)
            }
        }

        override fun stop() {
            stopCallCount++
            activeUtteranceId = null
        }

        fun completeCurrentUtterance(utteranceId: String? = activeUtteranceId) {
            val id = utteranceId ?: activeUtteranceId ?: return
            activeUtteranceId = null
            for (l in listeners) {
                l.onUtteranceCompleted(id)
            }
        }

        fun errorCurrentUtterance(utteranceId: String? = activeUtteranceId, errorCode: Int = -1) {
            val id = utteranceId ?: activeUtteranceId ?: return
            activeUtteranceId = null
            for (l in listeners) {
                l.onUtteranceError(id, errorCode)
            }
        }
    }
}
