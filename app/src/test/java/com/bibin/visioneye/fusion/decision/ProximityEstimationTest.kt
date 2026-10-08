package com.bibin.visioneye.fusion.decision

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.HorizontalPosition
import com.bibin.visioneye.speech.DefaultSpeechEventArbitrator
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic JVM unit tests for coarse proximity estimation in VisionEye NAVIGATE.
 *
 * Verifies scenarios A through J specified in the architectural requirements:
 * A. Basic classification (FAR, MEDIUM, NEAR)
 * B. Class-specific thresholds across object tiers
 * C. Exponential Moving Average (EMA) smoothing prevents jitter
 * D. Dual-threshold hysteresis prevents boundary chattering
 * E. Two-frame transition confirmation
 * F. MEDIUM -> NEAR escalation triggers a new urgent speech event
 * G. NEAR -> MEDIUM de-escalation remains silent
 * H. Stable NEAR does not repeatedly alert or speak
 * I. Object disappearance clears proximity history
 * J. Alert priority and capacity cap remain strictly preserved
 */
class ProximityEstimationTest {

    private lateinit var config: DecisionConfig
    private lateinit var engine: DefaultDecisionEngine

    @Before
    fun setup() {
        config = DecisionConfig(
            alertConfidenceThreshold = 0.40f,
            minimumStableObservations = 2,
            cooldownPeriodMs = 2500L,
            maxSelectedAlerts = 2,
            leftZoneBoundary = 0.33f,
            rightZoneBoundary = 0.67f,
            trackingMaxDisplacement = 0.25f,
            objectDisappearanceTimeoutMs = 1000L,
            proximitySmoothingAlpha = 0.35f,
            proximityHysteresisMargin = 0.04f,
            proximityPersistenceObservations = 2
        )
        engine = DefaultDecisionEngine(config = config)
    }

