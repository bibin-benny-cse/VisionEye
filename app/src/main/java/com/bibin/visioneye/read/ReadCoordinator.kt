package com.bibin.visioneye.read

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.bibin.visioneye.camera.FrameAnalyzer
import com.bibin.visioneye.core.mode.VisionMode
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

    internal var activeJob: Job? = null
    private var isCoordinatorActive = false

    /**
     * Prevents duplicate automatic or manual capture requests while capture or OCR is executing.
     */
    var isCaptureInProgress = false
        private set

    /**
     * Most recent detected page candidate, preserved for manual capture fallback.
     */
    var lastDetectedPage: DetectedPage? = null
        internal set

    /**
     * Diagnostic timing for the most recent high-resolution camera capture in milliseconds.
     */
    var lastCaptureDurationMs: Long = 0L
        private set

    /**
     * Diagnostic timing for the entire READ capture-to-OCR pipeline in milliseconds.
     */
    var lastTotalPipelineDurationMs: Long = 0L
        private set

    private var searchStartTimeMs: Long = 0L
    private var lastSearchGuidanceTimeMs: Long = 0L
    private var lastMovingGuidanceTimeMs: Long = 0L

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

        // Only evaluate preview frames while searching, stabilizing candidate page, or waiting after missing boundary
        val currentState = _readState.value
        if (currentState !is ReadState.Searching &&
            currentState !is ReadState.PageDetected &&
            currentState !is ReadState.PageBoundaryNotDetected
        ) {
            return@FrameAnalyzer
        }

        processPreviewFrame(imageProxy)
    }

    /**
     * Activates the READ coordinator when the user switches to [VisionMode.READ].
     */
    fun activate() {
        isCoordinatorActive = true
        isCaptureInProgress = false
        lastDetectedPage = null
        searchStartTimeMs = System.currentTimeMillis()
        lastSearchGuidanceTimeMs = 0L
        lastMovingGuidanceTimeMs = 0L
        stabilityTracker.reset()
        _readState.value = ReadState.Searching
        speechController?.announceMode(VisionMode.READ)
    }

    /**
     * Deactivates the coordinator, cancels background jobs, stops speech, and resets state.
     */
    fun deactivate() {
        isCoordinatorActive = false
        isCaptureInProgress = false
        lastDetectedPage = null
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
        isCaptureInProgress = false
        lastDetectedPage = null
        searchStartTimeMs = System.currentTimeMillis()
        lastSearchGuidanceTimeMs = 0L
        lastMovingGuidanceTimeMs = 0L
        stabilityTracker.reset()
        speechController?.stop()
        _readState.value = ReadState.Searching
        speechController?.speak("Scanning again. Align text in camera view.", SpeechPriority.HIGH)
    }

    /**
     * Manual capture: triggers immediate high-resolution capture of the currently
     * detected page boundary.
     *
     * If no verified page boundary is detected in view, refuses to run OCR on an
     * unverified full frame to prevent reading text outside the book page.
     * Provides clear spoken guidance instructing the user to reposition the book.
     */
    fun capturePageNow() {
        if (!isCoordinatorActive) return
        if (isCaptureInProgress) {
            Log.d(TAG, "capturePageNow ignored: capture or OCR operation is already running.")
            return
        }

        val detected = lastDetectedPage
        if (detected != null && detected.isVerifiedBoundary) {
            speechController?.speak("Capturing page. Hold still.", SpeechPriority.HIGH)
            triggerCapture(detected)
        } else {
            Log.d(TAG, "capturePageNow: No verified page boundary detected. Refusing full-frame OCR.")
            _readState.value = ReadState.PageBoundaryNotDetected()
            speechController?.speak(
                "Page boundary not detected. Reposition the book and try again.",
                SpeechPriority.HIGH
            )
        }
    }

    private fun processPreviewFrame(imageProxy: ImageProxy) {
        if (isCaptureInProgress) return

        try {
            val now = System.currentTimeMillis()
            val currentState = _readState.value

            // Concise spoken guidance when searching for a prolonged period without detection
            if (currentState is ReadState.Searching) {
                if (now - searchStartTimeMs >= 4500L && now - lastSearchGuidanceTimeMs >= 9000L) {
                    lastSearchGuidanceTimeMs = now
                    speechController?.speak("Looking for page. Align text in camera view.", SpeechPriority.NORMAL)
                }
            }

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
                lastDetectedPage = detectedPage
                if (stability.isStable) {
                    // Page has stabilized; trigger automatic high-res capture
                    triggerAutomaticCapture(detectedPage)
                } else {
                    _readState.value = ReadState.PageDetected(
                        progress = stability.progress,
                        detectedPage = detectedPage
                    )
                    // Concise spoken guidance when page is found but camera/page is moving
                    if (now - lastMovingGuidanceTimeMs >= 3500L) {
                        lastMovingGuidanceTimeMs = now
                        speechController?.speak("Page in view. Hold steady.", SpeechPriority.NORMAL)
                    }
                }
            } else {
                if (_readState.value is ReadState.PageDetected) {
                    _readState.value = ReadState.Searching
                    searchStartTimeMs = now
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error during preview page detection", t)
        }
    }

    private fun triggerAutomaticCapture(page: DetectedPage) {
        if (isCaptureInProgress) return
        speechController?.speak("Page detected. Hold still.", SpeechPriority.HIGH)
        triggerCapture(page)
    }

    private fun triggerCapture(page: DetectedPage) {
        if (isCaptureInProgress) return
        if (!page.isVerifiedBoundary) {
            Log.w(TAG, "triggerCapture rejected unverified boundary; aborting capture.")
            _readState.value = ReadState.PageBoundaryNotDetected()
            speechController?.speak(
                "Page boundary not detected. Reposition the book and try again.",
                SpeechPriority.HIGH
            )
            return
        }

        val capture = captureProvider
        if (capture == null) {
            Log.w(TAG, "Capture requested but captureProvider is null")
            _readState.value = ReadState.Error("Camera capture unavailable.")
            return
        }

        isCaptureInProgress = true
        _readState.value = ReadState.Capturing(page)
        val captureStartTime = System.currentTimeMillis()

        capture(
            { highResBitmap ->
                lastCaptureDurationMs = System.currentTimeMillis() - captureStartTime
                Log.d(TAG, "High-resolution capture completed in ${lastCaptureDurationMs}ms (${highResBitmap.width}x${highResBitmap.height})")
                handleCapturedBitmap(highResBitmap, page, captureStartTime)
            },
            { exception ->
                Log.e(TAG, "High-resolution capture failed", exception)
                isCaptureInProgress = false
                _readState.value = ReadState.Error("Image capture failed.")
                speechController?.speak("Capture failed.", SpeechPriority.NORMAL)
            }
        )
    }

    private fun handleCapturedBitmap(highResBitmap: Bitmap, page: DetectedPage, pipelineStartTime: Long) {
        if (!page.isVerifiedBoundary) {
            Log.w(TAG, "handleCapturedBitmap rejected unverified boundary; refusing OCR.")
            isCaptureInProgress = false
            _readState.value = ReadState.PageBoundaryNotDetected()
            speechController?.speak(
                "Page boundary not detected. Reposition the book and try again.",
                SpeechPriority.HIGH
            )
            return
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                _readState.value = ReadState.ProcessingOcr()
                speechController?.speak("Processing page.", SpeechPriority.NORMAL)

                // Preserve captured original image for debug inspection
                ReadDebugStore.lastCapturedBitmap = highResBitmap

                // 1. Perspective crop and straighten
                val rectifiedBitmap = withContext(backgroundDispatcher) {
                    pageImageProcessor.rectifyPage(highResBitmap, page)
                }
                ReadDebugStore.lastRectifiedBitmap = rectifiedBitmap

                // 2. Perform Google ML Kit Latin OCR and reading-order structuring
                val extractedText = withContext(backgroundDispatcher) {
                    ocrTextExtractor.extractText(rectifiedBitmap)
                }
                ReadDebugStore.lastOrganizedText = extractedText

                lastTotalPipelineDurationMs = System.currentTimeMillis() - pipelineStartTime
                Log.d(TAG, "READ pipeline (capture to OCR) finished in ${lastTotalPipelineDurationMs}ms")

                if (extractedText.isBlank()) {
                    isCaptureInProgress = false
                    _readState.value = ReadState.Error("No text detected on page.")
                    speechController?.speak("No text found on page.", SpeechPriority.NORMAL)
                    return@launch
                }

                // 3. Chunk text into sequential phrases safe for TTS
                val chunks = textChunker.chunk(extractedText)
                ReadDebugStore.lastSpeechChunks = chunks

                if (chunks.isEmpty()) {
                    isCaptureInProgress = false
                    _readState.value = ReadState.Error("No text detected on page.")
                    speechController?.speak("No text found on page.", SpeechPriority.NORMAL)
                    return@launch
                }

                // 4. Vocalize text sequentially
                vocalizeChunks(extractedText, chunks)

            } catch (t: Throwable) {
                Log.e(TAG, "Error in READ OCR processing pipeline", t)
                isCaptureInProgress = false
                _readState.value = ReadState.Error(t.localizedMessage ?: "OCR processing failed.")
                speechController?.speak("Unable to read text.", SpeechPriority.NORMAL)
            }
        }
    }

    private suspend fun vocalizeChunks(fullText: String, chunks: List<String>) {
        val controller = speechController
        if (controller == null) {
            isCaptureInProgress = false
            _readState.value = ReadState.Completed(fullText)
            return
        }

        // Flush any pending preview guidance ("Page in view...", "Processing page...") from TTS queue
        controller.stop()
        delay(50L)

        // Announce that reading is beginning
        controller.speak("Reading.", SpeechPriority.NORMAL)
        delay(120L)

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

        isCaptureInProgress = false
        if (isCoordinatorActive) {
            _readState.value = ReadState.Completed(fullText)
            speechController?.speak("Reading complete.", SpeechPriority.NORMAL)
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
