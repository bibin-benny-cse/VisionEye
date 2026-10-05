package com.bibin.visioneye.people

/**
 * Result produced by evaluating a single vision frame in PEOPLE mode.
 *
 * @property candidates All evaluated face recognition candidates in this frame.
 * @property recognizedNames List of confirmed known person names.
 * @property hasUnknownPerson True if an unknown person is detected.
 * @property isConfirmed True if temporal confirmation criteria are satisfied.
 * @property status Current user-facing scan status.
 * @property spokenAlert Speech announcement to vocalize via TTS, or null if no new alert.
 */
data class PeopleDecisionResult(
    val candidates: List<FaceRecognitionCandidate>,
    val recognizedNames: List<String>,
    val hasUnknownPerson: Boolean,
    val isConfirmed: Boolean,
    val status: PeopleScanStatus,
    val spokenAlert: String?
)

/**
 * Contract for the conservative temporal decision layer in PEOPLE mode.
 */
interface PeopleDecisionEngine {
    /**
     * Evaluates face recognition candidates from a camera frame and updates temporal confirmation state.
     *
     * @param candidates Face recognition candidates in this frame.
     * @param timestampMs Frame timestamp in milliseconds.
     */
    fun process(
        candidates: List<FaceRecognitionCandidate>,
        timestampMs: Long = System.currentTimeMillis()
    ): PeopleDecisionResult

    /**
     * Resets all confirmation history, frame counters, and speech cooldowns.
     */
    fun reset()
}

/**
 * Concrete implementation of [PeopleDecisionEngine].
 *
 * Enforces conservative temporal rules:
 * - Requires the same identity set to be recognized across at least [config.temporalConfirmationCount] frames.
 * - Deduplicates continuous visibility so the same person is not repeatedly vocalized.
 * - Formats speech utterances:
 *     - 1 person: "[Name] is in front of you."
 *     - Multiple: "[Name] and [Name] are in front of you."
 *     - Known + Unknown: Only identifies confirmed known names without guessing.
 *     - Unknown: Configurable via [config.speakUnknownPerson].
 * - Resets on absence beyond [config.absenceTimeoutMs].
 */
