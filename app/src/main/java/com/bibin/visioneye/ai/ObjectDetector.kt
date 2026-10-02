package com.bibin.visioneye.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * Diagnostic status code for YOLO object detection.
 */
enum class DetectorStatusCode {
    MODEL_READY,
    MODEL_MISSING,
    MODEL_LOAD_ERROR,
    MODEL_SCHEMA_MISMATCH,
    INFERENCE_ERROR,
    INITIALIZING,
    CLOSED
}

/**
 * Lifecycle and health state of the object detection engine.
 *
 * Clearly distinguishes missing model files, load failures, schema mismatches,
 * and inference errors while providing clean user-facing status text and detailed
 * diagnostic information for development.
 */
sealed interface DetectorState {
    val statusCode: DetectorStatusCode
    val userStatusMessage: String
    val diagnosticDetails: String?

    data object Uninitialized : DetectorState {
        override val statusCode = DetectorStatusCode.INITIALIZING
        override val userStatusMessage = "INITIALIZING"
        override val diagnosticDetails = null
    }

    data object Ready : DetectorState {
        override val statusCode = DetectorStatusCode.MODEL_READY
        override val userStatusMessage = "READY"
        override val diagnosticDetails = null
    }

    data class ModelMissing(
        val modelPath: String,
        val details: String = "Place yolov8n.tflite in app/src/main/assets/models/"
    ) : DetectorState {
        override val statusCode = DetectorStatusCode.MODEL_MISSING
        override val userStatusMessage = "MODEL MISSING"
        override val diagnosticDetails = "Asset '$modelPath' not found. $details"
    }

    data class ModelLoadError(
        val message: String,
        val cause: Throwable? = null
    ) : DetectorState {
        override val statusCode = DetectorStatusCode.MODEL_LOAD_ERROR
        override val userStatusMessage = "MODEL LOAD ERROR"
        override val diagnosticDetails = cause?.localizedMessage ?: message
    }

    data class ModelSchemaMismatch(
        val reason: String,
        val details: String
    ) : DetectorState {
        override val statusCode = DetectorStatusCode.MODEL_SCHEMA_MISMATCH
        override val userStatusMessage = "MODEL SCHEMA MISMATCH"
        override val diagnosticDetails = "$reason. $details"
    }

    data class InferenceError(
        val message: String,
        val cause: Throwable? = null
    ) : DetectorState {
        override val statusCode = DetectorStatusCode.INFERENCE_ERROR
        override val userStatusMessage = "INFERENCE ERROR"
        override val diagnosticDetails = cause?.localizedMessage ?: message
    }

    data object Closed : DetectorState {
        override val statusCode = DetectorStatusCode.CLOSED
        override val userStatusMessage = "CLOSED"
        override val diagnosticDetails = null
    }

    // Backward-compatibility variants
    data class ModelNotFound(val modelPath: String, val message: String) : DetectorState {
        override val statusCode = DetectorStatusCode.MODEL_MISSING
        override val userStatusMessage = "MODEL MISSING"
        override val diagnosticDetails = "Asset '$modelPath' not found. $message"
    }

    data class Error(val message: String, val cause: Throwable? = null) : DetectorState {
        override val statusCode = DetectorStatusCode.MODEL_LOAD_ERROR
        override val userStatusMessage = "MODEL LOAD ERROR"
        override val diagnosticDetails = cause?.localizedMessage ?: message
    }
}

/**
 * Architectural contract for on-device object detection in VisionEye.
 *
 * Exposing object detection behind this interface decouples inference models from the
 * camera pipeline and sensor fusion layers, allowing models (e.g. YOLOv8, YOLOv10,
 * quantized TFLite vs FP16) to be swapped without modifying camera or UI components.
 */
interface ObjectDetector : AutoCloseable {
    /**
     * Observable stream of current detector status.
     */
    val state: StateFlow<DetectorState>

    /**
     * Active detector configuration and thresholds.
     */
    val config: DetectorConfig

    /**
     * List of class labels recognized by the loaded model.
     */
    val labels: List<String>

    /**
     * Initializes the underlying runtime and loads the model asset.
     *
     * @return True if model loaded successfully and state is [DetectorState.Ready],
     *         false if model asset is missing or initialization failed.
     */
    fun initialize(): Boolean

    /**
     * Executes object detection on the provided [bitmap] taking into account
     * the camera sensor's [orientationDegrees].
     *
     * Preprocessing, model inference, and NMS post-processing must execute on
     * the calling thread (which must be a background worker thread).
     *
     * @param bitmap Raw image bitmap from the camera frame.
     * @param orientationDegrees Sensor orientation (e.g. 90, 270) to align the image upright.
     * @return List of detected objects with normalized bounding boxes and confidence scores.
     */
    fun detect(bitmap: Bitmap, orientationDegrees: Int): List<Detection>

    /**
     * Releases native interpreter memory and execution delegates.
     */
    override fun close()
}
