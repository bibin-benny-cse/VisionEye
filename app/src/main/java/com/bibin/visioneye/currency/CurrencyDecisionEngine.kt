package com.bibin.visioneye.currency

/**
 * Result produced by evaluating a single vision frame in Currency Mode.
 *
 * @property detections All validated banknote detections in this frame.
 * @property confirmedDenominations List of temporally confirmed banknote denominations.
 * @property isConfirmed True if temporal confirmation criteria are satisfied (at least 2 consecutive frames).
 * @property spokenAlert Speech announcement to vocalize via TTS, or null if silent.
 * @property statusMessage Concise status summary for UI display.
 */
data class CurrencyDecisionResult(
    val detections: List<CurrencyDetection>,
    val confirmedDenominations: List<CurrencyDenomination>,
    val isConfirmed: Boolean,
    val spokenAlert: String?,
    val statusMessage: String
)

/**
 * Architectural contract for the temporal decision layer in Currency Mode.
 */
interface CurrencyDecisionEngine {
    /**
     * Evaluates banknote detections from a camera frame and updates temporal confirmation state.
     *
     * @param detections Banknote detections in this frame.
     * @param timestampMs Frame timestamp in milliseconds.
     */
    fun process(
        detections: List<CurrencyDetection>,
        timestampMs: Long = System.currentTimeMillis()
    ): CurrencyDecisionResult

    /**
     * Resets all confirmation history, frame counters, and speech cooldowns.
     */
    fun reset()
}

/**
 * Concrete implementation of [CurrencyDecisionEngine].
 *
 * Enforces conservative temporal rules:
 * - Requires stable detection across [temporalConfirmationCount] qualifying frames (default: 2) before confirming.
 * - Suppresses repeated voice announcements while banknotes remain continuously visible in camera view.
 * - Resets session confirmation after [absenceTimeoutMs] (default: 1500 ms) of absence.
 * - Keeps idle and no-note frames completely silent.
 */
class DefaultCurrencyDecisionEngine(
    val temporalConfirmationCount: Int = 2,
    val absenceTimeoutMs: Long = 1500L
) : CurrencyDecisionEngine {

    private val lock = Any()

    private var consecutiveQualifyingFrames = 0
    private var lastObservedDenominations: Set<CurrencyDenomination> = emptySet()
    private var lastSeenTimestampMs = 0L

    private var currentConfirmedDenominations: List<CurrencyDenomination> = emptyList()
    private var lastSpokenDenominations: List<CurrencyDenomination> = emptyList()
    private var lastSpokenText: String? = null

    override fun process(
        detections: List<CurrencyDetection>,
        timestampMs: Long
    ): CurrencyDecisionResult = synchronized(lock) {
        if (detections.isEmpty()) {
            // No banknotes in frame
            consecutiveQualifyingFrames = 0
            lastObservedDenominations = emptySet()

            // Check if absent longer than timeout threshold -> reset confirmation & speech memory
            if (lastSeenTimestampMs > 0L && (timestampMs - lastSeenTimestampMs) >= absenceTimeoutMs) {
                currentConfirmedDenominations = emptyList()
                lastSpokenDenominations = emptyList()
                lastSpokenText = null
                lastSeenTimestampMs = 0L
            }

            val isConfirmed = currentConfirmedDenominations.isNotEmpty()
            val status = if (isConfirmed) {
                "CONFIRMED: ${formatDenominationsSummary(currentConfirmedDenominations)}"
            } else {
                "SCANNING FOR BANKNOTE..."
            }

            // Requirement 8: Keep idle/no-note frames completely silent
            return CurrencyDecisionResult(
                detections = emptyList(),
                confirmedDenominations = currentConfirmedDenominations,
                isConfirmed = isConfirmed,
                spokenAlert = null,
                statusMessage = status
            )
        }

        // Banknotes are present in frame
        lastSeenTimestampMs = timestampMs

        val currentDenominations = detections.map { it.denomination }.sortedByDescending { it.numericValue }
        val currentSet = currentDenominations.toSet()

        if (currentSet == lastObservedDenominations) {
            consecutiveQualifyingFrames++
        } else {
            consecutiveQualifyingFrames = 1
            lastObservedDenominations = currentSet
        }

        // Confirm denomination only after consecutive qualifying frames
        if (consecutiveQualifyingFrames >= temporalConfirmationCount) {
            currentConfirmedDenominations = currentDenominations
        }

        val isConfirmed = currentConfirmedDenominations.isNotEmpty()
        var alertToSpeak: String? = null

        if (isConfirmed) {
            // Check if the confirmed denomination set differs from what has already been spoken in this continuous session
            val stateChanged = currentConfirmedDenominations != lastSpokenDenominations
            if (stateChanged) {
                val utterance = CurrencyDenomination.formatSpokenAnnouncement(currentConfirmedDenominations)
                alertToSpeak = utterance
                lastSpokenDenominations = currentConfirmedDenominations
                lastSpokenText = utterance
            }
        }

        val status = if (isConfirmed) {
            "CONFIRMED: ${formatDenominationsSummary(currentConfirmedDenominations)}"
        } else {
            "DETECTING... (${consecutiveQualifyingFrames}/$temporalConfirmationCount)"
        }

        return CurrencyDecisionResult(
            detections = detections,
            confirmedDenominations = currentConfirmedDenominations,
            isConfirmed = isConfirmed,
            spokenAlert = alertToSpeak,
            statusMessage = status
        )
    }

    override fun reset() = synchronized(lock) {
        consecutiveQualifyingFrames = 0
        lastObservedDenominations = emptySet()
        lastSeenTimestampMs = 0L
        currentConfirmedDenominations = emptyList()
        lastSpokenDenominations = emptyList()
        lastSpokenText = null
    }

    private fun formatDenominationsSummary(denominations: List<CurrencyDenomination>): String {
        return denominations.joinToString(", ") { "₹${it.numericValue}" }
    }
}
