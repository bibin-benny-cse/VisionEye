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
     * Manual capture was attempted, but no verified page boundary was detected in view.
     * Instructs user to reposition the book and retry detection.
     *
     * @property message Spoken/displayed guidance message.
     */
    data class PageBoundaryNotDetected(
        val message: String = "Page boundary not detected. Reposition the book and try again."
    ) : ReadState

    /**
     * Triggering high-resolution capture via CameraX ImageCapture.
     *
     * @property detectedPage The page boundary being captured (or full-frame fallback).
     * @property isVerifiedPage True if capturing a verified page boundary; false if unverified full-frame fallback.
     */
    data class Capturing(
        val detectedPage: DetectedPage,
        val isVerifiedPage: Boolean = detectedPage.isVerifiedBoundary
    ) : ReadState

    /**
     * High-res image captured; performing perspective correction and ML Kit OCR.
     *
     * @property isVerifiedPage True if processing a verified page crop; false if processing unverified full frame.
     */
    data class ProcessingOcr(
        val isVerifiedPage: Boolean = true
    ) : ReadState

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
