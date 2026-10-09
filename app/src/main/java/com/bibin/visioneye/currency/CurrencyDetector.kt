package com.bibin.visioneye.currency

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.DetectorState
import com.bibin.visioneye.ai.HorizontalPosition
import com.bibin.visioneye.ai.ImagePreprocessor
import com.bibin.visioneye.ai.LetterboxTransform
import com.bibin.visioneye.ai.ObjectDetector
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
 * On-device Indian Banknote (Currency) Detector using the TensorFlow Lite runtime.
 *
 * Dedicated pipeline for the YOLO11n 6-class currency model:
 * - Independent from the NAVIGATE YOLOv8n detector.
 * - Safely loads and memory-maps `currency_yolo11n.tflite`.
 * - Validates model schema against [CurrencyModelValidator].
 * - Reuses [ImagePreprocessor] for sensor rotation, letterboxing, and NCHW tensor conversion.
 * - Decodes 8400 anchors across 6 currency classes.
 * - Enforces Non-Maximum Suppression (NMS) preventing competing class predictions for the same physical note
 *   while preserving genuinely distinct overlapping banknotes.
 */
/**
 * Architectural interface for on-device Indian banknote detection.
 */
interface CurrencyBanknoteDetector : ObjectDetector {
    val currencyConfig: CurrencyDetectorConfig
    fun detectCurrency(bitmap: Bitmap, orientationDegrees: Int): List<CurrencyDetection>
}

