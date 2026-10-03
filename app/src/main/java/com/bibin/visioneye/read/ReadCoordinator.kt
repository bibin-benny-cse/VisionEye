package com.bibin.visioneye.read

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.bibin.visioneye.camera.FrameAnalyzer
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import com.bibin.visioneye.speech.SpeechUtteranceListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/**
 * Central coordinator for VisionEye READ mode.
 *
 * Implements the minimal, direct pipeline:
 * [Search Page] -> [Temporal Stability Check] -> [Automatic High-Res Capture] -> [Perspective Rectification] -> [Google ML Kit Latin OCR] -> [Structured Reading Order] -> [Sequential TTS Feedback]
 *
 * Adheres strictly to VisionEye principles:
 * - Reuses existing CameraX ImageAnalysis + ImageCapture
 * - Purely on-device processing
 * - Preserves exact recognized English text without spelling corrections or AI hallucination
 * - Offloads all inference, transformation, and OCR to background threads
 */
class ReadCoordinator(
    private val speechController: SpeechController? = null,
    val pageDetector: PageDetector = PageDetector(),
    val stabilityTracker: PageStabilityTracker = PageStabilityTracker(),
    val pageImageProcessor: PageImageProcessor = PageImageProcessor(),
    val ocrTextExtractor: OcrTextExtractor = OcrTextExtractor(),
    val textChunker: TextChunker = TextChunker(),
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)

    private val _readState = MutableStateFlow<ReadState>(ReadState.Searching)
    val readState: StateFlow<ReadState> = _readState.asStateFlow()

    private var activeJob: Job? = null
    private var isCoordinatorActive = false

    /**
     * Provider callback for triggering high-resolution captures via CameraX ImageCapture.
     */
    var captureProvider: ((onSuccess: (Bitmap) -> Unit, onError: (Throwable) -> Unit) -> Unit)? = null

    /**
     * Frame analyzer connected to CameraX [com.bibin.visioneye.camera.FrameAnalysisDispatcher].
     * Active only while in [VisionMode.READ].
     */
    val frameAnalyzer = FrameAnalyzer { imageProxy ->
        if (!isCoordinatorActive) return@FrameAnalyzer

        // Only evaluate preview frames while searching or stabilizing candidate page
        val currentState = _readState.value
        if (currentState !is ReadState.Searching && currentState !is ReadState.PageDetected) {
            return@FrameAnalyzer
        }

        processPreviewFrame(imageProxy)
    }

    /**
     * Activates the READ coordinator when the user switches to [VisionMode.READ].
     */
    fun activate() {
        isCoordinatorActive = true
        stabilityTracker.reset()
        _readState.value = ReadState.Searching
    }

    /**
     * Deactivates the coordinator, cancels background jobs, stops speech, and resets state.
     */
    fun deactivate() {
        isCoordinatorActive = false
        activeJob?.cancel()
        activeJob = null
        stabilityTracker.reset()
        speechController?.stop()
        _readState.value = ReadState.Searching
    }

    /**
     * Resets the coordinator to search for a new page (e.g. after completion or error).
     */
    fun scanAgain() {
        activeJob?.cancel()
        activeJob = null
        stabilityTracker.reset()
        speechController?.stop()
        _readState.value = ReadState.Searching
    }

    private fun processPreviewFrame(imageProxy: ImageProxy) {
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()

            val uprightBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val detectedPage = pageDetector.detect(uprightBitmap)
            val stability = stabilityTracker.update(detectedPage)

            if (detectedPage != null) {
                if (stability.isStable) {
                    // Page has stabilized; trigger automatic high-res capture
                    triggerAutomaticCapture(detectedPage)
                } else {
                    _readState.value = ReadState.PageDetected(
                        progress = stability.progress,
                        detectedPage = detectedPage
                    )
                }
            } else {
                if (_readState.value is ReadState.PageDetected) {
                    _readState.value = ReadState.Searching
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error during preview page detection", t)
        }
    }

    private fun triggerAutomaticCapture(page: DetectedPage) {
        val capture = captureProvider
        if (capture == null) {
            Log.w(TAG, "Automatic capture requested but captureProvider is null")
            _readState.value = ReadState.Error("Camera capture unavailable.")
            return
        }

        _readState.value = ReadState.Capturing(page)

        capture(
            { highResBitmap ->
                handleCapturedBitmap(highResBitmap, page)
            },
            { exception ->
                Log.e(TAG, "High-resolution capture failed", exception)
                _readState.value = ReadState.Error("Image capture failed.")
                speechController?.speak("Capture failed.", SpeechPriority.NORMAL)
            }
        )
    }

    private fun handleCapturedBitmap(highResBitmap: Bitmap, page: DetectedPage) {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                _readState.value = ReadState.ProcessingOcr

                // 1. Perspective crop and straighten
                val rectifiedBitmap = withContext(backgroundDispatcher) {
                    pageImageProcessor.rectifyPage(highResBitmap, page)
                }

                // 2. Perform Google ML Kit Latin OCR and reading-order structuring
                val extractedText = withContext(backgroundDispatcher) {
                    ocrTextExtractor.extractText(rectifiedBitmap)
                }

                if (extractedText.isBlank()) {
                    _readState.value = ReadState.Error("No text detected on page.")
                    speechController?.speak("No text found on page.", SpeechPriority.NORMAL)
                    return@launch
                }

                // 3. Chunk text into sequential phrases safe for TTS
                val chunks = textChunker.chunk(extractedText)
                if (chunks.isEmpty()) {
                    _readState.value = ReadState.Error("No text detected on page.")
                    speechController?.speak("No text found on page.", SpeechPriority.NORMAL)
                    return@launch
                }

                // 4. Vocalize text sequentially
                vocalizeChunks(extractedText, chunks)

            } catch (t: Throwable) {
                Log.e(TAG, "Error in READ OCR processing pipeline", t)
                _readState.value = ReadState.Error(t.localizedMessage ?: "OCR processing failed.")
                speechController?.speak("Unable to read text.", SpeechPriority.NORMAL)
            }
        }
    }

    private suspend fun vocalizeChunks(fullText: String, chunks: List<String>) {
        val controller = speechController
        if (controller == null) {
            _readState.value = ReadState.Completed(fullText)
            return
        }

        for (i in chunks.indices) {
            if (!scope.isActive || !isCoordinatorActive) break

            val chunk = chunks[i]
            _readState.value = ReadState.Reading(
                fullText = fullText,
                currentChunk = chunk,
                chunkIndex = i,
                totalChunks = chunks.size
            )

            val targetUtteranceId = "visioneye_read_chunk_${System.currentTimeMillis()}_$i"
            val completionDeferred = CompletableDeferred<Unit>()

            val listener = object : SpeechUtteranceListener {
                override fun onUtteranceCompleted(utteranceId: String) {
                    if (utteranceId == targetUtteranceId) {
                        completionDeferred.complete(Unit)
                    }
                }

                override fun onUtteranceError(utteranceId: String, errorCode: Int?) {
                    if (utteranceId == targetUtteranceId) {
                        completionDeferred.complete(Unit)
                    }
                }
            }

            controller.addUtteranceListener(listener)
            try {
                controller.speak(chunk, SpeechPriority.HIGH, targetUtteranceId)
                // Wait for utterance completion with adaptive timeout
                val timeoutMs = max(6000L, chunk.length * 120L)
                withTimeoutOrNull(timeoutMs) {
                    completionDeferred.await()
                }
                delay(180L) // Small natural acoustic pause between sentences
            } finally {
                controller.removeUtteranceListener(listener)
            }
        }

        if (isCoordinatorActive) {
            _readState.value = ReadState.Completed(fullText)
        }
    }

    /**
     * Cleanly shuts down internal OCR resources.
     */
    fun release() {
        deactivate()
        ocrTextExtractor.close()
    }

    companion object {
        private const val TAG = "ReadCoordinator"
    }
}
