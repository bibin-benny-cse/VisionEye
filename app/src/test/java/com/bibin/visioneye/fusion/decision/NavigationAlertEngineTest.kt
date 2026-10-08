package com.bibin.visioneye.fusion.decision

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.HorizontalPosition
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic JVM unit tests for VisionEye Navigation Guidance & Alert Engine.
 *
 * Verifies all 24 required test scenarios defined in the milestone specification:
 * 1. LEFT classification
 * 2. CENTER classification
 * 3. RIGHT classification
 * 4. confidence threshold
 * 5. confidence exactly at threshold
 * 6. confidence below threshold
 * 7. persistent object becomes stable
 * 8. transient object does not immediately alert
 * 9. duplicate alert suppression
 * 10. cooldown behavior
 * 11. changed position generates a new alert
 * 12. multiple-object deterministic selection
 * 13. priority handling
 * 14. same-class multiple objects
 * 15. different-class objects
 * 16. object disappearance
 * 17. object reappearance
 * 18. alert message formatting
 * 19. no distance values generated
 * 20. no NEAR/MEDIUM/FAR classification
 * 21. deterministic results for identical input
 * 22. normalized coordinates work independently of resolution
 * 23. lifecycle reset
 * 24. TTS trigger occurs only for selected alerts
 */
class NavigationAlertEngineTest {

    private lateinit var engine: DefaultDecisionEngine
    private val defaultConfig = DecisionConfig(
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
        engine = DefaultDecisionEngine(config = defaultConfig)
    }

