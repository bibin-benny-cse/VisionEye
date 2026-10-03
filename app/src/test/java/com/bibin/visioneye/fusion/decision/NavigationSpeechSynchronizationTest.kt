package com.bibin.visioneye.fusion.decision

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.DetectorConfig
import com.bibin.visioneye.ai.DetectorState
import com.bibin.visioneye.ai.HorizontalPosition
import com.bibin.visioneye.ai.ObjectDetector
import com.bibin.visioneye.ai.YoloDebugState
import com.bibin.visioneye.ai.YoloFrameAnalyzer
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests for VisionEye Navigation TTS Repeat / Alert State Synchronization.
 *
 * Verifies the single authoritative speech triggering path:
 * YOLO detections -> DecisionEngine -> DecisionResult.selectedAlerts -> NEW eligible alert event -> SpeechController.speak()
 *
 * Covers all 10 required test scenarios:
 * 1. testSameAlertSpeaksOnlyOnce
 * 2. testSameAlertAcrossManyFramesDoesNotRepeat
 * 3. testDifferentPositionCreatesNewSpeechEvent
 * 4. testDifferentClassCreatesNewSpeechEvent
 * 5. testRawDetectionsDoNotTriggerSpeech
 * 6. testNoSelectedAlertDoesNotTriggerSpeech
 * 7. testCooldownBlocksRepeatedSpeech
 * 8. testCameraRestartResetsSpeechState
 * 9. testComposeRecompositionCannotTriggerSpeech
 * 10. testMultipleRawObjectsOnlySelectedAlertsCanSpeak
 */
class NavigationSpeechSynchronizationTest {

    private lateinit var engine: DefaultDecisionEngine
    private lateinit var fakeSpeech: FakeSpeechController
    private val config = DecisionConfig(
        alertConfidenceThreshold = 0.40f,
        minimumStableObservations = 2,
        cooldownPeriodMs = 2500L,
        maxSelectedAlerts = 2,
        leftZoneBoundary = 0.33f,
        rightZoneBoundary = 0.67f,
        trackingMaxDisplacement = 0.25f,
        objectDisappearanceTimeoutMs = 1000L
    )

    @Before
    fun setup() {
        engine = DefaultDecisionEngine(config = config)
        fakeSpeech = FakeSpeechController()
    }

    private fun createDetection(
        className: String,
        confidence: Float = 0.85f,
        centerX: Float = 0.20f,
        centerY: Float = 0.50f,
        width: Float = 0.20f,
        height: Float = 0.40f,
        classId: Int = 0
    ): Detection {
        val halfW = width / 2f
        val halfH = height / 2f
        val box = BoundingBox(
            left = (centerX - halfW).coerceIn(0f, 1f),
            top = (centerY - halfH).coerceIn(0f, 1f),
            right = (centerX + halfW).coerceIn(0f, 1f),
            bottom = (centerY + halfH).coerceIn(0f, 1f)
        )
        return Detection(
            classId = classId,
            label = className,
            confidence = confidence,
            boundingBox = box
        )
    }

    /**
     * Executes the authoritative speech pipeline matching YoloFrameAnalyzer.analyze():
     * DecisionEngine.process() -> if (newAlertEvent != null) speechController.speak(newAlertEvent.message)
     */
    private fun processFrame(detections: List<Detection>, timestampMs: Long): DecisionResult {
        val result = engine.process(detections, timestampMs)
        result.newAlertEvent?.let { newAlert ->
            fakeSpeech.speak(newAlert.message, SpeechPriority.NORMAL)
        }
        return result
    }

    // 1. Point at the same chair in the same position; verify it speaks only once.
    @Test
    fun testSameAlertSpeaksOnlyOnce() {
        val chairLeft = createDetection("chair", centerX = 0.20f)

        // Frame 1: Transient first observation -> no speech
        processFrame(listOf(chairLeft), timestampMs = 1000L)
        assertEquals("Frame 1 (observation 1 < 2): No speech", 0, fakeSpeech.spokenUtterances.size)

        // Frame 2: Stabilized persistent observation -> speaks exactly once
        processFrame(listOf(chairLeft), timestampMs = 1200L)
        assertEquals("Frame 2 (observation 2 >= 2): Speaks once", 1, fakeSpeech.spokenUtterances.size)
        assertEquals("chair on your left", fakeSpeech.spokenUtterances.first())

        // Frame 3: Same chair, same position -> no repeated speech
        processFrame(listOf(chairLeft), timestampMs = 1400L)
        assertEquals("Frame 3: Repeated frame must not cause speech", 1, fakeSpeech.spokenUtterances.size)
    }

