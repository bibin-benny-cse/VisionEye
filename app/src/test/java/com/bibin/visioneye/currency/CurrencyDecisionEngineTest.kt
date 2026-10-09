package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.HorizontalPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CurrencyDecisionEngine] verifying:
 * - 2 qualifying frames temporal confirmation
 * - Single-frame noise rejection
 * - Continuous visibility speech suppression (no repetitive chatter)
 * - 1500ms absence timeout session reset
 * - Multi-note speech formatting
 * - Silent idle frames
 */
class CurrencyDecisionEngineTest {

    private lateinit var engine: DefaultCurrencyDecisionEngine

    @Before
    fun setup() {
        engine = DefaultCurrencyDecisionEngine(
            temporalConfirmationCount = 2,
            absenceTimeoutMs = 1500L
        )
    }

    private fun createDetection(
        denomination: CurrencyDenomination,
        confidence: Float = 0.90f
    ): CurrencyDetection {
        return CurrencyDetection(
            denomination = denomination,
            confidence = confidence,
            boundingBox = BoundingBox(0.2f, 0.2f, 0.8f, 0.8f),
            position = HorizontalPosition.CENTER
        )
    }

    @Test
    fun confirmation_requiresTwoConsecutiveFrames() {
        val note = listOf(createDetection(CurrencyDenomination.TEN))

        // Frame 1: First detection - not confirmed yet, silent
        val result1 = engine.process(note, timestampMs = 1000L)
        assertFalse(result1.isConfirmed)
        assertNull(result1.spokenAlert)
        assertTrue(result1.statusMessage.contains("1/2"))

        // Frame 2: Second consecutive detection - confirmed and announced!
        val result2 = engine.process(note, timestampMs = 1100L)
        assertTrue(result2.isConfirmed)
        assertEquals(listOf(CurrencyDenomination.TEN), result2.confirmedDenominations)
        assertEquals("10 rupee note", result2.spokenAlert)
        assertTrue(result2.statusMessage.contains("CONFIRMED"))
    }

    @Test
    fun singleNoiseFrame_doesNotTriggerAnnouncement() {
        val note = listOf(createDetection(CurrencyDenomination.FIVE_HUNDRED))

        // Frame 1: Noise flash
        val result1 = engine.process(note, timestampMs = 1000L)
        assertFalse(result1.isConfirmed)
        assertNull(result1.spokenAlert)

        // Frame 2: Disappears immediately
        val result2 = engine.process(emptyList(), timestampMs = 1100L)
        assertFalse(result2.isConfirmed)
        assertNull(result2.spokenAlert)
    }

    @Test
    fun continuousVisibility_suppressesRepeatedAnnouncements() {
        val note = listOf(createDetection(CurrencyDenomination.ONE_HUNDRED))

        // Frame 1
        engine.process(note, timestampMs = 1000L)
        // Frame 2: Confirmed and spoken
        val result2 = engine.process(note, timestampMs = 1100L)
        assertEquals("100 rupee note", result2.spokenAlert)

        // Frame 3: Note remains in camera view
        val result3 = engine.process(note, timestampMs = 1200L)
        assertTrue(result3.isConfirmed)
        assertNull(result3.spokenAlert) // Must remain silent while note is continuously held

        // Frame 4: Still in view
        val result4 = engine.process(note, timestampMs = 1300L)
        assertTrue(result4.isConfirmed)
        assertNull(result4.spokenAlert)
    }

    @Test
    fun absenceReset_after1500ms_allowsReannouncement() {
        val note = listOf(createDetection(CurrencyDenomination.FIFTY))

        // First session: frames at t=1000 and t=1100
        engine.process(note, timestampMs = 1000L)
        val result2 = engine.process(note, timestampMs = 1100L)
        assertEquals("50 rupee note", result2.spokenAlert)

        // Note is removed from view at t=1200
        engine.process(emptyList(), timestampMs = 1200L)

        // Frame at t=2000 (800ms absent, less than 1500ms timeout)
        val resultShortAbsent = engine.process(emptyList(), timestampMs = 2000L)
        // Session confirmation retained during short temporary occlusion
        assertTrue(resultShortAbsent.isConfirmed)

        // Frame at t=2800 (1600ms absent since t=1200, exceeding 1500ms timeout)
        val resultReset = engine.process(emptyList(), timestampMs = 2800L)
        assertFalse(resultReset.isConfirmed)
        assertNull(resultReset.spokenAlert)

        // Second session: note re-presented at t=3000
        val resultReFrame1 = engine.process(note, timestampMs = 3000L)
        assertFalse(resultReFrame1.isConfirmed)
        assertNull(resultReFrame1.spokenAlert)

        // Frame 2 at t=3100: re-confirmed and re-announced!
        val resultReFrame2 = engine.process(note, timestampMs = 3100L)
        assertTrue(resultReFrame2.isConfirmed)
        assertEquals("50 rupee note", resultReFrame2.spokenAlert)
    }

    @Test
    fun newBanknoteIntroduced_announcesUpdatedComposition() {
        val note100 = listOf(createDetection(CurrencyDenomination.ONE_HUNDRED))
        engine.process(note100, timestampMs = 1000L)
        val result1 = engine.process(note100, timestampMs = 1100L)
        assertEquals("100 rupee note", result1.spokenAlert)

        // User introduces a second note: both ₹100 and ₹500
        val notes = listOf(
            createDetection(CurrencyDenomination.ONE_HUNDRED),
            createDetection(CurrencyDenomination.FIVE_HUNDRED)
        )

        // Frame 1 with both notes
        val resultBoth1 = engine.process(notes, timestampMs = 1200L)
        assertNull(resultBoth1.spokenAlert)

        // Frame 2 with both notes
        val resultBoth2 = engine.process(notes, timestampMs = 1300L)
        assertTrue(resultBoth2.isConfirmed)
        // 500 comes before 100
        assertEquals("500 rupee note and 100 rupee note", resultBoth2.spokenAlert)
    }

    @Test
    fun idleFrames_remainCompletelySilent() {
        for (t in 1..10) {
            val result = engine.process(emptyList(), timestampMs = t * 200L)
            assertNull(result.spokenAlert)
            assertFalse(result.isConfirmed)
            assertEquals("SCANNING FOR BANKNOTE...", result.statusMessage)
        }
    }

    @Test
    fun manualReset_clearsAllStateImmediately() {
        val note = listOf(createDetection(CurrencyDenomination.TWO_HUNDRED))
        engine.process(note, timestampMs = 1000L)
        engine.process(note, timestampMs = 1100L)

        engine.reset()

        val afterReset = engine.process(emptyList(), timestampMs = 1200L)
        assertFalse(afterReset.isConfirmed)
        assertNull(afterReset.spokenAlert)
    }
}