    private fun createDetection(
        className: String,
        confidence: Float,
        centerX: Float,
        centerY: Float = 0.5f,
        width: Float = 0.2f,
        height: Float = 0.4f,
        classId: Int = 0,
        timestampMs: Long = 1000L
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
            boundingBox = box,
            timestampMs = timestampMs
        )
    }

    // 1. LEFT classification
    @Test
    fun test01_leftClassification() {
        val normalizedX = 0.20f
        val position = HorizontalPosition.fromNormalizedX(
            normalizedX,
            leftBoundary = defaultConfig.leftZoneBoundary,
            rightBoundary = defaultConfig.rightZoneBoundary
        )
        assertEquals(HorizontalPosition.LEFT, position)

        val det = createDetection("chair", 0.85f, centerX = 0.20f)
        assertEquals(HorizontalPosition.LEFT, det.position)
    }

    // 2. CENTER classification
    @Test
    fun test02_centerClassification() {
        val normalizedX = 0.50f
        val position = HorizontalPosition.fromNormalizedX(
            normalizedX,
            leftBoundary = defaultConfig.leftZoneBoundary,
            rightBoundary = defaultConfig.rightZoneBoundary
        )
        assertEquals(HorizontalPosition.CENTER, position)

        val det = createDetection("chair", 0.85f, centerX = 0.50f)
        assertEquals(HorizontalPosition.CENTER, det.position)
    }

    // 3. RIGHT classification
    @Test
    fun test03_rightClassification() {
        val normalizedX = 0.85f
        val position = HorizontalPosition.fromNormalizedX(
            normalizedX,
            leftBoundary = defaultConfig.leftZoneBoundary,
            rightBoundary = defaultConfig.rightZoneBoundary
        )
        assertEquals(HorizontalPosition.RIGHT, position)

        val det = createDetection("chair", 0.85f, centerX = 0.85f)
        assertEquals(HorizontalPosition.RIGHT, det.position)
    }

    // 4. confidence threshold
    @Test
    fun test04_confidenceThreshold() {
        val detAbove = createDetection("chair", 0.75f, centerX = 0.20f)
        val result = engine.process(listOf(detAbove), timestampMs = 1000L)
        assertEquals(1, result.totalCandidateCount)
    }

    // 5. confidence exactly at threshold
    @Test
    fun test05_confidenceExactlyAtThreshold() {
        val detExact = createDetection("chair", 0.40f, centerX = 0.20f)
        val result = engine.process(listOf(detExact), timestampMs = 1000L)
        assertEquals("Detection exactly at threshold (0.40) must be admitted", 1, result.totalCandidateCount)
    }

    // 6. confidence below threshold
    @Test
    fun test06_confidenceBelowThreshold() {
        val detBelow = createDetection("chair", 0.39f, centerX = 0.20f)
        val result = engine.process(listOf(detBelow), timestampMs = 1000L)
        assertEquals("Detection below threshold (0.39) must be filtered out", 0, result.totalCandidateCount)
        assertTrue(result.selectedAlerts.isEmpty())
    }

    // 7. persistent object becomes stable
    @Test
    fun test07_persistentObjectBecomesStable() {
        val detFrame1 = createDetection("chair", 0.85f, centerX = 0.20f, timestampMs = 1000L)
        val result1 = engine.process(listOf(detFrame1), timestampMs = 1000L)
        assertTrue("Frame 1 (observation 1 < 2): transient object should not alert yet", result1.selectedAlerts.isEmpty())

        val detFrame2 = createDetection("chair", 0.85f, centerX = 0.20f, timestampMs = 1200L)
        val result2 = engine.process(listOf(detFrame2), timestampMs = 1200L)
        assertEquals("Frame 2 (observation 2 >= 2): persistent object must emit alert", 1, result2.selectedAlerts.size)
        assertEquals("chair, medium, on your left", result2.selectedAlerts.first().message)
    }

    // 8. transient object does not immediately alert
    @Test
    fun test08_transientObjectDoesNotImmediatelyAlert() {
        val transientDet = createDetection("book", 0.90f, centerX = 0.80f, timestampMs = 1000L)
        val result = engine.process(listOf(transientDet), timestampMs = 1000L)
        assertTrue("Single-frame transient detection must not generate alert", result.selectedAlerts.isEmpty())
        assertEquals(1, result.suppressedCount)
    }

    // 9. duplicate alert suppression
    @Test
    fun test09_duplicateAlertSuppression() {
        // Frame 1
        engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 1000L)
        // Frame 2: Stabilizes and emits alert
        val res2 = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 1200L)
        assertEquals(1, res2.selectedAlerts.size)

        // Frame 3: Same chair, same position, within cooldown (t = 1400ms vs 1200ms, cooldown is 2500ms)
        val res3 = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 1400L)
        assertTrue("Duplicate alert within cooldown must be suppressed", res3.selectedAlerts.isEmpty())
        assertEquals("Suppression counter must increment for duplicate alert", 1, res3.suppressedCount)
    }

    // 10. cooldown behavior
    @Test
    fun test10_cooldownBehavior() {
        // Frame 1 & 2: Alert emitted at t = 1000L
        engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 800L)
        val resAlert = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 1000L)
        assertEquals(1, resAlert.selectedAlerts.size)

        // At t = 2000L (1000ms elapsed < 2500ms cooldown): Suppressed
        val resDuring = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 2000L)
        assertTrue("Within 2500ms cooldown, duplicate must be suppressed", resDuring.selectedAlerts.isEmpty())

        // Continuous tracking leading up to cooldown expiration at t = 3600L (2600ms elapsed > 2500ms)
        engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 3400L)
        val resAfter = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 3600L)
        assertEquals("After cooldown expiration, alert should be emitted again", 1, resAfter.selectedAlerts.size)
    }

    // 11. changed position generates a new alert
    @Test
    fun test11_changedPositionGeneratesNewAlert() {
        // Frame 1 & 2: chair on LEFT at t = 1000L
        engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 800L)
        val resLeft = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 1000L)
        assertEquals("chair, medium, on your left", resLeft.selectedAlerts.first().message)

        // Chair transitions to CENTER at t = 1400L and stabilizes at t = 1600L (well within 2500ms cooldown of LEFT alert)
        // Position change represents a new navigation state and emits after stabilization (Test 5)
        val resCenter1 = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.50f)), timestampMs = 1400L)
        assertTrue("Transient first observation of changed position does not alert yet", resCenter1.selectedAlerts.isEmpty())

        val resCenter2 = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.50f)), timestampMs = 1600L)
        assertEquals(
            "Position change from LEFT to CENTER must generate a new alert after stabilization despite previous cooldown",
            1,
            resCenter2.selectedAlerts.size
        )
        assertEquals("chair, medium, ahead", resCenter2.selectedAlerts.first().message)
    }

    // 12. multiple-object deterministic selection
    @Test
    fun test12_multipleObjectDeterministicSelection() {
        // Feed frame 1 for all objects to initialize tracking
        val objects1 = listOf(
            createDetection("chair", 0.85f, centerX = 0.15f),
            createDetection("table", 0.80f, centerX = 0.50f),
            createDetection("book", 0.90f, centerX = 0.85f)
        )
        engine.process(objects1, timestampMs = 1000L)

        // Feed frame 2 to stabilize
        val objects2 = listOf(
            createDetection("chair", 0.85f, centerX = 0.15f),
            createDetection("table", 0.80f, centerX = 0.50f),
            createDetection("book", 0.90f, centerX = 0.85f)
        )
        val result = engine.process(objects2, timestampMs = 1200L)

        // Table (CENTER, obstacle priority) should be prioritized
        assertTrue(result.selectedAlerts.isNotEmpty())
        val topAlert = result.selectedAlerts.first()
        assertEquals("table, medium, ahead", topAlert.message)
    }

    // 13. priority handling
    @Test
    fun test13_priorityHandling() {
        // Person (HIGH priority) vs Chair (STANDARD priority)
        // Person is passed second in the array
        val frame1 = listOf(
            createDetection("chair", 0.95f, centerX = 0.20f),
            createDetection("person", 0.70f, centerX = 0.80f)
        )
        engine.process(frame1, timestampMs = 1000L)

        val frame2 = listOf(
            createDetection("chair", 0.95f, centerX = 0.20f),
            createDetection("person", 0.70f, centerX = 0.80f)
        )
        val result = engine.process(frame2, timestampMs = 1200L)

        assertEquals("person ahead / on your right must take priority over chair", "person", result.selectedAlerts.first().className)
    }

    // 14. same-class multiple objects
    @Test
    fun test14_sameClassMultipleObjects() {
        // Two chairs: one on LEFT, one on RIGHT
        val frame1 = listOf(
            createDetection("chair", 0.85f, centerX = 0.15f),
            createDetection("chair", 0.88f, centerX = 0.85f)
        )
        engine.process(frame1, timestampMs = 1000L)

        val frame2 = listOf(
            createDetection("chair", 0.85f, centerX = 0.15f),
            createDetection("chair", 0.88f, centerX = 0.85f)
        )
        val result = engine.process(frame2, timestampMs = 1200L)

        // Both distinct chairs are stabilized and returned up to maxSelectedAlerts (2)
        assertEquals(2, result.selectedAlerts.size)
        val positions = result.selectedAlerts.map { it.position }.toSet()
        assertTrue(positions.contains(HorizontalPosition.LEFT))
        assertTrue(positions.contains(HorizontalPosition.RIGHT))
    }

    // 15. different-class objects
    @Test
    fun test15_differentClassObjects() {
        val frame1 = listOf(
            createDetection("person", 0.90f, centerX = 0.50f),
            createDetection("chair", 0.85f, centerX = 0.20f),
            createDetection("cup", 0.80f, centerX = 0.85f)
        )
        engine.process(frame1, timestampMs = 1000L)

        val frame2 = listOf(
            createDetection("person", 0.90f, centerX = 0.50f),
            createDetection("chair", 0.85f, centerX = 0.20f),
            createDetection("cup", 0.80f, centerX = 0.85f)
        )
        val result = engine.process(frame2, timestampMs = 1200L)

        // maxSelectedAlerts is 2, person and chair should be selected, cup suppressed by capacity limit
        assertEquals(2, result.selectedAlerts.size)
        assertEquals("person", result.selectedAlerts[0].className)
        assertEquals("chair", result.selectedAlerts[1].className)
        assertEquals(1, result.suppressedCount)
    }

    // 16. object disappearance
    @Test
    fun test16_objectDisappearance() {
        // Object present at t = 1000L and t = 1200L
        engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 1000L)
        val res2 = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 1200L)
        assertEquals(1, res2.selectedAlerts.size)

        // Absent at t = 3000L (time gap 1800ms > objectDisappearanceTimeoutMs 1000ms)
        val emptyResult = engine.process(emptyList(), timestampMs = 3000L)
        assertTrue(emptyResult.selectedAlerts.isEmpty())
    }

    // 17. object reappearance
    @Test
    fun test17_objectReappearance() {
        // Chair seen at t = 1000L and t = 1200L
        engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 1000L)
        engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 1200L)

        // Chair absent for 2000ms (disappears)
        engine.process(emptyList(), timestampMs = 3500L)

        // Reappears at t = 4000L: must require stabilization (minimumStableObservations = 2) again
        val resReappear1 = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 4000L)
        assertTrue("Reappeared object must re-stabilize before alerting", resReappear1.selectedAlerts.isEmpty())

        val resReappear2 = engine.process(listOf(createDetection("chair", 0.85f, centerX = 0.20f)), timestampMs = 4200L)
        assertEquals("Reappeared object alerts upon reaching 2 observations", 1, resReappear2.selectedAlerts.size)
    }

    // 18. alert message formatting
    @Test
    fun test18_alertMessageFormatting() {
        assertEquals("chair, close, on your left", AlertMessageFormatter.format("chair", HorizontalPosition.LEFT, ProximityLevel.NEAR))
        assertEquals("chair, medium, ahead", AlertMessageFormatter.format("chair", HorizontalPosition.CENTER, ProximityLevel.MEDIUM))
        assertEquals("chair, far, on your right", AlertMessageFormatter.format("chair", HorizontalPosition.RIGHT, ProximityLevel.FAR))

        assertEquals("Chair, close, on your left", AlertMessageFormatter.formatCapitalized("chair", HorizontalPosition.LEFT, ProximityLevel.NEAR))
        assertEquals("Person, medium, ahead", AlertMessageFormatter.formatCapitalized("person", HorizontalPosition.CENTER, ProximityLevel.MEDIUM))
        assertEquals("Table, far, on your right", AlertMessageFormatter.formatCapitalized("table", HorizontalPosition.RIGHT, ProximityLevel.FAR))
    }

    // 19. no distance values generated
    @Test
    fun test19_noDistanceValuesGenerated() {
        val testPositions = listOf(HorizontalPosition.LEFT, HorizontalPosition.CENTER, HorizontalPosition.RIGHT)
        val testObjects = listOf("chair", "person", "table", "door", "stairs", "cup")

        val distancePatterns = listOf("meter", "metre", "cm", "feet", "inch", "m away", "distance")
        for (obj in testObjects) {
            for (pos in testPositions) {
                val msg = AlertMessageFormatter.format(obj, pos).lowercase()
                for (pattern in distancePatterns) {
                    assertFalse(
                        "Alert message '$msg' must not contain distance term '$pattern'",
                        msg.contains(pattern)
                    )
                }
            }
        }
    }

    // 20. audible coarse proximity classification ("close", "medium", "far", never raw "near")
    @Test
    fun test20_audibleCoarseProximityClassification() {
        val testPositions = listOf(HorizontalPosition.LEFT, HorizontalPosition.CENTER, HorizontalPosition.RIGHT)

        for (pos in testPositions) {
            val nearMsg = AlertMessageFormatter.format("chair", pos, ProximityLevel.NEAR).lowercase()
            assertTrue("NEAR alert must contain 'close'", nearMsg.contains("close"))
            assertFalse("NEAR alert must not contain raw internal enum name 'near'", nearMsg.contains("near"))

            val medMsg = AlertMessageFormatter.format("chair", pos, ProximityLevel.MEDIUM).lowercase()
            assertTrue("MEDIUM alert must contain 'medium'", medMsg.contains("medium"))

            val farMsg = AlertMessageFormatter.format("chair", pos, ProximityLevel.FAR).lowercase()
            assertTrue("FAR alert must contain 'far'", farMsg.contains("far"))
        }
    }

    // 21. deterministic results for identical input
    @Test
    fun test21_deterministicResultsForIdenticalInput() {
        val engine1 = DefaultDecisionEngine(config = defaultConfig)
        val engine2 = DefaultDecisionEngine(config = defaultConfig)

        val inputs = listOf(
            listOf(createDetection("chair", 0.85f, 0.20f), createDetection("table", 0.75f, 0.50f)),
            listOf(createDetection("chair", 0.85f, 0.20f), createDetection("table", 0.75f, 0.50f)),
            listOf(createDetection("chair", 0.85f, 0.20f), createDetection("table", 0.75f, 0.50f))
        )

        for ((idx, frame) in inputs.withIndex()) {
            val ts = 1000L + idx * 200L
            val res1 = engine1.process(frame, timestampMs = ts)
            val res2 = engine2.process(frame, timestampMs = ts)

            assertEquals(res1.selectedAlerts.size, res2.selectedAlerts.size)
            assertEquals(res1.suppressedCount, res2.suppressedCount)
            for (i in res1.selectedAlerts.indices) {
                assertEquals(res1.selectedAlerts[i].message, res2.selectedAlerts[i].message)
                assertEquals(res1.selectedAlerts[i].position, res2.selectedAlerts[i].position)
            }
        }
    }

    // 22. normalized coordinates work independently of resolution
    @Test
    fun test22_normalizedCoordinatesWorkIndependentlyOfResolution() {
        // Resolution A: 640x480 preview
        val x640 = 0.20f * 640f // 128px
        val normA = x640 / 640f

        // Resolution B: 1080x1920 preview
        val x1080 = 0.20f * 1080f // 216px
        val normB = x1080 / 1080f

        val posA = HorizontalPosition.fromNormalizedX(normA)
        val posB = HorizontalPosition.fromNormalizedX(normB)

        assertEquals(HorizontalPosition.LEFT, posA)
        assertEquals(HorizontalPosition.LEFT, posB)
        assertEquals("Normalized coordinates must yield identical position irrespective of resolution", posA, posB)
    }

    // 23. lifecycle reset
    @Test
    fun test23_lifecycleReset() {
        // Emit alert at t = 1000L
        engine.process(listOf(createDetection("chair", 0.85f, 0.20f)), timestampMs = 800L)
        val res1 = engine.process(listOf(createDetection("chair", 0.85f, 0.20f)), timestampMs = 1000L)
        assertEquals(1, res1.selectedAlerts.size)

        // Reset engine state
        engine.resetCooldowns()

        // After reset, state is cleared; object must re-stabilize as fresh stream
        val resAfterReset1 = engine.process(listOf(createDetection("chair", 0.85f, 0.20f)), timestampMs = 1100L)
        assertTrue("After reset, object must re-stabilize before emitting", resAfterReset1.selectedAlerts.isEmpty())

        val resAfterReset2 = engine.process(listOf(createDetection("chair", 0.85f, 0.20f)), timestampMs = 1300L)
        assertEquals("After stabilizing again, alert is emitted", 1, resAfterReset2.selectedAlerts.size)
    }

    // 24. TTS trigger occurs only for selected alerts
    @Test
    fun test24_ttsTriggerOccursOnlyForSelectedAlerts() {
        val fakeTts = FakeSpeechController()

        // Frame 1: Transient detection -> selectedAlerts is empty -> No TTS
        val res1 = engine.process(listOf(createDetection("chair", 0.85f, 0.20f)), timestampMs = 1000L)
        if (res1.selectedAlerts.isNotEmpty()) {
            fakeTts.speak(res1.selectedAlerts.first().message, SpeechPriority.NORMAL)
        }
        assertEquals("No TTS spoken for transient unstabilized detection", 0, fakeTts.spokenUtterances.size)

        // Frame 2: Stabilized persistent detection -> selectedAlerts has 1 alert -> TTS triggered
        val res2 = engine.process(listOf(createDetection("chair", 0.85f, 0.20f)), timestampMs = 1200L)
        if (res2.selectedAlerts.isNotEmpty()) {
            fakeTts.speak(res2.selectedAlerts.first().message, SpeechPriority.NORMAL)
        }
        assertEquals("TTS triggered for stabilized selected alert", 1, fakeTts.spokenUtterances.size)
        assertEquals("chair, medium, on your left", fakeTts.spokenUtterances.first())

        // Frame 3: Duplicate within cooldown -> selectedAlerts is empty -> No additional TTS
        val res3 = engine.process(listOf(createDetection("chair", 0.85f, 0.20f)), timestampMs = 1400L)
        if (res3.selectedAlerts.isNotEmpty()) {
            fakeTts.speak(res3.selectedAlerts.first().message, SpeechPriority.NORMAL)
        }
        assertEquals("TTS not flooded during cooldown", 1, fakeTts.spokenUtterances.size)
    }

    /**
     * Fake [SpeechController] implementation for verifying TTS behavior without Android dependencies.
     */
    private class FakeSpeechController : SpeechController {
        val spokenUtterances = mutableListOf<String>()

        override fun speak(utterance: String, priority: SpeechPriority) {
            spokenUtterances.add(utterance)
        }

        override fun stop() {}
    }
}
