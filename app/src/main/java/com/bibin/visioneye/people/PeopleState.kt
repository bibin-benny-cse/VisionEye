package com.bibin.visioneye.people

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.HorizontalPosition

/**
 * High-level status indicator for VisionEye PEOPLE mode.
 *
 * @property label User-facing status string displayed on the UI and announced by TalkBack.
 */
enum class PeopleScanStatus(val label: String) {
    LOOKING_FOR_PEOPLE("Looking for people..."),
    PERSON_RECOGNIZED("Person recognized"),
    MULTIPLE_PEOPLE_RECOGNIZED("Multiple people recognized"),
    UNKNOWN_PERSON("Unknown person")
}

/**
 * Observable runtime state for VisionEye PEOPLE face recognition mode.
 *
 * @property status Current high-level scan status.
 * @property recognizedNames List of confirmed saved person names visible in view (e.g. ["Father"]).
 * @property hasUnknownPerson True if an un-enrolled / unknown face is visible.
 * @property isConfirmed True only if the temporal confirmation rule is satisfied across frames.
 * @property candidates All evaluated face recognition candidates in the current frame.
 * @property lastSpokenAlert Last spoken announcement vocalized by TTS.
 * @property inferenceTimeMs Latency in milliseconds for face detection + embedding extraction.
 * @property isActive True when PEOPLE mode is actively scanning.
 */
data class PeopleState(
    val status: PeopleScanStatus = PeopleScanStatus.LOOKING_FOR_PEOPLE,
    val recognizedNames: List<String> = emptyList(),
    val hasUnknownPerson: Boolean = false,
    val isConfirmed: Boolean = false,
    val candidates: List<FaceRecognitionCandidate> = emptyList(),
    val lastSpokenAlert: String? = null,
    val inferenceTimeMs: Long = 0L,
    val isActive: Boolean = false
) {
    /**
     * Total count of recognized individuals plus unknown persons.
     */
    val confirmedCount: Int
        get() = recognizedNames.size + (if (hasUnknownPerson) 1 else 0)

    /**
     * Accessible status text for TalkBack and UI banner.
     */
    val statusMessage: String
        get() = when {
            recognizedNames.size >= 2 -> "${formatNames(recognizedNames)} recognized"
            recognizedNames.size == 1 -> "${recognizedNames.first()} recognized"
            hasUnknownPerson -> "Unknown person"
            else -> status.label
        }

    /**
     * Adapt candidates to [Detection] for optional bounding-box rendering on the camera screen.
     */
    val detections: List<Detection>
        get() = candidates.map { cand ->
            val box = cand.face.boundingBox
            val centerX = (box.left + box.right) / 2f
            val position = when {
                centerX < 0.35f -> HorizontalPosition.LEFT
                centerX > 0.65f -> HorizontalPosition.RIGHT
                else -> HorizontalPosition.CENTER
            }
            Detection(
                classId = if (cand.isKnown) 1 else 0,
                label = cand.displayName,
                confidence = cand.similarity,
                boundingBox = BoundingBox(box.left, box.top, box.right, box.bottom),
                position = position
            )
        }

    companion object {
        fun formatNames(names: List<String>): String {
            return when (names.size) {
                0 -> ""
                1 -> names[0]
                2 -> "${names[0]} and ${names[1]}"
                else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
            }
        }
    }
}
