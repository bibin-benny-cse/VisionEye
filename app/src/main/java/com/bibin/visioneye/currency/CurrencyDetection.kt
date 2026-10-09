package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.HorizontalPosition

/**
 * Immutable representation of an Indian Banknote detected by [CurrencyDetector].
 *
 * @property denomination The recognized banknote denomination (₹10, ₹20, ₹50, ₹100, ₹200, ₹500).
 * @property confidence Prediction score between 0.0f and 1.0f.
 * @property boundingBox Normalized bounding box coordinates within the camera view [0.0, 1.0].
 * @property position Horizontal sector (LEFT, CENTER, RIGHT).
 * @property timestampMs Epoch timestamp in milliseconds when this detection occurred.
 */
data class CurrencyDetection(
    val denomination: CurrencyDenomination,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val position: HorizontalPosition = HorizontalPosition.fromNormalizedX(boundingBox.centerX),
    val timestampMs: Long = System.currentTimeMillis()
) {
    val classId: Int get() = denomination.classId
    val label: String get() = denomination.label
    val spokenName: String get() = denomination.spokenName

    /**
     * Converts to generic [Detection] for UI overlays and visualization.
     */
    fun toGenericDetection(): Detection = Detection(
        classId = denomination.classId,
        label = "₹${denomination.numericValue}",
        confidence = confidence,
        boundingBox = boundingBox,
        position = position,
        timestampMs = timestampMs
    )
}