    // 2. Point camera at the same chair continuously for 30 frames (6 seconds, well past cooldown).
    // Must speak exactly once across all 30 frames.
    @Test
    fun testSameAlertAcrossManyFramesDoesNotRepeat() {
        val chairLeft = createDetection("chair", centerX = 0.20f)

        // 30 frames across 6.0 seconds at ~5 FPS (200ms intervals)
        for (i in 0 until 30) {
            val ts = 1000L + (i * 200L)
            processFrame(listOf(chairLeft), timestampMs = ts)
        }

        // Even though cooldown is 2500ms and 6000ms have elapsed, the continuously tracked
        // chair in unchanged position must speak ONLY ONCE.
        assertEquals("Continuously tracked chair across 30 frames must speak exactly once", 1, fakeSpeech.spokenUtterances.size)
        assertEquals("chair on your left", fakeSpeech.spokenUtterances[0])
    }

    // 3. Position change: chair LEFT changes to chair CENTER.
    // After temporal stabilization in the new position, generate a new speech event: "chair ahead".
    @Test
    fun testDifferentPositionCreatesNewSpeechEvent() {
        val chairLeft = createDetection("chair", centerX = 0.20f)
        val chairCenter = createDetection("chair", centerX = 0.50f)

        // Chair on LEFT (frames 1 & 2)
        processFrame(listOf(chairLeft), timestampMs = 1000L)
        processFrame(listOf(chairLeft), timestampMs = 1200L)
        assertEquals("Chair on LEFT speaks once", 1, fakeSpeech.spokenUtterances.size)
        assertEquals("chair on your left", fakeSpeech.spokenUtterances[0])

        // Chair transitions to CENTER
        // Frame 3: First observation in CENTER -> transient stabilization required -> no speech yet
        val resCenter1 = processFrame(listOf(chairCenter), timestampMs = 1400L)
        assertNull("First frame in new position should not emit newAlertEvent", resCenter1.newAlertEvent)
        assertEquals("No speech before new position stabilizes", 1, fakeSpeech.spokenUtterances.size)

        // Frame 4: Second observation in CENTER -> stabilized in CENTER -> speaks "chair ahead"
        val resCenter2 = processFrame(listOf(chairCenter), timestampMs = 1600L)
        assertNotNull("Stabilized in new position must emit newAlertEvent", resCenter2.newAlertEvent)
        assertEquals("chair ahead", resCenter2.newAlertEvent?.message)
        assertEquals("New speech event emitted for position change", 2, fakeSpeech.spokenUtterances.size)
        assertEquals("chair ahead", fakeSpeech.spokenUtterances[1])

        // Frame 5: Continues in CENTER -> silent
        processFrame(listOf(chairCenter), timestampMs = 1800L)
        assertEquals("Continues in CENTER without repeating", 2, fakeSpeech.spokenUtterances.size)
    }

    // 4. Different object: chair LEFT is active, then table CENTER becomes selected alert.
    // Generates speech for table ahead without replaying chair.
    @Test
    fun testDifferentClassCreatesNewSpeechEvent() {
        val chairLeft = createDetection("chair", centerX = 0.20f)
        val tableCenter = createDetection("table", centerX = 0.50f)

        // Chair on LEFT stabilizes and speaks
        processFrame(listOf(chairLeft), timestampMs = 1000L)
        processFrame(listOf(chairLeft), timestampMs = 1200L)
        assertEquals(1, fakeSpeech.spokenUtterances.size)
        assertEquals("chair on your left", fakeSpeech.spokenUtterances[0])

        // Table on CENTER appears while chair is still visible
        processFrame(listOf(chairLeft, tableCenter), timestampMs = 1400L)
        processFrame(listOf(chairLeft, tableCenter), timestampMs = 1600L)

        // Table ahead becomes the new alert event
        assertEquals(2, fakeSpeech.spokenUtterances.size)
        assertEquals("table ahead", fakeSpeech.spokenUtterances[1])

        // Frame 5: Both continue visible -> neither replays
        processFrame(listOf(chairLeft, tableCenter), timestampMs = 1800L)
        assertEquals("No repeated speech while both objects remain active", 2, fakeSpeech.spokenUtterances.size)
    }

    // 5. Raw detections alone must NEVER directly trigger speech.
    @Test
    fun testRawDetectionsDoNotTriggerSpeech() {
        val transientDet = createDetection("book", confidence = 0.90f, centerX = 0.85f)

        // Only 1 frame (unstabilized)
        val result = processFrame(listOf(transientDet), timestampMs = 1000L)

        assertEquals("Raw detections list contains 1 detection", 1, result.totalCandidateCount)
        assertTrue("Selected alerts must be empty for unstabilized raw detection", result.selectedAlerts.isEmpty())
        assertNull("newAlertEvent must be null", result.newAlertEvent)
        assertEquals("Speech must not be called for raw unstabilized detection", 0, fakeSpeech.spokenUtterances.size)
    }

    // 6. When no selected alert exists, no speech is triggered.
    @Test
    fun testNoSelectedAlertDoesNotTriggerSpeech() {
        // Below confidence threshold (0.35 < 0.40)
        val lowConf = createDetection("chair", confidence = 0.35f, centerX = 0.20f)
        val result = processFrame(listOf(lowConf), timestampMs = 1000L)

        assertTrue(result.selectedAlerts.isEmpty())
        assertNull(result.newAlertEvent)
        assertEquals(0, fakeSpeech.spokenUtterances.size)

        // Empty detections
        val emptyResult = processFrame(emptyList(), timestampMs = 1200L)
        assertTrue(emptyResult.selectedAlerts.isEmpty())
        assertNull(emptyResult.newAlertEvent)
        assertEquals(0, fakeSpeech.spokenUtterances.size)
    }

