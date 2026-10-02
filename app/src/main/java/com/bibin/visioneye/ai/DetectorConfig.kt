package com.bibin.visioneye.ai

import org.tensorflow.lite.DataType

/**
 * Memory ordering of spatial and channel dimensions for input/output tensors.
 */
enum class TensorLayout {
    /** Channels-first planar memory layout: [Batch, Channels, Height, Width] */
    NCHW,
    /** Channels-last interleaved memory layout: [Batch, Height, Width, Channels] */
    NHWC
}

/**
 * Centralized configuration parameters for YOLOv8 object detection.
 *
 * Provides a single source of truth for input/output tensor dimensions, memory layout,
 * model paths, and detection thresholds across VisionEye.
 *
 * @property modelPath Asset path to the compiled TensorFlow Lite model file.
 * @property labelPath Asset path to the newline-delimited class labels text file.
 * @property batchSize Number of images per inference batch (default: 1).
 * @property inputChannels Number of color channels required by the model (default: 3 for RGB).
 * @property inputWidth Width in pixels required by the model input tensor (default: 640).
 * @property inputHeight Height in pixels required by the model input tensor (default: 640).
 * @property inputLayout Tensor spatial and channel memory layout (default: [TensorLayout.NCHW]).
 * @property inputDataType Tensor input primitive data type (default: [DataType.FLOAT32]).
 * @property confidenceThreshold Minimum confidence score for a candidate box to be retained (default: 0.40f).
 * @property iouThreshold Intersection-over-Union threshold for Non-Maximum Suppression (default: 0.45f).
 * @property maxDetections Maximum number of output detections to return per frame (default: 20).
 * @property numThreads Number of CPU worker threads allocated to the TFLite interpreter (default: 4).
 * @property positionLeftBoundary Upper bound for the LEFT horizontal sector in [0.0, 1.0] (default: 0.33f).
 * @property positionRightBoundary Lower bound for the RIGHT horizontal sector in [0.0, 1.0] (default: 0.67f).
 * @property isDebugOverlayEnabled Whether development-only detection visualization overlay is enabled (default: true).
 */
data class DetectorConfig(
    val modelPath: String = "models/yolov8n.tflite",
    val labelPath: String = "models/coco_labels.txt",
    val batchSize: Int = 1,
    val inputChannels: Int = 3,
    val inputWidth: Int = 640,
    val inputHeight: Int = 640,
    val inputLayout: TensorLayout = TensorLayout.NCHW,
    val inputDataType: DataType = DataType.FLOAT32,
    val confidenceThreshold: Float = 0.40f,
    val iouThreshold: Float = 0.45f,
    val maxDetections: Int = 20,
    val numThreads: Int = 4,
    val positionLeftBoundary: Float = 0.33f,
    val positionRightBoundary: Float = 0.67f,
    val isDebugOverlayEnabled: Boolean = true
) {
    /**
     * Total number of elements in the flattened input tensor.
     */
    val inputElementCount: Int get() = batchSize * inputChannels * inputHeight * inputWidth

    /**
     * Total number of bytes allocated for the direct Float32 input ByteBuffer.
     */
    val inputByteCount: Int get() = inputElementCount * 4
}
