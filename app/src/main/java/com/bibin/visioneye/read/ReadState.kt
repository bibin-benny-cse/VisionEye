package com.bibin.visioneye.read

/**
 * State machine representing the active lifecycle of VisionEye READ mode.
 *
 * Flow:
 * Searching -> PageDetected (stabilizing) -> Capturing -> ProcessingOcr -> Reading -> Completed / Error
 */
sealed interface ReadState {
    /**
     * Searching for a physical page or document in front of the camera.
     */
    data object Searching : ReadState

    /**
     * A candidate page is in view and stabilizing before automatic capture.
     *
     * @property progress Stabilization progress from 0.0f to 1.0f.
     * @property detectedPage The currently detected page boundary.
     */
    data class PageDetected(
        val progress: Float,
        val detectedPage: DetectedPage
    ) : ReadState

    /**
     * Triggering high-resolution capture via CameraX ImageCapture.
     */
    data class Capturing(val detectedPage: DetectedPage) : ReadState

    /**
     * High-res image captured; performing perspective correction and ML Kit OCR.
     */
    data object ProcessingOcr : ReadState

    /**
     * OCR completed; actively vocalizing text sequentially.
     *
     * @property fullText The complete formatted text extracted from the page.
     * @property currentChunk The individual sentence or phrase currently being spoken.
     * @property chunkIndex 0-based index of the currently spoken chunk.
     * @property totalChunks Total number of sequential speech chunks.
     */
    data class Reading(
        val fullText: String,
        val currentChunk: String,
        val chunkIndex: Int,
        val totalChunks: Int
    ) : ReadState

    /**
     * Vocalization of the page has completed successfully.
     *
     * @property fullText The complete formatted text extracted from the page.
     */
    data class Completed(val fullText: String) : ReadState

    /**
     * An error occurred during capture, image processing, or OCR.
     *
     * @property message Short, user-facing error description.
     */
    data class Error(val message: String) : ReadState
}
