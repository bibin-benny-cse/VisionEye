package com.bibin.visioneye.fusion.decision

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
}
