package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.DetectorStatusCode

/**
 * Observable runtime diagnostic and display state for Currency Mode.
 *
 * @property isReady True if currency model is loaded and ready for inference.
 * @property statusCode Diagnostic detector status code.
 * @property statusMessage User-facing status summary.
 * @property diagnosticDetails Optional developer diagnostic message.
 * @property detections Banknote detections in the most recent analyzed frame.
 * @property confirmedDenominations Temporally confirmed banknote denominations.
 * @property isConfirmed True if at least 2 consecutive qualifying frames observed.
 * @property lastSpokenAnnouncement Most recent vocalized speech announcement, if any.
 * @property inferenceTimeMs Latency in milliseconds for the most recent frame analysis.
 * @property lastInferenceTimestamp Epoch timestamp of the last analyzed frame.
 */
data class CurrencyState(
    val isReady: Boolean = false,
    val statusCode: DetectorStatusCode = DetectorStatusCode.INITIALIZING,
    val statusMessage: String = "INITIALIZING",
    val diagnosticDetails: String? = null,
    val detections: List<CurrencyDetection> = emptyList(),
    val confirmedDenominations: List<CurrencyDenomination> = emptyList(),
    val isConfirmed: Boolean = false,
    val lastSpokenAnnouncement: String? = null,
    val inferenceTimeMs: Long = 0L,
    val lastInferenceTimestamp: Long = 0L
)