    private fun createDetection(
        className: String,
        confidence: Float = 0.85f,
        centerX: Float = 0.50f,
        centerY: Float = 0.50f,
        width: Float = 0.30f,
        height: Float = 0.40f,
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
            classId = 0,
            label = className,
            confidence = confidence,
            boundingBox = box,
            timestampMs = timestampMs
        )
    }

    // A. Basic classification: small box -> FAR, medium box -> MEDIUM, large box -> NEAR
    @Test
    fun testA_basicClassification() {
        val thresholds = config.defaultProximityThresholds

        val farDetection = createDetection("chair", height = 0.15f)
        val medDetection = createDetection("chair", height = 0.40f)
        val nearDetection = createDetection("chair", height = 0.60f)

        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimate(farDetection, thresholds))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimate(medDetection, thresholds))
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimate(nearDetection, thresholds))

        // Degenerate/invalid dimensions safely fallback to FAR
        val zeroWidth = createDetection("chair", width = 0.0f, height = 0.8f)
        val zeroHeight = createDetection("chair", width = 0.5f, height = 0.0f)
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimate(zeroWidth, thresholds))
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimate(zeroHeight, thresholds))
    }

    // B. Class-specific thresholds: same height produces different proximity for different object tiers
    @Test
    fun testB_classSpecificThresholds() {
        val testHeight = 0.50f

        // Tall tier: person (near: 0.60, med: 0.30) -> testHeight 0.50 is MEDIUM
        val personThresholds = config.getProximityThresholds("person")
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(testHeight, personThresholds))

        // Small tier: cup (near: 0.38, med: 0.15) -> testHeight 0.50 is NEAR
        val cupThresholds = config.getProximityThresholds("cup")
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(testHeight, cupThresholds))

        // Medium tier: chair (near: 0.55, med: 0.25) -> testHeight 0.50 is MEDIUM
        val chairThresholds = config.getProximityThresholds("chair")
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(testHeight, chairThresholds))
    }

    // C. EMA smoothing: alternating jittery values do not cause state chattering
    @Test
    fun testC_emaSmoothing() {
        val thresholds = ProximityThresholds(nearHeight = 0.50f, mediumHeight = 0.20f)
        val alpha = 0.35f

        // Start smoothed height comfortably in MEDIUM at 0.35
        var smoothed = 0.35f
        var currentProximity = ProximityLevel.MEDIUM

        // Jitter spikes momentarily towards 0.52 (NEAR boundary) then drops back to 0.34
        val jitterInputs = listOf(0.52f, 0.34f, 0.53f, 0.35f, 0.51f)
        for (raw in jitterInputs) {
            smoothed = alpha * raw + (1f - alpha) * smoothed
            // Smoothed value remains well below 0.50 threshold due to EMA dampening
            assertTrue("Smoothed height must be dampened below near threshold", smoothed < thresholds.nearHeight)
            val resolved = ProximityEstimator.resolveWithHysteresis(smoothed, currentProximity, thresholds, 0.04f)
            assertEquals("Proximity remains stable at MEDIUM despite input flutter", ProximityLevel.MEDIUM, resolved)
        }
    }

    // D. Hysteresis: proximity does not immediately downgrade at the boundary
    @Test
    fun testD_hysteresisBoundaryResilience() {
        val thresholds = ProximityThresholds(nearHeight = 0.60f, mediumHeight = 0.25f)
        val hysteresis = 0.04f

        // Currently NEAR
        val activeProximity = ProximityLevel.NEAR

        // Height drops slightly below nearHeight (0.59 < 0.60), but stays above nearHeight - hysteresis (0.56)
        val resolvedSlightDrop = ProximityEstimator.resolveWithHysteresis(
            smoothedHeight = 0.58f,
            currentProximity = activeProximity,
            thresholds = thresholds,
            hysteresis = hysteresis
        )
        assertEquals("Hysteresis must hold NEAR when within margin", ProximityLevel.NEAR, resolvedSlightDrop)

        // Height drops further below hysteresis band (0.54 < 0.56) -> transitions to MEDIUM
        val resolvedLargeDrop = ProximityEstimator.resolveWithHysteresis(
            smoothedHeight = 0.54f,
            currentProximity = activeProximity,
            thresholds = thresholds,
            hysteresis = hysteresis
        )
        assertEquals("Drops to MEDIUM once outside hysteresis margin", ProximityLevel.MEDIUM, resolvedLargeDrop)
    }

    // E. Two-frame transition confirmation: single frame spike does not switch proximity
    @Test
    fun testE_twoFrameTransitionConfirmation() {
        // Frame 1 & 2: Chair at MEDIUM (height = 0.45f) stabilizes
        engine.process(listOf(createDetection("chair", height = 0.45f)), timestampMs = 1000L)
        val res2 = engine.process(listOf(createDetection("chair", height = 0.45f)), timestampMs = 1200L)
        assertEquals(1, res2.selectedAlerts.size)
        assertEquals(ProximityLevel.MEDIUM, res2.selectedAlerts.first().proximity)
        assertEquals("chair, medium, ahead", res2.selectedAlerts.first().message)

        // Frame 3: Single frame height spike into NEAR (height = 0.85f)
        // With alpha = 0.35: smoothed = 0.35 * 0.85 + 0.65 * 0.45 = 0.59f >= nearThreshold (0.55f)
        // Must NOT immediately alert as NEAR because transition requires 2 consecutive observations
        val res3 = engine.process(listOf(createDetection("chair", height = 0.85f)), timestampMs = 1400L)
        // If selected in cooldown check, its proximity must still be MEDIUM
        if (res3.selectedAlerts.isNotEmpty()) {
            assertEquals("First frame of height increase must remain at previous proximity", ProximityLevel.MEDIUM, res3.selectedAlerts.first().proximity)
        }
        assertNull("First spike frame must not produce a NEAR speech event", res3.newAlertEvent)

        // Frame 4: Second consecutive frame at NEAR -> confirmed transition to NEAR
        val res4 = engine.process(listOf(createDetection("chair", height = 0.85f)), timestampMs = 1600L)
        assertEquals(1, res4.selectedAlerts.size)
        assertEquals(ProximityLevel.NEAR, res4.selectedAlerts.first().proximity)
        assertEquals("chair, close, ahead", res4.selectedAlerts.first().message)
        assertNotNull("Confirmed transition produces NEAR speech event", res4.newAlertEvent)
    }

    // F. MEDIUM -> NEAR escalation triggers a new urgent speech event
    @Test
    fun testF_mediumToNearEscalationTriggersAlert() {
        // Frame 1 & 2: Object stably observed at MEDIUM in CENTER
        engine.process(listOf(createDetection("chair", height = 0.45f, centerX = 0.50f)), timestampMs = 1000L)
        val resMedium = engine.process(listOf(createDetection("chair", height = 0.45f, centerX = 0.50f)), timestampMs = 1200L)

        assertEquals("MEDIUM alert emitted on initial stabilization", 1, resMedium.selectedAlerts.size)
        assertEquals("chair, medium, ahead", resMedium.selectedAlerts.first().message)
        assertEquals(ProximityLevel.MEDIUM, resMedium.selectedAlerts.first().proximity)
        assertNotNull("newAlertEvent is created for first speech", resMedium.newAlertEvent)
        assertEquals("chair, medium, ahead", resMedium.newAlertEvent?.message)

        // Frames 3 & 4: User approaches object; height increases into NEAR (0.85f)
        // Frame 3: First observation above NEAR threshold (candidate count = 1)
        engine.process(listOf(createDetection("chair", height = 0.85f, centerX = 0.50f)), timestampMs = 1400L)
        // Frame 4: Second observation above NEAR threshold (confirmed transition)
        val resEscalated = engine.process(listOf(createDetection("chair", height = 0.85f, centerX = 0.50f)), timestampMs = 1600L)

        assertEquals("NEAR alert selected", 1, resEscalated.selectedAlerts.size)
        assertEquals("chair, close, ahead", resEscalated.selectedAlerts.first().message)
        assertEquals(ProximityLevel.NEAR, resEscalated.selectedAlerts.first().proximity)

        // Escalation to NEAR in the same sector MUST create a new alert event despite same position
        assertNotNull("Escalation to NEAR creates new speech event", resEscalated.newAlertEvent)
        assertEquals("chair, close, ahead", resEscalated.newAlertEvent?.message)
    }

    // G. NEAR -> MEDIUM de-escalation remains silent (does not repeat speech)
    @Test
    fun testG_nearToMediumDeescalationRemainsSilent() {
        // Frames 1 & 2: Object starts at NEAR
        engine.process(listOf(createDetection("chair", height = 0.80f, centerX = 0.50f)), timestampMs = 1000L)
        val resNear = engine.process(listOf(createDetection("chair", height = 0.80f, centerX = 0.50f)), timestampMs = 1200L)
        assertEquals("chair, close, ahead", resNear.newAlertEvent?.message)

        // User backs away: height drops to MEDIUM (0.35f) across consecutive frames
        engine.process(listOf(createDetection("chair", height = 0.35f, centerX = 0.50f)), timestampMs = 1400L)
        engine.process(listOf(createDetection("chair", height = 0.35f, centerX = 0.50f)), timestampMs = 1600L)
        val resDeescalated = engine.process(listOf(createDetection("chair", height = 0.35f, centerX = 0.50f)), timestampMs = 1800L)

        // De-escalation must NOT generate a new speech event
        assertNull("De-escalation from NEAR to MEDIUM must remain silent", resDeescalated.newAlertEvent)
        assertTrue("No new alert events created on retreat", resDeescalated.newAlertEvents.isEmpty())
    }

    // H. Stable NEAR does not repeatedly alert or speak
    @Test
    fun testH_stableNearDoesNotRepeatedlyAlert() {
        // Frames 1 & 2: Stabilize in NEAR
        engine.process(listOf(createDetection("person", height = 0.80f, centerX = 0.50f)), timestampMs = 1000L)
        val resInitial = engine.process(listOf(createDetection("person", height = 0.80f, centerX = 0.50f)), timestampMs = 1200L)
        assertEquals("person, close, ahead", resInitial.newAlertEvent?.message)

        // Subsequent frames while holding position at NEAR (t = 1400ms, 1600ms, 1800ms)
        for (t in listOf(1400L, 1600L, 1800L, 2000L)) {
            val res = engine.process(listOf(createDetection("person", height = 0.80f, centerX = 0.50f)), timestampMs = t)
            assertNull("Stable NEAR must not repeat speech event at t = $t", res.newAlertEvent)
            assertTrue("newAlertEvents must remain empty during steady observation", res.newAlertEvents.isEmpty())
        }

        // Even after cooldown window expires (t = 4000ms), speech must NOT repeat without position change or re-escalation
        val resAfterCooldown = engine.process(listOf(createDetection("person", height = 0.80f, centerX = 0.50f)), timestampMs = 4000L)
        assertNull("Steady NEAR object does not re-alert speech even after cooldown", resAfterCooldown.newAlertEvent)
    }

    // I. Object disappearance clears proximity history
    @Test
    fun testI_objectDisappearanceClearsHistory() {
        // Object observed at NEAR at t = 1000L and 1200L
        engine.process(listOf(createDetection("chair", height = 0.80f, centerX = 0.20f)), timestampMs = 1000L)
        engine.process(listOf(createDetection("chair", height = 0.80f, centerX = 0.20f)), timestampMs = 1200L)

        // Disappears for 2000ms (> objectDisappearanceTimeoutMs 1000ms)
        engine.process(emptyList(), timestampMs = 3500L)

        // Reappears at t = 4000L: must require stabilization (2 observations) as fresh track
        val res1 = engine.process(listOf(createDetection("chair", height = 0.80f, centerX = 0.20f)), timestampMs = 4000L)
        assertTrue("Reappeared object must re-stabilize before alerting", res1.selectedAlerts.isEmpty())

        val res2 = engine.process(listOf(createDetection("chair", height = 0.80f, centerX = 0.20f)), timestampMs = 4200L)
        assertEquals("Reappeared object alerts upon re-stabilization", 1, res2.selectedAlerts.size)
        assertEquals("chair, close, on your left", res2.selectedAlerts.first().message)
        assertEquals(ProximityLevel.NEAR, res2.selectedAlerts.first().proximity)
    }

    // J. Alert priority and capacity cap remain strictly preserved
    @Test
    fun testJ_priorityAndCapacityPreservedWithProximity() {
        // 3 objects: cup (low priority, NEAR), person (urgent priority, MEDIUM), chair (medium priority, FAR)
        val cupNear = createDetection("cup", height = 0.50f, centerX = 0.85f)
        val personMed = createDetection("person", height = 0.50f, centerX = 0.50f)
        val chairFar = createDetection("chair", height = 0.15f, centerX = 0.15f)

        engine.process(listOf(cupNear, personMed, chairFar), timestampMs = 1000L)
        val result = engine.process(listOf(cupNear, personMed, chairFar), timestampMs = 1200L)

        // Capped at maxSelectedAlerts = 2
        assertEquals(2, result.selectedAlerts.size)

        // Highest priority classes (person then chair) selected ahead of low priority cup,
        // proving proximity does NOT compromise architectural class priority ranking
        assertEquals("person", result.selectedAlerts[0].className)
        assertEquals(ProximityLevel.MEDIUM, result.selectedAlerts[0].proximity)

        assertEquals("chair", result.selectedAlerts[1].className)
        assertEquals(ProximityLevel.FAR, result.selectedAlerts[1].proximity)

        assertEquals("1 detection suppressed by capacity cap", 1, result.suppressedCount)
    }

    /**
     * Fake SpeechController for testing speech arbitrator integration with proximity.
     */
    private class FakeSpeechController : SpeechController {
        val spoken = mutableListOf<String>()
        override fun speak(utterance: String, priority: SpeechPriority) {
            spoken.add(utterance)
        }
        override fun stop() {}
    }

    @Test
    fun testK_speechArbitratorNearEscalationAcceptance() {
        val fakeSpeech = FakeSpeechController()
        val arbitrator = DefaultSpeechEventArbitrator(fakeSpeech)

        val chairMed = AlertCandidate(
            className = "chair",
            confidence = 0.85f,
            position = HorizontalPosition.CENTER,
            priority = AlertPriority.MEDIUM,
            message = "chair, medium, ahead",
            proximity = ProximityLevel.MEDIUM
        )
        val chairNear = AlertCandidate(
            className = "chair",
            confidence = 0.85f,
            position = HorizontalPosition.CENTER,
            priority = AlertPriority.MEDIUM,
            message = "chair, close, ahead",
            proximity = ProximityLevel.NEAR
        )

        // 1. Submit MEDIUM -> spoken
        assertTrue("MEDIUM alert accepted", arbitrator.submit(chairMed))
        assertEquals(1, fakeSpeech.spoken.size)
        assertEquals("chair, medium, ahead", fakeSpeech.spoken.first())
        arbitrator.onUtteranceCompleted("test1")

        // 2. Submit duplicate MEDIUM -> rejected
        assertFalse("Duplicate MEDIUM alert rejected", arbitrator.submit(chairMed))

        // 3. Escalated NEAR alert submitted -> accepted and spoken!
        assertTrue("Escalated NEAR alert accepted", arbitrator.submit(chairNear))
        assertEquals(2, fakeSpeech.spoken.size)
        assertEquals("chair, close, ahead", fakeSpeech.spoken[1])
        arbitrator.onUtteranceCompleted("test2")

        // 4. Duplicate NEAR alert submitted -> rejected
        assertFalse("Duplicate NEAR alert rejected", arbitrator.submit(chairNear))
    }

    // L. Explicit spoken patterns for all proximity levels and positions
    @Test
    fun testL_explicitSpokenPatterns_allProximityLevelsAndPositions() {
        val objects = listOf("person", "chair", "table", "door")

        for (obj in objects) {
            // NEAR: "<object>, close, <position>"
            assertEquals("$obj, close, ahead", AlertMessageFormatter.format(obj, HorizontalPosition.CENTER, ProximityLevel.NEAR))
            assertEquals("$obj, close, on your left", AlertMessageFormatter.format(obj, HorizontalPosition.LEFT, ProximityLevel.NEAR))
            assertEquals("$obj, close, on your right", AlertMessageFormatter.format(obj, HorizontalPosition.RIGHT, ProximityLevel.NEAR))

            // MEDIUM: "<object>, medium, <position>"
            assertEquals("$obj, medium, ahead", AlertMessageFormatter.format(obj, HorizontalPosition.CENTER, ProximityLevel.MEDIUM))
            assertEquals("$obj, medium, on your left", AlertMessageFormatter.format(obj, HorizontalPosition.LEFT, ProximityLevel.MEDIUM))
            assertEquals("$obj, medium, on your right", AlertMessageFormatter.format(obj, HorizontalPosition.RIGHT, ProximityLevel.MEDIUM))

            // FAR: "<object>, far, <position>"
            assertEquals("$obj, far, ahead", AlertMessageFormatter.format(obj, HorizontalPosition.CENTER, ProximityLevel.FAR))
            assertEquals("$obj, far, on your left", AlertMessageFormatter.format(obj, HorizontalPosition.LEFT, ProximityLevel.FAR))
            assertEquals("$obj, far, on your right", AlertMessageFormatter.format(obj, HorizontalPosition.RIGHT, ProximityLevel.FAR))

            // Capitalized variants for UI display banners
            val capitalized = obj.replaceFirstChar { it.titlecase() }
            assertEquals("$capitalized, close, ahead", AlertMessageFormatter.formatCapitalized(obj, HorizontalPosition.CENTER, ProximityLevel.NEAR))
            assertEquals("$capitalized, medium, ahead", AlertMessageFormatter.formatCapitalized(obj, HorizontalPosition.CENTER, ProximityLevel.MEDIUM))
            assertEquals("$capitalized, far, ahead", AlertMessageFormatter.formatCapitalized(obj, HorizontalPosition.CENTER, ProximityLevel.FAR))
        }

        // Verify exact spokenText values on the ProximityLevel enum
        assertEquals("close", ProximityLevel.NEAR.spokenText)
        assertEquals("medium", ProximityLevel.MEDIUM.spokenText)
        assertEquals("far", ProximityLevel.FAR.spokenText)
    }

    // M. FAR -> MEDIUM escalation produces a new updated alert event
    @Test
    fun testM_farToMediumEscalationTriggersAlert() {
        // Frame 1 & 2: Object starts at FAR in CENTER (height = 0.15f < 0.25f mediumThreshold)
        engine.process(listOf(createDetection("chair", height = 0.15f, centerX = 0.50f)), timestampMs = 1000L)
        val resFar = engine.process(listOf(createDetection("chair", height = 0.15f, centerX = 0.50f)), timestampMs = 1200L)

        assertEquals("FAR alert emitted on stabilization", 1, resFar.selectedAlerts.size)
        assertEquals(ProximityLevel.FAR, resFar.selectedAlerts.first().proximity)
        assertEquals("chair, far, ahead", resFar.selectedAlerts.first().message)
        assertNotNull("Speech event emitted for initial FAR observation", resFar.newAlertEvent)
        assertEquals("chair, far, ahead", resFar.newAlertEvent?.message)

        // Frames 3 & 4: User approaches object; height increases into MEDIUM (height = 0.50f >= 0.25f mediumThreshold)
        engine.process(listOf(createDetection("chair", height = 0.50f, centerX = 0.50f)), timestampMs = 1400L)
        val resMedium = engine.process(listOf(createDetection("chair", height = 0.50f, centerX = 0.50f)), timestampMs = 1600L)

        assertEquals("MEDIUM alert selected", 1, resMedium.selectedAlerts.size)
        assertEquals(ProximityLevel.MEDIUM, resMedium.selectedAlerts.first().proximity)
        assertEquals("chair, medium, ahead", resMedium.selectedAlerts.first().message)

        // Escalation from FAR to MEDIUM MUST generate a new speech event
        assertNotNull("FAR -> MEDIUM escalation generates speech event", resMedium.newAlertEvent)
        assertEquals("chair, medium, ahead", resMedium.newAlertEvent?.message)

        // Further frames holding at MEDIUM must remain silent
        val resHold = engine.process(listOf(createDetection("chair", height = 0.50f, centerX = 0.50f)), timestampMs = 1800L)
        assertNull("Holding at MEDIUM does not repeat speech", resHold.newAlertEvent)
    }

    // N. MEDIUM -> FAR de-escalation remains silent
    @Test
    fun testN_mediumToFarDeescalationRemainsSilent() {
        // Stabilize at MEDIUM
        engine.process(listOf(createDetection("chair", height = 0.50f, centerX = 0.50f)), timestampMs = 1000L)
        val resMed = engine.process(listOf(createDetection("chair", height = 0.50f, centerX = 0.50f)), timestampMs = 1200L)
        assertEquals("chair, medium, ahead", resMed.newAlertEvent?.message)

        // User steps back; height drops into FAR (0.10f) across frames
        engine.process(listOf(createDetection("chair", height = 0.10f, centerX = 0.50f)), timestampMs = 1400L)
        engine.process(listOf(createDetection("chair", height = 0.10f, centerX = 0.50f)), timestampMs = 1600L)
        engine.process(listOf(createDetection("chair", height = 0.10f, centerX = 0.50f)), timestampMs = 1800L)
        val resDeescalated = engine.process(listOf(createDetection("chair", height = 0.10f, centerX = 0.50f)), timestampMs = 2000L)

        assertEquals(1, resDeescalated.selectedAlerts.size)
        assertEquals(ProximityLevel.FAR, resDeescalated.selectedAlerts.first().proximity)
        assertEquals("chair, far, ahead", resDeescalated.selectedAlerts.first().message)
        assertNull("MEDIUM -> FAR de-escalation must remain silent", resDeescalated.newAlertEvent)
    }

    // =========================================================================
    // PHYSICAL CALIBRATION REFINEMENT VERIFICATION SUITE (Items 1 - 11)
    // =========================================================================

    // Requirement 1: A genuinely large/close bounding box reaches NEAR
    @Test
    fun test01_genuinelyLargeOrCloseBoxReachesNear() {
        // Tall: nearHeight = 0.60f
        val personThresholds = config.getProximityThresholds("person")
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(0.60f, personThresholds))
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(0.80f, personThresholds))

        // Medium/Furniture/Vehicles: nearHeight = 0.55f (physical calibration: 0.58f previously misclassified as MEDIUM under 0.65 now reaches NEAR)
        val chairThresholds = config.getProximityThresholds("chair")
        val carThresholds = config.getProximityThresholds("car")
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(0.55f, chairThresholds))
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(0.58f, chairThresholds))
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(0.70f, carThresholds))

        // Small/Low: nearHeight = 0.38f
        val cupThresholds = config.getProximityThresholds("cup")
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(0.38f, cupThresholds))
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(0.45f, cupThresholds))

        // Fallback: nearHeight = 0.55f
        val fallbackThresholds = config.defaultProximityThresholds
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(0.55f, fallbackThresholds))
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.estimateFromHeight(0.65f, fallbackThresholds))
    }

    // Requirement 2: A medium-size box remains MEDIUM
    @Test
    fun test02_mediumSizeBoxRemainsMedium() {
        // Tall: [0.30f, 0.60f)
        val personThresholds = config.getProximityThresholds("person")
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.30f, personThresholds))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.45f, personThresholds))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.59f, personThresholds))

        // Medium: [0.25f, 0.55f)
        val chairThresholds = config.getProximityThresholds("chair")
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.25f, chairThresholds))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.40f, chairThresholds))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.54f, chairThresholds))

        // Small: [0.15f, 0.38f)
        val cupThresholds = config.getProximityThresholds("cup")
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.15f, cupThresholds))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.25f, cupThresholds))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.37f, cupThresholds))

        // Fallback: [0.25f, 0.55f)
        val fallbackThresholds = config.defaultProximityThresholds
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.25f, fallbackThresholds))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.40f, fallbackThresholds))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.estimateFromHeight(0.54f, fallbackThresholds))
    }

    // Requirement 3: A small/far box remains FAR
    @Test
    fun test03_smallFarBoxRemainsFar() {
        // Tall: < 0.30f
        val personThresholds = config.getProximityThresholds("person")
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimateFromHeight(0.29f, personThresholds))
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimateFromHeight(0.10f, personThresholds))

        // Medium: < 0.25f
        val chairThresholds = config.getProximityThresholds("chair")
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimateFromHeight(0.24f, chairThresholds))
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimateFromHeight(0.10f, chairThresholds))

        // Small: < 0.15f
        val cupThresholds = config.getProximityThresholds("cup")
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimateFromHeight(0.14f, cupThresholds))
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimateFromHeight(0.05f, cupThresholds))

        // Fallback: < 0.25f
        val fallbackThresholds = config.defaultProximityThresholds
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimateFromHeight(0.24f, fallbackThresholds))
        assertEquals(ProximityLevel.FAR, ProximityEstimator.estimateFromHeight(0.08f, fallbackThresholds))
    }

    // Requirement 4: FAR -> MEDIUM -> NEAR while approaching
    @Test
    fun test04_approaching_farToMediumToNear_progression() {
        val testEngine = DefaultDecisionEngine(config = config)

        // Step 1: Object stably detected at FAR (height = 0.15f < 0.25f)
        testEngine.process(listOf(createDetection("chair", height = 0.15f, centerX = 0.50f)), timestampMs = 1000L)
        val resFar = testEngine.process(listOf(createDetection("chair", height = 0.15f, centerX = 0.50f)), timestampMs = 1200L)
        assertEquals(ProximityLevel.FAR, resFar.selectedAlerts.first().proximity)
        assertEquals("chair, far, ahead", resFar.selectedAlerts.first().message)
        assertEquals("chair, far, ahead", resFar.newAlertEvent?.message)

        // Step 2: Approaching -> height increases to 0.50f (medium range [0.25f, 0.55f))
        // Frame 1: Candidate count = 1, stays at FAR (suppressed by duplicate cooldown)
        val resApproach1 = testEngine.process(listOf(createDetection("chair", height = 0.50f, centerX = 0.50f)), timestampMs = 1400L)
        assertNull(resApproach1.newAlertEvent)
        // Frame 2: Candidate count = 2, confirmed transition to MEDIUM!
        val resApproach2 = testEngine.process(listOf(createDetection("chair", height = 0.50f, centerX = 0.50f)), timestampMs = 1600L)
        assertEquals(ProximityLevel.MEDIUM, resApproach2.selectedAlerts.first().proximity)
        assertEquals("chair, medium, ahead", resApproach2.selectedAlerts.first().message)
        assertEquals("chair, medium, ahead", resApproach2.newAlertEvent?.message)

        // Step 3: Approaching closer -> height increases to 0.85f (near range >= 0.55f)
        // Frame 1: smoothed height adapts (0.35 * 0.85 + 0.65 * 0.352 = 0.5263f)
        testEngine.process(listOf(createDetection("chair", height = 0.85f, centerX = 0.50f)), timestampMs = 1800L)
        // Frame 2: smoothed height crosses 0.55f -> candidate count = 1
        testEngine.process(listOf(createDetection("chair", height = 0.85f, centerX = 0.50f)), timestampMs = 2000L)
        // Frame 3: confirmed transition to NEAR!
        val resClose = testEngine.process(listOf(createDetection("chair", height = 0.85f, centerX = 0.50f)), timestampMs = 2200L)
        assertEquals(ProximityLevel.NEAR, resClose.selectedAlerts.first().proximity)
        assertEquals("chair, close, ahead", resClose.selectedAlerts.first().message)
        assertEquals("chair, close, ahead", resClose.newAlertEvent?.message)
    }

    // Requirement 5: NEAR -> MEDIUM -> FAR while moving away
    @Test
    fun test05_movingAway_nearToMediumToFar_progression() {
        val testEngine = DefaultDecisionEngine(config = config)

        // Step 1: Object stably detected at NEAR (height = 0.85f >= 0.55f)
        testEngine.process(listOf(createDetection("chair", height = 0.85f, centerX = 0.50f)), timestampMs = 1000L)
        val resNear = testEngine.process(listOf(createDetection("chair", height = 0.85f, centerX = 0.50f)), timestampMs = 1200L)
        assertEquals(ProximityLevel.NEAR, resNear.selectedAlerts.first().proximity)
        assertEquals("chair, close, ahead", resNear.newAlertEvent?.message)

        // Step 2: Retreating -> height drops to 0.35f (drops below 0.55 - 0.04 = 0.51f into MEDIUM)
        // Feed frames across EMA smoothing and 2-frame confirmation
        testEngine.process(listOf(createDetection("chair", height = 0.35f, centerX = 0.50f)), timestampMs = 1400L)
        testEngine.process(listOf(createDetection("chair", height = 0.35f, centerX = 0.50f)), timestampMs = 1600L)
        testEngine.process(listOf(createDetection("chair", height = 0.35f, centerX = 0.50f)), timestampMs = 1800L)
        val resMed = testEngine.process(listOf(createDetection("chair", height = 0.35f, centerX = 0.50f)), timestampMs = 2000L)
        assertEquals(ProximityLevel.MEDIUM, resMed.selectedAlerts.first().proximity)
        assertEquals("chair, medium, ahead", resMed.selectedAlerts.first().message)
        // De-escalation must NOT speak!
        assertNull("NEAR -> MEDIUM retreat must remain silent", resMed.newAlertEvent)

        // Step 3: Retreating further -> height drops to 0.05f (drops below 0.25 - 0.04 = 0.21f into FAR)
        testEngine.process(listOf(createDetection("chair", height = 0.05f, centerX = 0.50f)), timestampMs = 2200L)
        testEngine.process(listOf(createDetection("chair", height = 0.05f, centerX = 0.50f)), timestampMs = 2400L)
        testEngine.process(listOf(createDetection("chair", height = 0.05f, centerX = 0.50f)), timestampMs = 2600L)
        val resFar = testEngine.process(listOf(createDetection("chair", height = 0.05f, centerX = 0.50f)), timestampMs = 2800L)
        assertEquals(ProximityLevel.FAR, resFar.selectedAlerts.first().proximity)
        assertEquals("chair, far, ahead", resFar.selectedAlerts.first().message)
        // De-escalation must NOT speak!
        assertNull("MEDIUM -> FAR retreat must remain silent", resFar.newAlertEvent)
    }

    // Requirement 6: Hysteresis prevents boundary oscillation
    @Test
    fun test06_hysteresisPreventsBoundaryOscillation_bothBoundaries() {
        val thresholds = config.getProximityThresholds("chair") // near: 0.55, medium: 0.25
        val hysteresis = config.proximityHysteresisMargin // 0.04

        // 1. NEAR boundary (0.55f, lower margin 0.51f):
        // Approaching from MEDIUM: must reach 0.55f to become NEAR
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.resolveWithHysteresis(0.54f, ProximityLevel.MEDIUM, thresholds, hysteresis))
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.resolveWithHysteresis(0.55f, ProximityLevel.MEDIUM, thresholds, hysteresis))

        // Once NEAR, oscillating inside hysteresis band (0.52f to 0.54f) must NOT flip back to MEDIUM
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.resolveWithHysteresis(0.54f, ProximityLevel.NEAR, thresholds, hysteresis))
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.resolveWithHysteresis(0.52f, ProximityLevel.NEAR, thresholds, hysteresis))
        assertEquals(ProximityLevel.NEAR, ProximityEstimator.resolveWithHysteresis(0.51f, ProximityLevel.NEAR, thresholds, hysteresis))
        // Drops only when below 0.51f
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.resolveWithHysteresis(0.50f, ProximityLevel.NEAR, thresholds, hysteresis))

        // 2. MEDIUM boundary (0.25f, lower margin ~0.21f):
        // Approaching from FAR: must reach 0.25f to become MEDIUM
        assertEquals(ProximityLevel.FAR, ProximityEstimator.resolveWithHysteresis(0.24f, ProximityLevel.FAR, thresholds, hysteresis))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.resolveWithHysteresis(0.25f, ProximityLevel.FAR, thresholds, hysteresis))

        // Once MEDIUM, oscillating inside hysteresis band (0.22f to 0.24f) must NOT flip back to FAR
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.resolveWithHysteresis(0.24f, ProximityLevel.MEDIUM, thresholds, hysteresis))
        assertEquals(ProximityLevel.MEDIUM, ProximityEstimator.resolveWithHysteresis(0.22f, ProximityLevel.MEDIUM, thresholds, hysteresis))
        // Drops only when strictly below (mediumHeight - hysteresis) which is ~0.21f
        assertEquals(ProximityLevel.FAR, ProximityEstimator.resolveWithHysteresis(0.19f, ProximityLevel.MEDIUM, thresholds, hysteresis))
    }

    // Requirement 7: Two-frame confirmation remains active
    @Test
    fun test07_twoFrameConfirmationRejectsSingleFrameFlutter() {
        val testEngine = DefaultDecisionEngine(config = config)

        // Stabilize at MEDIUM
        testEngine.process(listOf(createDetection("chair", height = 0.40f)), timestampMs = 1000L)
        val resStable = testEngine.process(listOf(createDetection("chair", height = 0.40f)), timestampMs = 1200L)
        assertEquals(ProximityLevel.MEDIUM, resStable.selectedAlerts.first().proximity)
        assertEquals("chair, medium, ahead", resStable.newAlertEvent?.message)

        // Single-frame spike into NEAR
        val resSpike = testEngine.process(listOf(createDetection("chair", height = 0.85f)), timestampMs = 1400L)
        // Single frame spike must not produce a speech event
        assertNull("Single frame spike must not trigger NEAR speech", resSpike.newAlertEvent)

        // Return to normal MEDIUM height in next frame
        val resReturn = testEngine.process(listOf(createDetection("chair", height = 0.40f)), timestampMs = 1600L)
        assertNull(resReturn.newAlertEvent)

        // Single-frame drop into FAR
        val resDrop = testEngine.process(listOf(createDetection("chair", height = 0.10f)), timestampMs = 1800L)
        assertNull("Single frame drop must not trigger speech", resDrop.newAlertEvent)

        // Return to normal MEDIUM height
        testEngine.process(listOf(createDetection("chair", height = 0.40f)), timestampMs = 2000L)

        // Continuous tracking to cooldown expiry (3800L - 1200L = 2600ms > 2500ms cooldown)
        testEngine.process(listOf(createDetection("chair", height = 0.40f)), timestampMs = 2800L)
        testEngine.process(listOf(createDetection("chair", height = 0.40f)), timestampMs = 3600L)
        val resSteady = testEngine.process(listOf(createDetection("chair", height = 0.40f)), timestampMs = 3800L)
        assertEquals(1, resSteady.selectedAlerts.size)
        assertEquals("Object stably held MEDIUM throughout flutters", ProximityLevel.MEDIUM, resSteady.selectedAlerts.first().proximity)
        assertNull("Steady state must not re-alert speech", resSteady.newAlertEvent)
    }

    // Requirements 8, 9, 10: Spoken wording verification
    @Test
    fun test08_09_10_speechWordMappings() {
        // Requirement 8: NEAR speech = "close"
        assertEquals("close", ProximityLevel.NEAR.spokenText)
        assertEquals("chair, close, ahead", AlertMessageFormatter.format("chair", HorizontalPosition.CENTER, ProximityLevel.NEAR))
        assertEquals("chair, close, on your left", AlertMessageFormatter.format("chair", HorizontalPosition.LEFT, ProximityLevel.NEAR))
        assertEquals("chair, close, on your right", AlertMessageFormatter.format("chair", HorizontalPosition.RIGHT, ProximityLevel.NEAR))

        // Requirement 9: MEDIUM speech = "medium"
        assertEquals("medium", ProximityLevel.MEDIUM.spokenText)
        assertEquals("chair, medium, ahead", AlertMessageFormatter.format("chair", HorizontalPosition.CENTER, ProximityLevel.MEDIUM))
        assertEquals("chair, medium, on your left", AlertMessageFormatter.format("chair", HorizontalPosition.LEFT, ProximityLevel.MEDIUM))
        assertEquals("chair, medium, on your right", AlertMessageFormatter.format("chair", HorizontalPosition.RIGHT, ProximityLevel.MEDIUM))

        // Requirement 10: FAR speech = "far"
        assertEquals("far", ProximityLevel.FAR.spokenText)
        assertEquals("chair, far, ahead", AlertMessageFormatter.format("chair", HorizontalPosition.CENTER, ProximityLevel.FAR))
        assertEquals("chair, far, on your left", AlertMessageFormatter.format("chair", HorizontalPosition.LEFT, ProximityLevel.FAR))
        assertEquals("chair, far, on your right", AlertMessageFormatter.format("chair", HorizontalPosition.RIGHT, ProximityLevel.FAR))
    }

    // Requirement 11: Alert priority and max capacity remain unchanged
    @Test
    fun test11_navigatePriorityAndCapacityPreserved() {
        val testEngine = DefaultDecisionEngine(config = config)

        // 4 simultaneous detections:
        // person (urgent rank 180, NEAR)
        // car (high rank 150, MEDIUM)
        // table (medium rank 100, FAR)
        // cup (low rank 10, NEAR)
        val person = createDetection("person", height = 0.65f, centerX = 0.50f)
        val car = createDetection("car", height = 0.40f, centerX = 0.20f)
        val table = createDetection("table", height = 0.15f, centerX = 0.80f)
        val cup = createDetection("cup", height = 0.45f, centerX = 0.50f)

        testEngine.process(listOf(person, car, table, cup), timestampMs = 1000L)
        val result = testEngine.process(listOf(person, car, table, cup), timestampMs = 1200L)

        // Capacity capped strictly at maxSelectedAlerts = 2
        assertEquals(2, result.selectedAlerts.size)
        assertEquals(2, result.suppressedCount)

        // Priority ordering is preserved: person first, then car
        assertEquals("person", result.selectedAlerts[0].className)
        assertEquals(ProximityLevel.NEAR, result.selectedAlerts[0].proximity)

        assertEquals("car", result.selectedAlerts[1].className)
        assertEquals(ProximityLevel.MEDIUM, result.selectedAlerts[1].proximity)
    }
}
