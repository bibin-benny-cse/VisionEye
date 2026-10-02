package com.bibin.visioneye.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * Concrete on-device YOLOv8n object detector using the TensorFlow Lite runtime.
 *
 * Implements:
 * - Safe asset loading with clear error states (never crashes if model is missing)
 * - YUV/Bitmap sensor rotation and letterboxing
 * - Reusable direct Float32 buffers to minimize GC allocations during 5 FPS inference
 * - Dynamic decoding supporting both [1, 84, 8400] and [1, 8400, 84] output tensors
 * - Class-aware Non-Maximum Suppression (NMS)
 * - Coordinate mapping back to normalized [0.0, 1.0] image view space
 */
class YoloV8Detector(
    private val context: Context,
    override val config: DetectorConfig = DetectorConfig()
) : ObjectDetector {

    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow<DetectorState>(DetectorState.Uninitialized)
    override val state: StateFlow<DetectorState> = _state.asStateFlow()

    private var interpreter: Interpreter? = null
    private var _labels: List<String> = emptyList()
    override val labels: List<String>
        get() = _labels

    // Cached buffers to avoid heap thrashing during inference
    private var cachedInputBuffer: ByteBuffer? = null
    private var cachedPixelArray: IntArray? = null

    // Shape metadata determined from loaded model
    private var outputShape: IntArray = intArrayOf(1, 84, 8400)
    private var isChannelsFirst: Boolean = true // [1, 84, 8400] vs [1, 8400, 84]
    private var outputDim1: Int = 84
    private var outputDim2: Int = 8400

    private val initLock = Any()

    override fun initialize(): Boolean = synchronized(initLock) {
        if (_state.value is DetectorState.Ready && interpreter != null) {
            return true
        }

        // 1. Load labels
        _labels = loadLabels()

        // 2. Load model buffer from assets
        val modelBuffer: ByteBuffer = try {
            loadModelFile(config.modelPath)
        } catch (e: IOException) {
            val msg = "YOLOv8n model file '${config.modelPath}' not found in assets. " +
                    "Please export yolov8n.tflite and place it in app/src/main/assets/models/. " +
                    "See MODEL_SETUP.md for instructions."
            Log.w(TAG, msg)
            _state.value = DetectorState.ModelMissing(config.modelPath, "Place yolov8n.tflite in app/src/main/assets/models/")
            return false
        } catch (e: Exception) {
            val msg = "Failed to open model asset '${config.modelPath}': ${e.localizedMessage}"
            Log.e(TAG, msg, e)
            _state.value = DetectorState.ModelLoadError(msg, e)
            return false
        }

        // 3. Initialize TFLite interpreter
        return try {
            val options = Interpreter.Options().apply {
                setNumThreads(config.numThreads)
            }
            val interp = Interpreter(modelBuffer, options)

            // 4. Validate model schema before accepting for inference
            val validation = ModelValidator.validate(interp, config)
            if (!validation.isValid) {
                val failureMsg = validation.failureReason ?: "Model tensor schema is incompatible"
                val details = validation.formatSummary()
                Log.e(TAG, "Model validation failed: $failureMsg ($details)")
                interp.close()
                _state.value = DetectorState.ModelSchemaMismatch(failureMsg, details)
                return false
            }

            interpreter = interp
            Log.i(TAG, "YOLOv8 model validated successfully: ${validation.formatSummary()}")

            // Configure output shape parameters from validated tensor
            outputShape = validation.outputShapes[0]
            outputDim1 = outputShape[1]
            outputDim2 = outputShape[2]
            isChannelsFirst = outputDim1 < outputDim2 // [1, 84, 8400] vs [1, 8400, 84]

            // Pre-allocate cached input buffer
            val inputBytes = config.inputByteCount
            cachedInputBuffer = ByteBuffer.allocateDirect(inputBytes).apply {
                order(ByteOrder.nativeOrder())
            }
            cachedPixelArray = IntArray(config.inputWidth * config.inputHeight)

            _state.value = DetectorState.Ready
            Log.i(TAG, "YoloV8Detector successfully initialized and ready for inference.")
            true
        } catch (e: Exception) {
            val msg = "Failed to initialize TFLite interpreter: ${e.localizedMessage}"
            Log.e(TAG, msg, e)
            _state.value = DetectorState.ModelLoadError(msg, e)
            false
        }
    }

    override fun detect(bitmap: Bitmap, orientationDegrees: Int): List<Detection> {
        val currentInterpreter = interpreter
        if (_state.value !is DetectorState.Ready || currentInterpreter == null) {
            return emptyList()
        }

        if (bitmap.isRecycled) {
            Log.w(TAG, "Received recycled bitmap for detection. Skipping.")
            return emptyList()
        }

        val startTime = SystemClock.uptimeMillis()

        return try {
            // 1. Rotate to upright orientation
            val uprightBitmap = ImagePreprocessor.rotateBitmap(bitmap, orientationDegrees)

            // 2. Letterbox to model dimensions (preserving aspect ratio and coordinate transform)
            val (letterboxedBitmap, transform) = ImagePreprocessor.letterbox(
                uprightBitmap = uprightBitmap,
                targetWidth = config.inputWidth,
                targetHeight = config.inputHeight
            )

            // 3. Convert to Float32 ByteBuffer in NCHW planar layout
            val inputBuffer = ImagePreprocessor.bitmapToFloatBuffer(
                bitmap = letterboxedBitmap,
                layout = config.inputLayout,
                targetBuffer = cachedInputBuffer,
                reusablePixelArray = cachedPixelArray
            )

            // 4. Allocate output array matching model dimensions
            val outputArray = Array(1) { Array(outputDim1) { FloatArray(outputDim2) } }

            // 5. Execute on-device inference
            currentInterpreter.run(inputBuffer, outputArray)

            // 6. Decode output tensor into candidate detections
            val candidates = decodeYoloOutput(outputArray[0], transform)

            // 7. Non-Maximum Suppression (NMS)
            val nmsDetections = applyNms(candidates, config.iouThreshold, config.maxDetections)

            val elapsedMs = SystemClock.uptimeMillis() - startTime
            Log.d(TAG, "YOLO inference complete in ${elapsedMs}ms: found ${nmsDetections.size} objects.")

            nmsDetections
        } catch (e: Exception) {
            Log.e(TAG, "Exception during YOLO detection: ${e.localizedMessage}", e)
            _state.value = DetectorState.InferenceError(e.localizedMessage ?: "Inference failed", e)
            emptyList()
        }
    }

    private data class CandidateBox(
        val classId: Int,
        val confidence: Float,
        val box: BoundingBox
    )

    private fun decodeYoloOutput(
        output: Array<FloatArray>,
        transform: LetterboxTransform
    ): List<CandidateBox> {
        val candidates = mutableListOf<CandidateBox>()

        if (isChannelsFirst) {
            // Shape: [84, 8400] -> rows are [cx, cy, w, h, class0...class79]
            val numBoxes = outputDim2
            val numClasses = outputDim1 - 4

            for (b in 0 until numBoxes) {
                var maxScore = 0f
                var maxClassId = -1

                for (c in 0 until numClasses) {
                    val score = output[4 + c][b]
                    if (score > maxScore) {
                        maxScore = score
                        maxClassId = c
                    }
                }

                if (maxScore >= config.confidenceThreshold) {
                    var cx = output[0][b]
                    var cy = output[1][b]
                    var w = output[2][b]
                    var h = output[3][b]

                    // Some exports provide normalized [0..1] coordinates; convert to pixel if needed
                    if (cx <= 1.0f && w <= 1.0f && config.inputWidth > 10) {
                        cx *= config.inputWidth
                        cy *= config.inputHeight
                        w *= config.inputWidth
                        h *= config.inputHeight
                    }

                    val normalizedBox = transform.mapBoxToNormalized(cx, cy, w, h)
                    candidates.add(CandidateBox(maxClassId, maxScore, normalizedBox))
                }
            }
        } else {
            // Shape: [8400, 84] -> rows are box anchors, columns are [cx, cy, w, h, class0...class79]
            val numBoxes = outputDim1
            val numClasses = outputDim2 - 4

            for (b in 0 until numBoxes) {
                var maxScore = 0f
                var maxClassId = -1

                for (c in 0 until numClasses) {
                    val score = output[b][4 + c]
                    if (score > maxScore) {
                        maxScore = score
                        maxClassId = c
                    }
                }

                if (maxScore >= config.confidenceThreshold) {
                    var cx = output[b][0]
                    var cy = output[b][1]
                    var w = output[b][2]
                    var h = output[b][3]

                    if (cx <= 1.0f && w <= 1.0f && config.inputWidth > 10) {
                        cx *= config.inputWidth
                        cy *= config.inputHeight
                        w *= config.inputWidth
                        h *= config.inputHeight
                    }

                    val normalizedBox = transform.mapBoxToNormalized(cx, cy, w, h)
                    candidates.add(CandidateBox(maxClassId, maxScore, normalizedBox))
                }
            }
        }

        return candidates
    }

    private fun applyNms(
        candidates: List<CandidateBox>,
        iouThreshold: Float,
        maxDetections: Int
    ): List<Detection> {
        if (candidates.isEmpty()) return emptyList()

        // Sort descending by confidence score
        val sorted = candidates.sortedByDescending { it.confidence }
        val selected = mutableListOf<CandidateBox>()

        for (candidate in sorted) {
            if (selected.size >= maxDetections) break

            var shouldSelect = true
            for (chosen in selected) {
                // Class-specific NMS
                if (chosen.classId == candidate.classId) {
                    val iou = calculateIoU(chosen.box, candidate.box)
                    if (iou >= iouThreshold) {
                        shouldSelect = false
                        break
                    }
                }
            }

            if (shouldSelect) {
                selected.add(candidate)
            }
        }

        val now = System.currentTimeMillis()
        return selected.map { box ->
            Detection(
                classId = box.classId,
                label = _labels.getOrElse(box.classId) { "class_${box.classId}" },
                confidence = box.confidence,
                boundingBox = box.box,
                position = HorizontalPosition.fromNormalizedX(
                    normalizedX = box.box.centerX,
                    leftBoundary = config.positionLeftBoundary,
                    rightBoundary = config.positionRightBoundary
                ),
                timestampMs = now
            )
        }
    }

    private fun calculateIoU(a: BoundingBox, b: BoundingBox): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    private fun loadModelFile(path: String): ByteBuffer {
        val fileDescriptor = appContext.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(): List<String> {
        val loaded = mutableListOf<String>()
        try {
            appContext.assets.open(config.labelPath).use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            loaded.add(trimmed)
                        }
                        line = reader.readLine()
                    }
                }
            }
            if (loaded.isNotEmpty()) {
                Log.d(TAG, "Loaded ${loaded.size} class labels from ${config.labelPath}")
                return loaded
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load labels from ${config.labelPath}, using default COCO 80 labels.")
        }
        return DEFAULT_COCO_LABELS
    }

    override fun close() {
        synchronized(initLock) {
            try {
                interpreter?.close()
                interpreter = null
                cachedInputBuffer = null
                cachedPixelArray = null
                _state.value = DetectorState.Closed
                Log.i(TAG, "YoloV8Detector closed and resources released.")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing TFLite interpreter: ${e.localizedMessage}", e)
            }
        }
    }

    companion object {
        private const val TAG = "YoloV8Detector"

        val DEFAULT_COCO_LABELS = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
            "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    }
}
