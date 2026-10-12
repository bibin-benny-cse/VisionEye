package com.bibin.visioneye.voice

import java.util.Locale

/**
 * Result of attempting to match one or more spoken hypotheses against registered actions.
 */
sealed interface MatchResult {
    /** An action matched successfully. */
    data class Matched(val action: VoiceAction, val matchedPhrase: String) : MatchResult

    /**
     * Speech was recognized, but no registered action on the current screen matched.
     * Contains user-friendly recovery suggestions generated from actual visible controls.
     */
    data class NoMatch(
        val recognizedText: String,
        val availableSuggestions: List<String>,
        val feedbackMessage: String
    ) : MatchResult

    /** Empty or blank recognition input. */
    data object EmptyInput : MatchResult
}

/**
 * Normalizer and matching engine for spoken commands in VisionEye.
 *
 * Implements predictable normalization:
 * - Case insensitivity
 * - Punctuation stripping
 * - Whitespace consolidation
 * - Hypotheses iteration from SpeechRecognizer results
 * - Prioritization of current screen context before global commands
 * - Constructive error recovery with concrete visible control suggestions
 */
object VoiceMatcher {

    /**
     * Normalizes an utterance or command phrase for robust comparison.
     */
    fun normalize(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ") // Strip punctuation, symbols, slashes
            .replace(Regex("\\s+"), " ")         // Collapse spaces
            .trim()
    }

    /**
     * Attempts to match recognized speech hypotheses against candidate actions.
     *
     * @param hypotheses List of alternative transcriptions from SpeechRecognizer.
     * @param candidateActions List of currently registered actions for consideration.
     * @param currentContext Current active screen context.
     * @return [MatchResult] indicating matched action or constructive recovery message.
     */
    fun match(
        hypotheses: List<String>,
        candidateActions: List<VoiceAction>,
        currentContext: VoiceScreenContext
    ): MatchResult {
        val cleanHypotheses = hypotheses
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (cleanHypotheses.isEmpty()) {
            return MatchResult.EmptyInput
        }

        val primaryUtterance = cleanHypotheses.first()
        val availableActions = candidateActions.filter { it.isAvailable() }

        // 1. Check exact matches on current screen context first
        for (rawHypothesis in cleanHypotheses) {
            val normalized = normalize(rawHypothesis)
            if (normalized.isBlank()) continue

            // A. Exact match against canonicalCommand or visibleLabel in current screen
            val screenAction = availableActions.firstOrNull { action ->
                action.screenContext == currentContext && (
                    normalize(action.canonicalCommand) == normalized ||
                    normalize(action.visibleLabel) == normalized ||
                    action.aliases.any { normalize(it) == normalized }
                )
            }
            if (screenAction != null) {
                return MatchResult.Matched(screenAction, rawHypothesis)
            }
        }

        // 2. Check global actions (e.g. "What can I do here?", "Help")
        for (rawHypothesis in cleanHypotheses) {
            val normalized = normalize(rawHypothesis)
            val globalAction = availableActions.firstOrNull { action ->
                action.screenContext == null && (
                    normalize(action.canonicalCommand) == normalized ||
                    normalize(action.visibleLabel) == normalized ||
                    action.aliases.any { normalize(it) == normalized }
                )
            }
            if (globalAction != null) {
                return MatchResult.Matched(globalAction, rawHypothesis)
            }
        }

        // 3. No match found: Generate constructive contextual suggestions
        val visibleLabels = availableActions
            .filter { it.screenContext == currentContext || it.screenContext == null }
            .map { it.visibleLabel }
            .distinct()
            .take(3)

        val suggestionText = if (visibleLabels.isNotEmpty()) {
            "On this screen you can say: " + visibleLabels.joinToString(", ")
        } else {
            "Say 'What can I do here?' for available controls."
        }

        val feedback = "I heard \"$primaryUtterance\". $suggestionText."

        return MatchResult.NoMatch(
            recognizedText = primaryUtterance,
            availableSuggestions = visibleLabels,
            feedbackMessage = feedback
        )
    }
}
