package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.TensorLayout
import org.tensorflow.lite.DataType

/**
 * Centralized configuration parameters for YOLO11n Indian Banknote (Currency) detection.
 *
 * Provides a single source of truth for input/output tensor dimensions, memory layout,
 * model paths, and detection thresholds across Currency Mode.
 *
 * @property modelPath Asset path to the compiled TensorFlow Lite currency model file.
 * @property labelPath Asset path to the class labels text file.
 * @property batchSize Number of images per inference batch (default: 1).
 * @property inputChannels Number of color channels required by the model (default: 3 for RGB).
 * @property inputWidth Width in pixels required by the model input tensor (default: 640).
 * @property inputHeight Height in pixels required by the model input tensor (default: 640).
 * @property inputLayout Tensor spatial and channel memory layout (default: [TensorLayout.NCHW]).
 * @property inputDataType Tensor input primitive data type (default: [DataType.FLOAT32]).
 * @property expectedOutputChannels Expected feature channels in the raw output tensor (4 coords + 6 classes = 10).
 * @property expectedOutputAnchors Expected number of detection anchors (default: 8400).
 * @property confidenceThreshold Minimum confidence score for a candidate box to be retained (default: 0.50f).
 * @property iouThreshold Intersection-over-Union threshold for Non-Maximum Suppression (default: 0.45f).
 * @property maxDetections Maximum number of output detections to return per frame (default: 10).
 * @property numThreads Number of CPU worker threads allocated to the TFLite interpreter (default: 4).
 */
data class CurrencyDetectorConfig(
    val modelPath: String = "models/currency_yolo11n.tflite",
    val labelPath: String = "models/currency_labels.txt",
    val batchSize: Int = 1,
    val inputChannels: Int = 3,
    val inputWidth: Int = 640,
    val inputHeight: Int = 640,
    val inputLayout: TensorLayout = TensorLayout.NCHW,
    val inputDataType: DataType = DataType.FLOAT32,
    val expectedOutputChannels: Int = 10,
    val expectedOutputAnchors: Int = 8400,
    val confidenceThreshold: Float = 0.50f,
    val iouThreshold: Float = 0.45f,
    val maxDetections: Int = 10,
    val numThreads: Int = 4
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
