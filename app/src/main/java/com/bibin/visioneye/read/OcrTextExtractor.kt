package com.bibin.visioneye.read

import android.graphics.Bitmap
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
class OcrTextExtractor(
    private val readingOrderOrganizer: ReadingOrderOrganizer = ReadingOrderOrganizer()
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts English text from the rectified [bitmap], arranged in natural reading order.
     *
     * @param bitmap Upright, perspective-corrected document bitmap.
     * @return Natural reading-order text string, or empty string if no text recognized.
     */
    suspend fun extractText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val lines = mutableListOf<RecognizedLine>()

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox
                        if (box != null) {
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

                val orderedText = readingOrderOrganizer.organize(lines)
                continuation.resume(orderedText)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    /**
     * Releases ML Kit resources.
     */
    fun close() {
        recognizer.close()
    }
}
