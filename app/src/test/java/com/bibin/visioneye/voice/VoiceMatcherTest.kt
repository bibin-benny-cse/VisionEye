package com.bibin.visioneye.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [VoiceMatcher].
 *
 * Verifies:
 * - Exact visible label matching regardless of case and punctuation
 * - Canonical command matching
 * - Hypotheses fallback resolution
 * - Screen context filtering (ignoring controls from other screens)
 * - Availability guard filtering (disabled controls cannot be matched)
 * - Constructive failure messaging with screen-specific suggestions
 */
class VoiceMatcherTest {

    private val sampleActions = listOf(
        VoiceAction(
            id = "main_navigate",
            screenContext = VoiceScreenContext.MAIN_DASHBOARD,
            visibleLabel = "NAVIGATE",
            canonicalCommand = "Navigate",
            aliases = listOf("Go to navigate", "Start navigate"),
            onExecute = { VoiceActionResult.Success("Navigate mode") }
        ),
        VoiceAction(
            id = "main_read",
            screenContext = VoiceScreenContext.MAIN_DASHBOARD,
            visibleLabel = "READ TEXT",
            canonicalCommand = "Read text",
            aliases = listOf("Read", "OCR"),
            onExecute = { VoiceActionResult.Success("Read mode") }
        ),
        VoiceAction(
            id = "main_disabled",
            screenContext = VoiceScreenContext.MAIN_DASHBOARD,
            visibleLabel = "SPECIAL ACTION",
            canonicalCommand = "Special action",
            isAvailable = { false },
            onExecute = { VoiceActionResult.Success("Special") }
        ),
        VoiceAction(
            id = "sos_trigger",
            screenContext = VoiceScreenContext.SOS_EMERGENCY,
            visibleLabel = "TRIGGER SOS",
            canonicalCommand = "Trigger SOS",
            onExecute = { VoiceActionResult.Success("Trigger SOS") }
        )
    )

    @Test
    fun normalize_cleansPunctuationAndWhitespace() {
        assertEquals("read text", VoiceMatcher.normalize("  READ, TEXT!  "))
        assertEquals("trigger sos", VoiceMatcher.normalize("Trigger SOS..."))
        assertEquals("outdoor gps navigation", VoiceMatcher.normalize("Outdoor GPS Navigation"))
    }

    @Test
    fun match_matchesExactVisibleLabel() {
        val result = VoiceMatcher.match(
            hypotheses = listOf("READ TEXT"),
            candidateActions = sampleActions,
            currentContext = VoiceScreenContext.MAIN_DASHBOARD
        )
        assertTrue(result is MatchResult.Matched)
        val matched = result as MatchResult.Matched
        assertEquals("main_read", matched.action.id)
        assertEquals("READ TEXT", matched.action.visibleLabel)
    }

    @Test
    fun match_matchesCaseAndPunctuationInsensitive() {
        val result = VoiceMatcher.match(
            hypotheses = listOf("navigate!"),
            candidateActions = sampleActions,
            currentContext = VoiceScreenContext.MAIN_DASHBOARD
        )
        assertTrue(result is MatchResult.Matched)
        val matched = result as MatchResult.Matched
        assertEquals("main_navigate", matched.action.id)
    }

    @Test
    fun match_matchesAlias() {
        val result = VoiceMatcher.match(
            hypotheses = listOf("start navigate"),
            candidateActions = sampleActions,
            currentContext = VoiceScreenContext.MAIN_DASHBOARD
        )
        assertTrue(result is MatchResult.Matched)
        val matched = result as MatchResult.Matched
        assertEquals("main_navigate", matched.action.id)
    }

    @Test
    fun match_hypothesesFallbackResolution() {
        val result = VoiceMatcher.match(
            hypotheses = listOf("unrelated noise", "read text"),
            candidateActions = sampleActions,
            currentContext = VoiceScreenContext.MAIN_DASHBOARD
        )
        assertTrue(result is MatchResult.Matched)
        val matched = result as MatchResult.Matched
        assertEquals("main_read", matched.action.id)
    }

    @Test
    fun match_ignoresActionsFromDifferentScreenContext() {
        val result = VoiceMatcher.match(
            hypotheses = listOf("Trigger SOS"),
            candidateActions = sampleActions,
            currentContext = VoiceScreenContext.MAIN_DASHBOARD
        )
        // sos_trigger is for SOS_EMERGENCY, not MAIN_DASHBOARD
        assertTrue(result is MatchResult.NoMatch)
    }

    @Test
    fun match_ignoresDisabledActions() {
        val result = VoiceMatcher.match(
            hypotheses = listOf("special action"),
            candidateActions = sampleActions,
            currentContext = VoiceScreenContext.MAIN_DASHBOARD
        )
        assertTrue(result is MatchResult.NoMatch)
    }

    @Test
    fun match_unrecognizedUtteranceGeneratesSuggestions() {
        val result = VoiceMatcher.match(
            hypotheses = listOf("take me home"),
            candidateActions = sampleActions,
            currentContext = VoiceScreenContext.MAIN_DASHBOARD
        )
        assertTrue(result is MatchResult.NoMatch)
        val noMatch = result as MatchResult.NoMatch
        assertEquals("take me home", noMatch.recognizedText)
        assertTrue(noMatch.feedbackMessage.contains("take me home"))
        assertTrue(noMatch.availableSuggestions.contains("NAVIGATE"))
        assertTrue(noMatch.availableSuggestions.contains("READ TEXT"))
    }
}