class CurrencyDetector(
    private val context: Context,
    override val currencyConfig: CurrencyDetectorConfig = CurrencyDetectorConfig()
) : CurrencyBanknoteDetector {

    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow<DetectorState>(DetectorState.Uninitialized)
    override val state: StateFlow<DetectorState> = _state.asStateFlow()

    override val config = com.bibin.visioneye.ai.DetectorConfig(
        modelPath = currencyConfig.modelPath,
        labelPath = currencyConfig.labelPath,
        confidenceThreshold = currencyConfig.confidenceThreshold,
        iouThreshold = currencyConfig.iouThreshold,
        maxDetections = currencyConfig.maxDetections,
        numThreads = currencyConfig.numThreads
    )

    private var interpreter: Interpreter? = null
    private var _labels: List<String> = emptyList()
    override val labels: List<String>
        get() = _labels

    // Cached direct Float32 buffers to minimize per-frame GC allocations
    private var cachedInputBuffer: ByteBuffer? = null
    private var cachedPixelArray: IntArray? = null

    // Shape metadata determined from validated model
    private var isChannelsFirst: Boolean = true
    private var outputDim1: Int = 10
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
            loadModelFile(currencyConfig.modelPath)
        } catch (e: IOException) {
            val msg = "Currency model file '${currencyConfig.modelPath}' not found in assets. " +
                    "Please place currency_yolo11n.tflite in app/src/main/assets/models/."
            Log.w(TAG, msg)
            _state.value = DetectorState.ModelMissing(currencyConfig.modelPath, "Place currency_yolo11n.tflite in assets/models/")
            return false
        } catch (e: Exception) {
            val msg = "Failed to open currency model asset '${currencyConfig.modelPath}': ${e.localizedMessage}"
            Log.e(TAG, msg, e)
            _state.value = DetectorState.ModelLoadError(msg, e)
            return false
        }

        // 3. Initialize TFLite interpreter
        return try {
            val options = Interpreter.Options().apply {
                setNumThreads(currencyConfig.numThreads)
            }
            val interp = Interpreter(modelBuffer, options)

            // 4. Validate currency model schema
            val validation = CurrencyModelValidator.validate(interp, currencyConfig)
            if (!validation.isValid) {
                val failureMsg = validation.failureReason ?: "Currency model schema mismatch"
                val details = validation.formatSummary()
                Log.e(TAG, "Currency model validation failed: $failureMsg ($details)")
                interp.close()
                _state.value = DetectorState.ModelSchemaMismatch(failureMsg, details)
                return false
            }

            interpreter = interp
            Log.i(TAG, "Currency model validated successfully: ${validation.formatSummary()}")

            // Configure output shape parameters from validated tensor
            val outputShape = validation.outputShapes[0]
            outputDim1 = outputShape[1]
            outputDim2 = outputShape[2]
            isChannelsFirst = outputDim1 < outputDim2 // [1, 10, 8400] vs [1, 8400, 10]

            // Pre-allocate cached direct input buffer (1 * 3 * 640 * 640 * 4 bytes)
            val inputBytes = currencyConfig.inputByteCount
            cachedInputBuffer = ByteBuffer.allocateDirect(inputBytes).apply {
                order(ByteOrder.nativeOrder())
            }
            cachedPixelArray = IntArray(currencyConfig.inputWidth * currencyConfig.inputHeight)

            _state.value = DetectorState.Ready
            Log.i(TAG, "CurrencyDetector successfully initialized and ready for inference.")
            true
        } catch (e: Exception) {
            val msg = "Failed to initialize TFLite interpreter for currency: ${e.localizedMessage}"
            Log.e(TAG, msg, e)
            _state.value = DetectorState.ModelLoadError(msg, e)
            false
        }
    }

    /**
     * Executes currency banknote detection on [bitmap], taking camera sensor orientation into account.
     */
    override fun detectCurrency(bitmap: Bitmap, orientationDegrees: Int): List<CurrencyDetection> {
        val currentInterpreter = interpreter
        if (_state.value !is DetectorState.Ready || currentInterpreter == null) {
            return emptyList()
        }

        if (bitmap.isRecycled) {
            Log.w(TAG, "Received recycled bitmap for currency detection. Skipping.")
            return emptyList()
        }

        val startTime = SystemClock.uptimeMillis()

        return try {
            // 1. Rotate to upright orientation
            val uprightBitmap = ImagePreprocessor.rotateBitmap(bitmap, orientationDegrees)

            // 2. Letterbox to 640x640 with aspect preservation
            val (letterboxedBitmap, transform) = ImagePreprocessor.letterbox(
                uprightBitmap = uprightBitmap,
                targetWidth = currencyConfig.inputWidth,
                targetHeight = currencyConfig.inputHeight
            )

            // 3. Convert to Float32 ByteBuffer in NCHW planar layout
            val inputBuffer = ImagePreprocessor.bitmapToFloatBuffer(
                bitmap = letterboxedBitmap,
                layout = currencyConfig.inputLayout,
                targetBuffer = cachedInputBuffer,
                reusablePixelArray = cachedPixelArray
            )

            // 4. Output tensor buffer: [1, outputDim1, outputDim2]
            val outputArray = Array(1) { Array(outputDim1) { FloatArray(outputDim2) } }

            // 5. Execute on-device inference
            currentInterpreter.run(inputBuffer, outputArray)

            // 6. Decode output tensor into banknote candidates
            val candidates = decodeCurrencyOutput(outputArray[0], transform)

            // 7. Non-Maximum Suppression preventing competing class predictions on same note
            val nmsDetections = applyNms(candidates, currencyConfig.iouThreshold, currencyConfig.maxDetections)

            val elapsedMs = SystemClock.uptimeMillis() - startTime
            Log.d(TAG, "Currency inference complete in ${elapsedMs}ms: found ${nmsDetections.size} notes.")

            nmsDetections
        } catch (e: Exception) {
            Log.e(TAG, "Exception during currency detection: ${e.localizedMessage}", e)
            _state.value = DetectorState.InferenceError(e.localizedMessage ?: "Currency inference failed", e)
            emptyList()
        }
    }

    override fun detect(bitmap: Bitmap, orientationDegrees: Int): List<Detection> {
        return detectCurrency(bitmap, orientationDegrees).map { it.toGenericDetection() }
    }

    private fun decodeCurrencyOutput(
        output: Array<FloatArray>,
        transform: LetterboxTransform
    ): List<CandidateNote> {
        return CurrencyPostProcessor.decodeCurrencyOutput(
            output = output,
            transform = transform,
            isChannelsFirst = isChannelsFirst,
            outputDim1 = outputDim1,
            outputDim2 = outputDim2,
            config = currencyConfig
        )
    }

    private fun applyNms(
        candidates: List<CandidateNote>,
        iouThreshold: Float,
        maxDetections: Int
    ): List<CurrencyDetection> {
        return CurrencyPostProcessor.applyNms(
            candidates = candidates,
            iouThreshold = iouThreshold,
            maxDetections = maxDetections
        )
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
            appContext.assets.open(currencyConfig.labelPath).use { stream ->
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
                Log.d(TAG, "Loaded ${loaded.size} class labels from ${currencyConfig.labelPath}")
                return loaded
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load labels from ${currencyConfig.labelPath}, using default 6 currency labels.")
        }
        return DEFAULT_CURRENCY_LABELS
    }

    override fun close() {
        synchronized(initLock) {
            try {
                interpreter?.close()
                interpreter = null
                cachedInputBuffer = null
                cachedPixelArray = null
                _state.value = DetectorState.Closed
                Log.i(TAG, "CurrencyDetector closed and resources released.")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing currency TFLite interpreter: ${e.localizedMessage}", e)
            }
        }
    }

    companion object {
        private const val TAG = "CurrencyDetector"

        val DEFAULT_CURRENCY_LABELS = listOf("10", "100", "20", "200", "50", "500")
    }
}
