package com.bibin.visioneye.read

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device Optical Character Recognition (OCR) wrapper using Google ML Kit.
 *
 * Configured strictly for Latin / English text recognition.
 * Employs [ReadingOrderOrganizer] to reconstruct natural reading order from
 * ML Kit's structured block, line, and element geometry.
 */
open class OcrTextExtractor(
    private val readingOrderOrganizer: ReadingOrderOrganizer = ReadingOrderOrganizer(),
    private val recognizerProvider: () -> com.google.mlkit.vision.text.TextRecognizer = {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
) {
    private val recognizerDelegate = lazy { recognizerProvider() }
    private val recognizer by recognizerDelegate

    /**
     * Diagnostic timing for the most recent OCR pass in milliseconds.
     */
    var lastOcrDurationMs: Long = 0L
        private set

    /**
     * Extracts English text from the rectified [bitmap], arranged in natural reading order.
     *
     * Filters out sliced margin artifacts from adjacent pages or backgrounds while
     * strictly preserving legitimate text near page edges.
     *
     * @param bitmap Upright, perspective-corrected document bitmap.
     * @return Natural reading-order text string, or empty string if no text recognized.
     */
    open suspend fun extractText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val startTime = System.currentTimeMillis()
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val lines = mutableListOf<RecognizedLine>()
                var totalCharacters = 0
                var singleCharCount = 0
                var suspiciousSingleCharCount = 0
                val rawTextSb = StringBuilder()

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val rawLineText = line.text
                        rawTextSb.append(rawLineText).append("\n")
                        totalCharacters += rawLineText.length

                        for (element in line.elements) {
                            val elemText = element.text.trim()
                            if (elemText.length == 1) {
                                singleCharCount++
                                val c = elemText[0]
                                if (c != 'a' && c != 'A' && c != 'I' && !c.isDigit()) {
                                    suspiciousSingleCharCount++
                                }
                            }
                        }

                        val box = line.boundingBox
                        if (box != null) {
                            if (isInvalidOrBorderArtifact(box, line.text, imageWidth, imageHeight)) {
                                continue
                            }
                            lines.add(
                                RecognizedLine(
                                    text = line.text,
                                    left = box.left.toFloat(),
                                    top = box.top.toFloat(),
                                    right = box.right.toFloat(),
                                    bottom = box.bottom.toFloat()
                                )
                            )
                        } else {
                            if (line.text.isNotBlank() && !line.text.trim().all { !it.isLetterOrDigit() }) {
                                lines.add(
                                    RecognizedLine(
                                        text = line.text,
                                        left = 0f,
                                        top = 0f,
                                        right = 0f,
                                        bottom = 0f
                                    )
                                )
                            }
                        }
                    }
                }

                lastOcrDurationMs = System.currentTimeMillis() - startTime
                val suspiciousRatio = if (singleCharCount > 0) {
                    suspiciousSingleCharCount.toFloat() / singleCharCount
                } else {
                    0.0f
                }

                val diagnostics = OcrDiagnostics(
                    blockCount = visionText.textBlocks.size,
                    lineCount = lines.size,
                    totalCharCount = totalCharacters,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    durationMs = lastOcrDurationMs,
                    suspiciousSingleCharRatio = suspiciousRatio
                )
                ReadDebugStore.lastDiagnostics = diagnostics
                ReadDebugStore.lastRawOcrText = rawTextSb.toString().trim()

                Log.d(
                    TAG,
                    "OCR extracted in ${lastOcrDurationMs}ms: ${visionText.textBlocks.size} blocks, ${lines.size} lines, $totalCharacters chars, suspiciousSingleCharRatio=${String.format(java.util.Locale.US, "%.1f", suspiciousRatio * 100)}%"
                )

                val orderedText = readingOrderOrganizer.organize(lines)
                continuation.resume(orderedText)
            }
            .addOnFailureListener { exception ->
                lastOcrDurationMs = System.currentTimeMillis() - startTime
                Log.e(TAG, "OCR extraction failed after ${lastOcrDurationMs}ms", exception)
                continuation.resumeWithException(exception)
            }
    }

    /**
     * Checks if a line bounding box is completely invalid or represents a sliced margin artifact
     * (e.g., from an adjacent book page cut off at the page border).
     *
     * Crucially preserves legitimate margin text such as single digits (page numbers),
     * chapter headings, and footnotes.
     */
    internal fun isInvalidOrBorderArtifact(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        text: String,
        imageWidth: Int,
        imageHeight: Int
    ): Boolean {
        val boxWidth = right - left
        val boxHeight = bottom - top

        // Discard degenerate or blank boxes
        if (boxWidth <= 0 || boxHeight <= 0) return true
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true

        // Reject lines containing exclusively non-alphanumeric symbols and punctuation
        if (trimmed.all { !it.isLetterOrDigit() }) {
            return true
        }

        // Reject boxes located completely outside the image bounds
        if (right <= 0 || left >= imageWidth || bottom <= 0 || top >= imageHeight) {
            return true
        }

        // Check if bounding box touches the absolute edge of the cropped image
        val touchesLeftOrRightEdge = left <= 2 || right >= imageWidth - 2
        val touchesTopOrBottomEdge = top <= 2 || bottom >= imageHeight - 2

        // Sliced margin artifact: touching border, narrow (< 24px), and contains only 1-2 non-word symbols
        if ((touchesLeftOrRightEdge || touchesTopOrBottomEdge) && boxWidth < 24) {
            val isPunctuationOrSymbolOnly = trimmed.length <= 2 && trimmed.all { !it.isLetterOrDigit() }
            if (isPunctuationOrSymbolOnly) {
                return true
            }
        }

        return false
    }

    internal fun isInvalidOrBorderArtifact(
        box: Rect,
        text: String,
        imageWidth: Int,
        imageHeight: Int
    ): Boolean = isInvalidOrBorderArtifact(box.left, box.top, box.right, box.bottom, text, imageWidth, imageHeight)

    /**
     * Releases ML Kit resources.
     */
    fun close() {
        if (recognizerDelegate.isInitialized()) {
            recognizer.close()
        }
    }

    companion object {
        private const val TAG = "OcrTextExtractor"
    }
}
