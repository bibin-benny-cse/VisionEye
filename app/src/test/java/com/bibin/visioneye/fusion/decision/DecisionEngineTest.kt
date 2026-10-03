package com.bibin.visioneye.fusion.decision

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.HorizontalPosition
import com.bibin.visioneye.ai.YoloDebugState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DecisionEngineTest {

    private lateinit var decisionEngine: DecisionEngine

    @Before
    fun setup() {
        decisionEngine = DefaultDecisionEngine(cooldownPeriodMs = 3000L)
    }

    @Test
    fun priorityRanking_matchesSpecification() {
        // Spec: collision warning > immediate obstacle > navigation instruction > general object information
        assertTrue(AlertPriority.COLLISION_WARNING.rank > AlertPriority.IMMEDIATE_OBSTACLE.rank)
        assertTrue(AlertPriority.IMMEDIATE_OBSTACLE.rank > AlertPriority.NAVIGATION_INSTRUCTION.rank)
        assertTrue(AlertPriority.NAVIGATION_INSTRUCTION.rank > AlertPriority.GENERAL_OBJECT_INFO.rank)
    }

    @Test
    fun firstAlert_emittedSuccessfully() {
        val alert = AlertItem(
            key = "obstacle_person_ahead",
            spokenText = "Person ahead.",
            priority = AlertPriority.IMMEDIATE_OBSTACLE,
            timestampMs = 1000L
        )

        assertTrue(decisionEngine.shouldEmitAlert(alert))
    }

    @Test
    fun duplicateAlert_withinCooldown_isSuppressed() {
        val firstAlert = AlertItem(
            key = "obstacle_person_ahead",
            spokenText = "Person ahead.",
            priority = AlertPriority.IMMEDIATE_OBSTACLE,
            timestampMs = 1000L
        )
        val duplicateAlert = AlertItem(
            key = "obstacle_person_ahead",
            spokenText = "Person ahead.",
            priority = AlertPriority.IMMEDIATE_OBSTACLE,
            timestampMs = 2500L // 1.5s later (cooldown is 3s)
        )

        assertTrue(decisionEngine.shouldEmitAlert(firstAlert))
        assertFalse("Duplicate alert within cooldown period must be suppressed", decisionEngine.shouldEmitAlert(duplicateAlert))
    }

    @Test
    fun duplicateAlert_afterCooldownExpires_isEmitted() {
        val firstAlert = AlertItem(
            key = "obstacle_vehicle_left",
            spokenText = "Vehicle on your left.",
            priority = AlertPriority.IMMEDIATE_OBSTACLE,
            timestampMs = 1000L
        )
        val subsequentAlert = AlertItem(
            key = "obstacle_vehicle_left",
            spokenText = "Vehicle on your left.",
            priority = AlertPriority.IMMEDIATE_OBSTACLE,
            timestampMs = 5000L // 4s later (cooldown is 3s)
        )

        assertTrue(decisionEngine.shouldEmitAlert(firstAlert))
        assertTrue("Alert after cooldown expiration should be emitted", decisionEngine.shouldEmitAlert(subsequentAlert))
    }

    @Test
    fun distinctAlertKeys_doNotSuppressEachOther() {
        val alertA = AlertItem(
            key = "obstacle_person_ahead",
            spokenText = "Person ahead.",
            priority = AlertPriority.IMMEDIATE_OBSTACLE,
            timestampMs = 1000L
        )
        val alertB = AlertItem(
            key = "obstacle_close",
            spokenText = "Obstacle close.",
            priority = AlertPriority.IMMEDIATE_OBSTACLE,
            timestampMs = 1500L
        )

        assertTrue(decisionEngine.shouldEmitAlert(alertA))
        assertTrue("Different alert key should not be suppressed by previous alert", decisionEngine.shouldEmitAlert(alertB))
    }

    @Test
    fun collisionWarning_bypassesNormalCooldown() {
        val collision1 = AlertItem(
            key = "collision_fast_approach",
            spokenText = "Stop. Vehicle approaching.",
            priority = AlertPriority.COLLISION_WARNING,
            timestampMs = 1000L
        )
        val collision2 = AlertItem(
            key = "collision_fast_approach",
            spokenText = "Stop. Vehicle approaching.",
            priority = AlertPriority.COLLISION_WARNING,
            timestampMs = 2500L // 1.5s later (normal cooldown is 3s)
        )

        assertTrue(decisionEngine.shouldEmitAlert(collision1))
        assertTrue("Critical collision warnings break through normal cooldown", decisionEngine.shouldEmitAlert(collision2))
    }

    @Test
    fun resetCooldowns_allowsImmediateReplay() {
        val alert = AlertItem(
            key = "turn_right",
            spokenText = "Turn right in 10 meters.",
            priority = AlertPriority.NAVIGATION_INSTRUCTION,
            timestampMs = 1000L
        )

        assertTrue(decisionEngine.shouldEmitAlert(alert))
        decisionEngine.resetCooldowns()

        val alertImmediate = alert.copy(timestampMs = 1100L)
        assertTrue("After resetting cooldowns, alert should be permitted", decisionEngine.shouldEmitAlert(alertImmediate))
    }

    // =========================================================================
    // MILESTONE 5: CONTEXT-AWARE DETECTION DECISION ENGINE TESTS
    // =========================================================================

    @Test
    fun milestone5_testA_leftDetection_chairGeneratesCorrectMessage() {
        val detection = com.bibin.visioneye.ai.Detection(
            classId = 56,
            label = "chair",
            confidence = 0.88f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.05f, 0.2f, 0.25f, 0.8f),
            position = com.bibin.visioneye.ai.HorizontalPosition.LEFT
        )
        val result = decisionEngine.process(listOf(detection), timestampMs = 1000L)

        assertEquals(1, result.selectedAlerts.size)
        val alert = result.selectedAlerts[0]
        assertEquals("chair", alert.className)
        assertEquals(com.bibin.visioneye.ai.HorizontalPosition.LEFT, alert.position)
        assertEquals("chair on your left", alert.message)
    }

    @Test
    fun milestone5_testB_centerDetection_tableGeneratesCorrectMessage() {
        val detection = com.bibin.visioneye.ai.Detection(
            classId = 60,
            label = "dining table",
            confidence = 0.85f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.40f, 0.2f, 0.60f, 0.8f),
            position = com.bibin.visioneye.ai.HorizontalPosition.CENTER
        )
        val result = decisionEngine.process(listOf(detection), timestampMs = 1000L)

        assertEquals(1, result.selectedAlerts.size)
        val alert = result.selectedAlerts[0]
        assertEquals("dining table", alert.className)
        assertEquals(com.bibin.visioneye.ai.HorizontalPosition.CENTER, alert.position)
        assertEquals("dining table ahead", alert.message)

        // Also test direct "table" alias
        val message = AlertMessageFormatter.format("table", com.bibin.visioneye.ai.HorizontalPosition.CENTER)
        assertEquals("table ahead", message)
    }

    @Test
    fun milestone5_testC_rightDetection_cupGeneratesCorrectMessage() {
        val detection = com.bibin.visioneye.ai.Detection(
            classId = 41,
            label = "cup",
            confidence = 0.75f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.80f, 0.3f, 0.95f, 0.7f),
            position = com.bibin.visioneye.ai.HorizontalPosition.RIGHT
        )
        val result = decisionEngine.process(listOf(detection), timestampMs = 1000L)

        assertEquals(1, result.selectedAlerts.size)
        val alert = result.selectedAlerts[0]
        assertEquals("cup", alert.className)
        assertEquals(com.bibin.visioneye.ai.HorizontalPosition.RIGHT, alert.position)
        assertEquals("cup on your right", alert.message)
    }

    @Test
    fun milestone5_testD_priorityOrdering_higherPriorityClassesSelectedFirst() {
        // Detections: cup (low), person (urgent), chair (medium)
        val cup = com.bibin.visioneye.ai.Detection(
            classId = 41, label = "cup", confidence = 0.95f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.8f, 0.2f, 0.9f, 0.4f),
            position = com.bibin.visioneye.ai.HorizontalPosition.RIGHT
        )
        val person = com.bibin.visioneye.ai.Detection(
            classId = 0, label = "person", confidence = 0.70f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.4f, 0.1f, 0.6f, 0.9f),
            position = com.bibin.visioneye.ai.HorizontalPosition.CENTER
        )
        val chair = com.bibin.visioneye.ai.Detection(
            classId = 56, label = "chair", confidence = 0.80f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.1f, 0.3f, 0.3f, 0.7f),
            position = com.bibin.visioneye.ai.HorizontalPosition.LEFT
        )

        val engine = DefaultDecisionEngine(DecisionConfig(maxSelectedAlerts = 2, minimumStableObservations = 1))
        val result = engine.process(listOf(cup, person, chair), timestampMs = 1000L)

        assertEquals(2, result.selectedAlerts.size)
        assertEquals("person", result.selectedAlerts[0].className)
        assertEquals("chair", result.selectedAlerts[1].className)
        assertEquals(1, result.suppressedCount) // cup suppressed due to lower priority
    }

    @Test
    fun milestone5_testE_confidenceFiltering_respectsConfiguredAlertThreshold() {
        val config = DecisionConfig(alertConfidenceThreshold = 0.60f, minimumStableObservations = 1)
        val engine = DefaultDecisionEngine(config)

        val highConfChair = com.bibin.visioneye.ai.Detection(
            classId = 56, label = "chair", confidence = 0.75f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.1f, 0.2f, 0.3f, 0.6f),
            position = com.bibin.visioneye.ai.HorizontalPosition.LEFT
        )
        val lowConfCup = com.bibin.visioneye.ai.Detection(
            classId = 41, label = "cup", confidence = 0.55f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.8f, 0.3f, 0.9f, 0.5f),
            position = com.bibin.visioneye.ai.HorizontalPosition.RIGHT
        )

        val result = engine.process(listOf(highConfChair, lowConfCup), timestampMs = 1000L)

        assertEquals(1, result.selectedAlerts.size)
        assertEquals("chair", result.selectedAlerts[0].className)
        assertEquals(1, result.totalCandidateCount) // only 1 passed confidence threshold
    }

    @Test
    fun milestone5_testF_maximumCandidateCount_limitsSelectedAlertsToMaxSelectedAlerts() {
        val engine = DefaultDecisionEngine(DecisionConfig(maxSelectedAlerts = 2, minimumStableObservations = 1))

        val detections = (1..10).map { i ->
            com.bibin.visioneye.ai.Detection(
                classId = i,
                label = "item_$i",
                confidence = 0.50f + (i * 0.04f),
                boundingBox = com.bibin.visioneye.ai.BoundingBox(0.1f, 0.1f, 0.5f, 0.5f),
                position = com.bibin.visioneye.ai.HorizontalPosition.CENTER
            )
        }

        val result = engine.process(detections, timestampMs = 1000L)

        assertEquals(2, result.selectedAlerts.size)
        assertEquals(8, result.suppressedCount)
        assertEquals(10, result.totalCandidateCount)
    }

    @Test
    fun milestone5_testG_cooldown_sameClassSamePositionRepeatedImmediatelyIsSuppressed() {
        val engine = DefaultDecisionEngine(DecisionConfig(cooldownPeriodMs = 4000L, minimumStableObservations = 1))

        val chairLeft1 = com.bibin.visioneye.ai.Detection(
            classId = 56, label = "chair", confidence = 0.85f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.1f, 0.2f, 0.3f, 0.6f),
            position = com.bibin.visioneye.ai.HorizontalPosition.LEFT
        )
        val chairLeft2 = com.bibin.visioneye.ai.Detection(
            classId = 56, label = "chair", confidence = 0.88f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.12f, 0.22f, 0.32f, 0.62f),
            position = com.bibin.visioneye.ai.HorizontalPosition.LEFT
        )

        // First frame at t = 1000ms
        val result1 = engine.process(listOf(chairLeft1), timestampMs = 1000L)
        assertEquals(1, result1.selectedAlerts.size)
        assertEquals(0, result1.suppressedCount)

        // Second frame at t = 2000ms (1000ms later, within 4000ms cooldown)
        val result2 = engine.process(listOf(chairLeft2), timestampMs = 2000L)
        assertEquals(0, result2.selectedAlerts.size)
        assertEquals(1, result2.suppressedCount)
    }

    @Test
    fun milestone5_testH_cooldownExpiration_sameClassSamePositionAfterCooldownIsPermitted() {
        val engine = DefaultDecisionEngine(DecisionConfig(cooldownPeriodMs = 4000L, minimumStableObservations = 1))

        val chairLeft = com.bibin.visioneye.ai.Detection(
            classId = 56, label = "chair", confidence = 0.85f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.1f, 0.2f, 0.3f, 0.6f),
            position = com.bibin.visioneye.ai.HorizontalPosition.LEFT
        )

        // First frame at t = 1000ms
        val result1 = engine.process(listOf(chairLeft), timestampMs = 1000L)
        assertEquals(1, result1.selectedAlerts.size)

        // Second frame after cooldown at t = 5500ms (4500ms later > 4000ms)
        val result2 = engine.process(listOf(chairLeft), timestampMs = 5500L)
        assertEquals(1, result2.selectedAlerts.size)
        assertEquals("chair on your left", result2.selectedAlerts[0].message)
    }

    @Test
    fun milestone5_testI_twoObjectsOfSameClassInDifferentPositions_bothValid() {
        val engine = DefaultDecisionEngine(DecisionConfig(maxSelectedAlerts = 2, minimumStableObservations = 1))

        val chairLeft = com.bibin.visioneye.ai.Detection(
            classId = 56, label = "chair", confidence = 0.89f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.1f, 0.2f, 0.3f, 0.7f),
            position = com.bibin.visioneye.ai.HorizontalPosition.LEFT
        )
        val chairRight = com.bibin.visioneye.ai.Detection(
            classId = 56, label = "chair", confidence = 0.92f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.7f, 0.2f, 0.9f, 0.7f),
            position = com.bibin.visioneye.ai.HorizontalPosition.RIGHT
        )

        val result = engine.process(listOf(chairLeft, chairRight), timestampMs = 1000L)

        assertEquals(2, result.selectedAlerts.size)
        val messages = result.selectedAlerts.map { it.message }
        assertTrue(messages.contains("chair on your left"))
        assertTrue(messages.contains("chair on your right"))
        assertEquals(0, result.suppressedCount)
    }

    @Test
    fun milestone5_testJ_deterministicOrdering_sameDetectionsProduceSameOutputOrder() {
        val engine = DefaultDecisionEngine(DecisionConfig(maxSelectedAlerts = 3, minimumStableObservations = 1))

        val d1 = com.bibin.visioneye.ai.Detection(
            classId = 0, label = "person", confidence = 0.90f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.4f, 0.2f, 0.6f, 0.8f),
            position = com.bibin.visioneye.ai.HorizontalPosition.CENTER
        )
        val d2 = com.bibin.visioneye.ai.Detection(
            classId = 2, label = "car", confidence = 0.85f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.7f, 0.2f, 0.9f, 0.8f),
            position = com.bibin.visioneye.ai.HorizontalPosition.RIGHT
        )
        val d3 = com.bibin.visioneye.ai.Detection(
            classId = 56, label = "chair", confidence = 0.95f,
            boundingBox = com.bibin.visioneye.ai.BoundingBox(0.05f, 0.2f, 0.25f, 0.8f),
            position = com.bibin.visioneye.ai.HorizontalPosition.LEFT
        )

        // Run 1 with list [d1, d2, d3]
        engine.resetCooldowns()
        val result1 = engine.process(listOf(d1, d2, d3), timestampMs = 1000L)

        // Run 2 with reversed list [d3, d1, d2]
        engine.resetCooldowns()
        val result2 = engine.process(listOf(d3, d1, d2), timestampMs = 1000L)

        assertEquals(result1.selectedAlerts.map { it.message }, result2.selectedAlerts.map { it.message })
        assertEquals("person ahead", result1.selectedAlerts[0].message)
        assertEquals("car on your right", result1.selectedAlerts[1].message)
        assertEquals("chair on your left", result1.selectedAlerts[2].message)
    }

    @Test
    fun milestone5_testK_noUnsafeTerminology_messagesAvoidObstacleDangerCollisionStop() {
        val forbiddenWords = listOf("obstacle", "danger", "collision", "stop")
        val sampleClasses = listOf("chair", "table", "person", "bed", "car", "cup", "handbag", "refrigerator", "laptop", "door")

        for (cls in sampleClasses) {
            for (pos in com.bibin.visioneye.ai.HorizontalPosition.entries) {
                val message = AlertMessageFormatter.format(cls, pos).lowercase()
                for (forbidden in forbiddenWords) {
                    assertFalse(
                        "Generated message '$message' must not contain forbidden word '$forbidden'",
                        message.contains(forbidden)
                    )
                }
            }
        }
    }

    // =========================================================================
    // MILESTONE 5A: DECISION-ENGINE & UI INTEGRATION BOUNDARY TESTS (A - H)
    // =========================================================================

    /**
     * Requirement A: Three valid detections with maxSelectedAlerts = 2
     * -> two selected alerts.
     */
    @Test
    fun milestone5a_testA_threeValidDetectionsWithMaxSelectedAlerts2_yieldsTwoSelectedAlerts() {
        val engine = DefaultDecisionEngine(DecisionConfig(maxSelectedAlerts = 2, minimumStableObservations = 1))

        val chairLeft = Detection(
            classId = 56, label = "chair", confidence = 0.90f,
            boundingBox = BoundingBox(0.05f, 0.2f, 0.25f, 0.8f),
            position = HorizontalPosition.LEFT
        )
        val chairRight = Detection(
            classId = 56, label = "chair", confidence = 0.85f,
            boundingBox = BoundingBox(0.70f, 0.2f, 0.95f, 0.8f),
            position = HorizontalPosition.RIGHT
        )
        val chairLeft2 = Detection(
            classId = 56, label = "chair", confidence = 0.65f,
            boundingBox = BoundingBox(0.10f, 0.3f, 0.30f, 0.7f),
            position = HorizontalPosition.LEFT
        )

        val result = engine.process(listOf(chairLeft, chairRight, chairLeft2), timestampMs = 1000L)

        assertEquals("Exactly 2 alerts should be selected when maxSelectedAlerts = 2", 2, result.selectedAlerts.size)
        assertEquals("chair on your left", result.selectedAlerts[0].message)
        assertEquals("chair on your right", result.selectedAlerts[1].message)
    }

    /**
     * Requirement B: Suppressed count reflects the unselected valid candidates.
     */
    @Test
    fun milestone5a_testB_suppressedCount_reflectsUnselectedValidCandidates() {
        val engine = DefaultDecisionEngine(DecisionConfig(maxSelectedAlerts = 2, minimumStableObservations = 1))

        val chairLeft = Detection(
            classId = 56, label = "chair", confidence = 0.90f,
            boundingBox = BoundingBox(0.05f, 0.2f, 0.25f, 0.8f),
            position = HorizontalPosition.LEFT
        )
        val chairRight = Detection(
            classId = 56, label = "chair", confidence = 0.85f,
            boundingBox = BoundingBox(0.70f, 0.2f, 0.95f, 0.8f),
            position = HorizontalPosition.RIGHT
        )
        val chairLeft2 = Detection(
            classId = 56, label = "chair", confidence = 0.65f,
            boundingBox = BoundingBox(0.10f, 0.3f, 0.30f, 0.7f),
            position = HorizontalPosition.LEFT
        )

        val result = engine.process(listOf(chairLeft, chairRight, chairLeft2), timestampMs = 1000L)

        assertEquals("Total candidate count should reflect all 3 valid detections above threshold", 3, result.totalCandidateCount)
        assertEquals("Suppressed count must reflect the unselected valid candidates (3 - 2 = 1)", 1, result.suppressedCount)
        assertEquals(2, result.selectedAlerts.size)
    }

    /**
     * Requirement C: DecisionEngine state persists between processing calls.
     */
    @Test
    fun milestone5a_testC_decisionEngineStatePersistsBetweenProcessingCalls() {
        val engine = DefaultDecisionEngine(DecisionConfig(cooldownPeriodMs = 4000L, maxSelectedAlerts = 2, minimumStableObservations = 1))

        val chairLeft = Detection(
            classId = 56, label = "chair", confidence = 0.90f,
            boundingBox = BoundingBox(0.05f, 0.2f, 0.25f, 0.8f),
            position = HorizontalPosition.LEFT
        )

        // Frame 1 at t = 1000ms: Candidate is selected
        val result1 = engine.process(listOf(chairLeft), timestampMs = 1000L)
        assertEquals(1, result1.selectedAlerts.size)
        assertEquals(0, result1.suppressedCount)

        // Frame 2 at t = 1344ms: Reusing the same engine instance, candidate must be suppressed by cooldown
        val result2 = engine.process(listOf(chairLeft), timestampMs = 1344L)
        assertEquals("Reused engine instance must retain cooldown state across frames", 0, result2.selectedAlerts.size)
        assertEquals("Suppression count must be 1 on consecutive frame within cooldown", 1, result2.suppressedCount)

        // Frame 3 at t = 5001ms: Cooldown expires (4000ms after t = 1000ms), alert is selected again
        val result3 = engine.process(listOf(chairLeft), timestampMs = 5001L)
        assertEquals("After cooldown expires on same instance, candidate is selected again", 1, result3.selectedAlerts.size)
        assertEquals(0, result3.suppressedCount)
    }

    /**
     * Requirement D: Repeated same class + same position within cooldown is suppressed.
     */
    @Test
    fun milestone5a_testD_repeatedSameClassSamePositionWithinCooldown_isSuppressed() {
        val engine = DefaultDecisionEngine(DecisionConfig(cooldownPeriodMs = 4000L, minimumStableObservations = 1))

        val cupCenter1 = Detection(
            classId = 41, label = "cup", confidence = 0.80f,
            boundingBox = BoundingBox(0.4f, 0.4f, 0.6f, 0.6f),
            position = HorizontalPosition.CENTER
        )
        val cupCenter2 = Detection(
            classId = 41, label = "cup", confidence = 0.82f,
            boundingBox = BoundingBox(0.42f, 0.41f, 0.61f, 0.62f),
            position = HorizontalPosition.CENTER
        )

        // First presentation at t = 2000ms
        val result1 = engine.process(listOf(cupCenter1), timestampMs = 2000L)
        assertEquals(1, result1.selectedAlerts.size)
        assertEquals("cup ahead", result1.selectedAlerts[0].message)

        // Repeated presentation at t = 2344ms (344ms later, within 4000ms cooldown)
        val result2 = engine.process(listOf(cupCenter2), timestampMs = 2344L)
        assertEquals("Repeated same-class and same-position detection must be suppressed", 0, result2.selectedAlerts.size)
        assertEquals(1, result2.suppressedCount)
    }

    /**
     * Requirement E: Different positions remain independently eligible.
     */
    @Test
    fun milestone5a_testE_differentPositionsRemainIndependentlyEligible() {
        val engine = DefaultDecisionEngine(DecisionConfig(cooldownPeriodMs = 4000L, maxSelectedAlerts = 2, minimumStableObservations = 1))

        val chairLeft = Detection(
            classId = 56, label = "chair", confidence = 0.90f,
            boundingBox = BoundingBox(0.05f, 0.2f, 0.25f, 0.8f),
            position = HorizontalPosition.LEFT
        )
        val chairRight = Detection(
            classId = 56, label = "chair", confidence = 0.85f,
            boundingBox = BoundingBox(0.70f, 0.2f, 0.95f, 0.8f),
            position = HorizontalPosition.RIGHT
        )

        // Verify deduplication keys differ
        assertEquals("chair_LEFT", chairLeft.label + "_" + chairLeft.position.name)
        assertEquals("chair_RIGHT", chairRight.label + "_" + chairRight.position.name)

        // Both in same frame
        val result = engine.process(listOf(chairLeft, chairRight), timestampMs = 1000L)
        assertEquals("Both distinct positions must be selected", 2, result.selectedAlerts.size)
        val messages = result.selectedAlerts.map { it.message }
        assertTrue(messages.contains("chair on your left"))
        assertTrue(messages.contains("chair on your right"))
        assertEquals(0, result.suppressedCount)

        // In subsequent frame at t = 2000ms, a new position CENTER appears
        val chairCenter = Detection(
            classId = 56, label = "chair", confidence = 0.88f,
            boundingBox = BoundingBox(0.4f, 0.2f, 0.6f, 0.8f),
            position = HorizontalPosition.CENTER
        )
        val resultNext = engine.process(listOf(chairLeft, chairRight, chairCenter), timestampMs = 2000L)
        // LEFT and RIGHT are on cooldown, but CENTER is new and eligible!
        assertEquals("New position CENTER should be selected even while LEFT and RIGHT are on cooldown", 1, resultNext.selectedAlerts.size)
        assertEquals("chair ahead", resultNext.selectedAlerts[0].message)
        assertEquals("LEFT and RIGHT should be suppressed (2)", 2, resultNext.suppressedCount)
    }

    /**
     * Requirement F: Raw detection count remains independent from selected-alert count.
     */
    @Test
    fun milestone5a_testF_rawDetectionCountRemainsIndependentFromSelectedAlertCount() {
        val engine = DefaultDecisionEngine(DecisionConfig(maxSelectedAlerts = 2, minimumStableObservations = 1))

        val rawDetections = listOf(
            Detection(56, "chair", 0.90f, BoundingBox(0.05f, 0.2f, 0.25f, 0.8f), HorizontalPosition.LEFT),
            Detection(56, "chair", 0.85f, BoundingBox(0.70f, 0.2f, 0.95f, 0.8f), HorizontalPosition.RIGHT),
            Detection(56, "chair", 0.65f, BoundingBox(0.10f, 0.3f, 0.30f, 0.7f), HorizontalPosition.LEFT)
        )

        val result = engine.process(rawDetections, timestampMs = 1000L)

        // Raw detection count remains 3
        assertEquals(3, rawDetections.size)
        // Selected alert count is bounded at 2
        assertEquals(2, result.selectedAlerts.size)
        // Verified independent concepts
        assertTrue("Raw detections (3) and selected alerts (2) are decoupled", rawDetections.size != result.selectedAlerts.size)
    }

    /**
     * Requirement G: The UI state contains both raw detections and selected alerts.
     */
    @Test
    fun milestone5a_testG_uiStateContainsBothRawDetectionsAndSelectedAlerts() {
        val engine = DefaultDecisionEngine(DecisionConfig(maxSelectedAlerts = 2, minimumStableObservations = 1))

        val rawDetections = listOf(
            Detection(56, "chair", 0.90f, BoundingBox(0.05f, 0.2f, 0.25f, 0.8f), HorizontalPosition.LEFT),
            Detection(56, "chair", 0.85f, BoundingBox(0.70f, 0.2f, 0.95f, 0.8f), HorizontalPosition.RIGHT),
            Detection(56, "chair", 0.65f, BoundingBox(0.10f, 0.3f, 0.30f, 0.7f), HorizontalPosition.LEFT)
        )

        val result = engine.process(rawDetections, timestampMs = 1000L)

        // Construct YoloDebugState exactly as YoloFrameAnalyzer does
        val uiState = YoloDebugState(
            isReady = true,
            inferenceTimeMs = 344L,
            detections = rawDetections,
            selectedAlerts = result.selectedAlerts,
            suppressedAlertCount = result.suppressedCount
        )

        // Verify UI state holds both independently
        assertEquals(3, uiState.detections.size)
        assertEquals(2, uiState.selectedAlerts.size)
        assertEquals(1, uiState.suppressedAlertCount)

        // Verify raw detections content
        assertEquals("chair", uiState.detections[0].className)
        assertEquals(HorizontalPosition.LEFT, uiState.detections[0].position)
        assertEquals(0.90f, uiState.detections[0].confidence, 0.001f)

        // Verify selected alert content
        assertEquals("chair on your left", uiState.selectedAlerts[0].message)
        assertEquals("chair on your right", uiState.selectedAlerts[1].message)
    }

    /**
     * Requirement H: No selected-alert list exceeds maxSelectedAlerts.
     */
    @Test
    fun milestone5a_testH_noSelectedAlertListExceedsMaxSelectedAlerts() {
        val engine = DefaultDecisionEngine(DecisionConfig(maxSelectedAlerts = 2, minimumStableObservations = 1))

        val candidateClasses = listOf("person", "car", "bicycle", "chair", "table", "cup")
        val detections = candidateClasses.mapIndexed { index, name ->
            Detection(
                classId = index,
                label = name,
                confidence = 0.70f + (index * 0.03f),
                boundingBox = BoundingBox(0.1f, 0.1f, 0.5f, 0.5f),
                position = when (index % 3) {
                    0 -> HorizontalPosition.LEFT
                    1 -> HorizontalPosition.CENTER
                    else -> HorizontalPosition.RIGHT
                }
            )
        }

        val result = engine.process(detections, timestampMs = 1000L)

        assertTrue(
            "Selected alerts count (${result.selectedAlerts.size}) must never exceed maxSelectedAlerts (2)",
            result.selectedAlerts.size <= 2
        )
        assertEquals(2, result.selectedAlerts.size)
        assertEquals(4, result.suppressedCount)
        assertEquals(6, result.totalCandidateCount)
    }
}