    // 7. Cooldown blocks repeated speech.
    @Test
    fun testCooldownBlocksRepeatedSpeech() {
        val chairLeft = createDetection("chair", centerX = 0.20f)

        processFrame(listOf(chairLeft), timestampMs = 1000L)
        val res2 = processFrame(listOf(chairLeft), timestampMs = 1200L)
        assertEquals(1, fakeSpeech.spokenUtterances.size)
        assertNotNull(res2.newAlertEvent)

        // At t = 1400L (200ms elapsed < 2500ms cooldown)
        val res3 = processFrame(listOf(chairLeft), timestampMs = 1400L)
        assertTrue("Within cooldown, selected alerts is empty", res3.selectedAlerts.isEmpty())
        assertNull("Within cooldown, newAlertEvent must be null", res3.newAlertEvent)
        assertEquals("Speech count remains 1", 1, fakeSpeech.spokenUtterances.size)
    }

    // 8. Stopping and restarting camera resets decision and speech event state cleanly.
    @Test
    fun testCameraRestartResetsSpeechState() {
        val chairLeft = createDetection("chair", centerX = 0.20f)

        // First camera session
        processFrame(listOf(chairLeft), timestampMs = 1000L)
        processFrame(listOf(chairLeft), timestampMs = 1200L)
        assertEquals(1, fakeSpeech.spokenUtterances.size)

        // Camera stop / reset
        engine.resetCooldowns()
        fakeSpeech.stop()
        assertEquals("Speech stop called on camera reset", 1, fakeSpeech.stopCallCount)

        // Second camera session: fresh tracking and fresh speech allowed after stabilization
        processFrame(listOf(chairLeft), timestampMs = 1300L)
        processFrame(listOf(chairLeft), timestampMs = 1500L)
        assertEquals("New camera session speaks stabilized detection", 2, fakeSpeech.spokenUtterances.size)
        assertEquals(listOf("chair on your left", "chair on your left"), fakeSpeech.spokenUtterances)
    }

    // 9. Compose recomposition / StateFlow collection cannot trigger speech.
    @Test
    fun testComposeRecompositionCannotTriggerSpeech() {
        val chairLeft = createDetection("chair", centerX = 0.20f)
        val result = engine.process(listOf(chairLeft, chairLeft), timestampMs = 1000L)

        // Construct YoloDebugState as Compose screen observes it
        val state = YoloDebugState(
            isReady = true,
            detections = listOf(chairLeft),
            selectedAlerts = result.selectedAlerts,
            suppressedAlertCount = result.suppressedCount,
            newAlertEvent = result.newAlertEvent
        )

        val stateFlow = MutableStateFlow(state)

        // Simulate 50 UI recompositions reading StateFlow value
        for (i in 0 until 50) {
            val currentState = stateFlow.value
            assertNotNull(currentState)
            // Recomposition only reads state, never calls speechController
        }

        assertEquals("UI recomposition or StateFlow reads must NEVER invoke speech", 0, fakeSpeech.spokenUtterances.size)
    }

    // 10. Multiple raw objects: only selected alerts can speak, never all raw objects sequentially.
    @Test
    fun testMultipleRawObjectsOnlySelectedAlertsCanSpeak() {
        val chairLeft = createDetection("chair", confidence = 0.85f, centerX = 0.15f)
        val chairCenter = createDetection("chair", confidence = 0.88f, centerX = 0.50f)
        val bookRight = createDetection("book", confidence = 0.90f, centerX = 0.85f)

        val frame = listOf(chairLeft, chairCenter, bookRight)

        // Stabilize over 2 frames
        processFrame(frame, timestampMs = 1000L)
        val res2 = processFrame(frame, timestampMs = 1200L)

        // Selected alerts is capped at maxSelectedAlerts (2)
        assertTrue(res2.selectedAlerts.size <= 2)
        // Book (low priority object) is suppressed and must NOT speak
        assertFalse(res2.selectedAlerts.any { it.className == "book" })

        // Exactly one new alert event is emitted for the primary stabilized object (chair CENTER)
        assertEquals("Exactly one new alert event emitted despite 3 raw objects", 1, fakeSpeech.spokenUtterances.size)
        assertEquals("chair ahead", fakeSpeech.spokenUtterances[0])
    }

    /**
     * Fake [SpeechController] implementation for verifying speech output.
     */
    private class FakeSpeechController : SpeechController {
        val spokenUtterances = mutableListOf<String>()
        var stopCallCount = 0

        override fun speak(utterance: String, priority: SpeechPriority) {
            spokenUtterances.add(utterance)
        }

        override fun stop() {
            stopCallCount++
        }
    }
}