class DefaultPeopleDecisionEngine(
    val config: PeopleRecognitionConfig = PeopleRecognitionConfig()
) : PeopleDecisionEngine {

    private val lock = Any()

    private var consecutiveQualifyingFrames = 0
    private var lastObservedNames: Set<String> = emptySet()
    private var lastSeenTimestampMs = 0L

    private var currentConfirmedNames: Set<String> = emptySet()
    private var currentConfirmedHasUnknown: Boolean = false

    private var lastSpokenNames: Set<String> = emptySet()
    private var lastSpokenHasUnknown: Boolean = false
    private var lastAlertTimestampMs = 0L

    override fun process(
        candidates: List<FaceRecognitionCandidate>,
        timestampMs: Long
    ): PeopleDecisionResult = synchronized(lock) {
        // Extract known names and unknown face flags
        val knownCandidates = candidates.filter { it.isKnown && it.matchedPerson != null }
        val currentNames = knownCandidates.map { it.matchedPerson!!.name }.distinct().sorted().toSet()
        val hasUnknown = candidates.any { !it.isKnown }

        if (candidates.isEmpty()) {
            // No faces in frame
            consecutiveQualifyingFrames = 0
            lastObservedNames = emptySet()

            if (lastSeenTimestampMs > 0L && (timestampMs - lastSeenTimestampMs) > config.absenceTimeoutMs) {
                currentConfirmedNames = emptySet()
                currentConfirmedHasUnknown = false
                lastSpokenNames = emptySet()
                lastSpokenHasUnknown = false
                lastSeenTimestampMs = 0L
                lastAlertTimestampMs = 0L
            }

            val isConfirmed = currentConfirmedNames.isNotEmpty() || currentConfirmedHasUnknown
            val status = when {
                currentConfirmedNames.size >= 2 -> PeopleScanStatus.MULTIPLE_PEOPLE_RECOGNIZED
                currentConfirmedNames.size == 1 -> PeopleScanStatus.PERSON_RECOGNIZED
                currentConfirmedHasUnknown -> PeopleScanStatus.UNKNOWN_PERSON
                else -> PeopleScanStatus.LOOKING_FOR_PEOPLE
            }

            return PeopleDecisionResult(
                candidates = emptyList(),
                recognizedNames = currentConfirmedNames.toList(),
                hasUnknownPerson = currentConfirmedHasUnknown,
                isConfirmed = isConfirmed,
                status = status,
                spokenAlert = null
            )
        }

        // Faces are present
        lastSeenTimestampMs = timestampMs

        // Check if the current frame matches the previous frame's observed identities
        if (currentNames == lastObservedNames) {
            consecutiveQualifyingFrames++
        } else {
            consecutiveQualifyingFrames = 1
            lastObservedNames = currentNames
        }

        // Confirm identity only after consecutive qualifying frames
        if (consecutiveQualifyingFrames >= config.temporalConfirmationCount) {
            currentConfirmedNames = currentNames
            currentConfirmedHasUnknown = hasUnknown && currentNames.isEmpty()
        }

        val isConfirmed = currentConfirmedNames.isNotEmpty() || currentConfirmedHasUnknown
        val status = when {
            currentConfirmedNames.size >= 2 -> PeopleScanStatus.MULTIPLE_PEOPLE_RECOGNIZED
            currentConfirmedNames.size == 1 -> PeopleScanStatus.PERSON_RECOGNIZED
            currentConfirmedHasUnknown -> PeopleScanStatus.UNKNOWN_PERSON
            else -> PeopleScanStatus.LOOKING_FOR_PEOPLE
        }

        // Determine spoken alert
        var alertToSpeak: String? = null
        if (isConfirmed) {
            val stateChanged = (currentConfirmedNames != lastSpokenNames) ||
                (currentConfirmedHasUnknown != lastSpokenHasUnknown)

            val cooldownElapsed = (lastAlertTimestampMs == 0L) ||
                ((timestampMs - lastAlertTimestampMs) >= config.recognitionCooldownMs)

            if (stateChanged && cooldownElapsed) {
                if (currentConfirmedNames.isNotEmpty()) {
                    val namesList = currentConfirmedNames.toList()
                    val formatted = when (namesList.size) {
                        1 -> "${namesList[0]} is in front of you."
                        2 -> "${namesList[0]} and ${namesList[1]} are in front of you."
                        else -> namesList.dropLast(1).joinToString(", ") + ", and " + namesList.last() + " are in front of you."
                    }
                    alertToSpeak = formatted
                    lastSpokenNames = currentConfirmedNames
                    lastSpokenHasUnknown = currentConfirmedHasUnknown
                    lastAlertTimestampMs = timestampMs
                } else if (currentConfirmedHasUnknown && config.speakUnknownPerson) {
                    alertToSpeak = "Unknown person in front of you."
                    lastSpokenNames = emptySet()
                    lastSpokenHasUnknown = true
                    lastAlertTimestampMs = timestampMs
                }
            }
        }

        return PeopleDecisionResult(
            candidates = candidates,
            recognizedNames = currentConfirmedNames.toList(),
            hasUnknownPerson = hasUnknown,
            isConfirmed = isConfirmed,
            status = status,
            spokenAlert = alertToSpeak
        )
    }

    override fun reset() = synchronized(lock) {
        consecutiveQualifyingFrames = 0
        lastObservedNames = emptySet()
        lastSeenTimestampMs = 0L
        currentConfirmedNames = emptySet()
        currentConfirmedHasUnknown = false
        lastSpokenNames = emptySet()
        lastSpokenHasUnknown = false
        lastAlertTimestampMs = 0L
    }
}
