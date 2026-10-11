package com.bibin.visioneye.read

import android.graphics.Bitmap

/**
 * Diagnostic metrics for a single OCR pass.
 *
 * Excludes sensitive document content from logs while providing observability into OCR health.
 */
data class OcrDiagnostics(
    val blockCount: Int = 0,
    val lineCount: Int = 0,
    val totalCharCount: Int = 0,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val durationMs: Long = 0L,
    val suspiciousSingleCharRatio: Float = 0.0f
)

/**
 * On-device in-memory diagnostic inspection store for VisionEye READ Mode.
 *
 * Preserves the most recent pipeline state (captured bitmap, rectified bitmap,
 * raw OCR text, organized text, and TTS chunks) in memory for debugging and testing.
 * Excludes document content from production logcat to safeguard user privacy.
 */
object ReadDebugStore {
    var lastCapturedBitmap: Bitmap? = null
        internal set

    var lastRectifiedBitmap: Bitmap? = null
        internal set

    var lastRawOcrText: String? = null
        internal set

    var lastOrganizedText: String? = null
        internal set

    var lastSpeechChunks: List<String> = emptyList()
        internal set

    var lastDiagnostics: OcrDiagnostics? = null
        internal set

    fun reset() {
        lastCapturedBitmap = null
        lastRectifiedBitmap = null
        lastRawOcrText = null
        lastOrganizedText = null
        lastSpeechChunks = emptyList()
        lastDiagnostics = null
    }

    /**
     * Produces a safe, non-sensitive string summary of the most recent OCR pass.
     */
    fun safeSummary(): String {
        val diag = lastDiagnostics ?: return "No OCR diagnostics available"
        return "Image: ${diag.imageWidth}x${diag.imageHeight}, " +
                "Blocks: ${diag.blockCount}, Lines: ${diag.lineCount}, " +
                "Chars: ${diag.totalCharCount}, " +
                "SingleCharRatio: ${String.format(java.util.Locale.US, "%.1f", diag.suspiciousSingleCharRatio * 100)}%, " +
                "Duration: ${diag.durationMs}ms, Chunks: ${lastSpeechChunks.size}"
    }
}
