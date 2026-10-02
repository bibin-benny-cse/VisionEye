package com.bibin.visioneye.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tensorflow.lite.DataType

class ObjectDetectorTest {

    @Test
    fun detectorConfig_defaultsMatchActualModelContract() {
        val config = DetectorConfig()

        assertEquals("models/yolov8n.tflite", config.modelPath)
        assertEquals("models/coco_labels.txt", config.labelPath)
        assertEquals(1, config.batchSize)
        assertEquals(3, config.inputChannels)
        assertEquals(640, config.inputWidth)
        assertEquals(640, config.inputHeight)
        assertEquals(TensorLayout.NCHW, config.inputLayout)
        assertEquals(DataType.FLOAT32, config.inputDataType)
        assertEquals(0.40f, config.confidenceThreshold, 0.001f)
        assertEquals(0.45f, config.iouThreshold, 0.001f)
        assertEquals(20, config.maxDetections)
        assertEquals(4, config.numThreads)
        assertEquals(0.33f, config.positionLeftBoundary, 0.001f)
        assertEquals(0.67f, config.positionRightBoundary, 0.001f)
        assertTrue(config.isDebugOverlayEnabled)

        // Verify total byte count: 1 * 3 * 640 * 640 * 4 bytes = 4,915,200 bytes
        assertEquals(1 * 3 * 640 * 640, config.inputElementCount)
        assertEquals(1 * 3 * 640 * 640 * 4, config.inputByteCount)
    }

    @Test
    fun boundingBox_geometryCalculations() {
        val box = BoundingBox(left = 0.1f, top = 0.2f, right = 0.7f, bottom = 0.8f)

        assertEquals(0.6f, box.width, 0.001f)
        assertEquals(0.6f, box.height, 0.001f)
        assertEquals(0.4f, box.centerX, 0.001f)
        assertEquals(0.5f, box.centerY, 0.001f)
    }

    @Test
    fun detection_immutableProperties() {
        val box = BoundingBox(left = 0.1f, top = 0.2f, right = 0.5f, bottom = 0.8f)
        val detection = Detection(
            classId = 0,
            label = "person",
            confidence = 0.92f,
            boundingBox = box,
            timestampMs = 123456789L
        )

        assertEquals(0, detection.classId)
        assertEquals("person", detection.label)
        assertEquals("person", detection.className)
        assertEquals(0.92f, detection.confidence, 0.001f)
        assertEquals(box, detection.boundingBox)
        assertEquals(0.3f, detection.normalizedCenterX, 0.001f)
        assertEquals(HorizontalPosition.LEFT, detection.position)
        assertEquals(123456789L, detection.timestampMs)
    }

    // --- MILESTONE 4: DETECTION POSITION & VISUALIZATION TESTS ---

    @Test
    fun milestone4_testA_centerXAtApproximately0_10_resolvesToLeft() {
        // Direct resolver check
        val position = HorizontalPosition.fromNormalizedX(0.10f)
        assertEquals(HorizontalPosition.LEFT, position)

        // Bounding box centered at 0.10f (e.g. left=0.05, right=0.15)
        val box = BoundingBox(left = 0.05f, top = 0.2f, right = 0.15f, bottom = 0.8f)
        assertEquals(0.10f, box.centerX, 0.001f)

        val detection = Detection(
            classId = 56,
            label = "chair",
            confidence = 0.85f,
            boundingBox = box
        )
        assertEquals(0.10f, detection.normalizedCenterX, 0.001f)
        assertEquals(HorizontalPosition.LEFT, detection.position)
    }

    @Test
    fun milestone4_testB_centerXAtApproximately0_50_resolvesToCenter() {
        // Direct resolver check
        val position = HorizontalPosition.fromNormalizedX(0.50f)
        assertEquals(HorizontalPosition.CENTER, position)

        // Bounding box centered at 0.50f (e.g. left=0.40, right=0.60)
        val box = BoundingBox(left = 0.40f, top = 0.1f, right = 0.60f, bottom = 0.9f)
        assertEquals(0.50f, box.centerX, 0.001f)

        val detection = Detection(
            classId = 0,
            label = "person",
            confidence = 0.91f,
            boundingBox = box
        )
        assertEquals(0.50f, detection.normalizedCenterX, 0.001f)
        assertEquals(HorizontalPosition.CENTER, detection.position)
    }

    @Test
    fun milestone4_testC_centerXAtApproximately0_90_resolvesToRight() {
        // Direct resolver check
        val position = HorizontalPosition.fromNormalizedX(0.90f)
        assertEquals(HorizontalPosition.RIGHT, position)

        // Bounding box centered at 0.90f (e.g. left=0.85, right=0.95)
        val box = BoundingBox(left = 0.85f, top = 0.3f, right = 0.95f, bottom = 0.7f)
        assertEquals(0.90f, box.centerX, 0.001f)

        val detection = Detection(
            classId = 72,
            label = "refrigerator",
            confidence = 0.80f,
            boundingBox = box
        )
        assertEquals(0.90f, detection.normalizedCenterX, 0.001f)
        assertEquals(HorizontalPosition.RIGHT, detection.position)
    }

    @Test
    fun milestone4_testD_exactThresholdBehaviorIsDeterministic() {
        // Default boundaries: LEFT < 0.33f <= CENTER < 0.67f <= RIGHT
        assertEquals(HorizontalPosition.LEFT, HorizontalPosition.fromNormalizedX(0.00f))
        assertEquals(HorizontalPosition.LEFT, HorizontalPosition.fromNormalizedX(0.3299f))
        assertEquals(HorizontalPosition.CENTER, HorizontalPosition.fromNormalizedX(0.3300f))
        assertEquals(HorizontalPosition.CENTER, HorizontalPosition.fromNormalizedX(0.50f))
        assertEquals(HorizontalPosition.CENTER, HorizontalPosition.fromNormalizedX(0.6699f))
        assertEquals(HorizontalPosition.RIGHT, HorizontalPosition.fromNormalizedX(0.6700f))
        assertEquals(HorizontalPosition.RIGHT, HorizontalPosition.fromNormalizedX(1.00f))

        // Custom boundary test
        val customLeft = 0.40f
        val customRight = 0.60f
        assertEquals(HorizontalPosition.LEFT, HorizontalPosition.fromNormalizedX(0.39f, customLeft, customRight))
        assertEquals(HorizontalPosition.CENTER, HorizontalPosition.fromNormalizedX(0.40f, customLeft, customRight))
        assertEquals(HorizontalPosition.CENTER, HorizontalPosition.fromNormalizedX(0.59f, customLeft, customRight))
        assertEquals(HorizontalPosition.RIGHT, HorizontalPosition.fromNormalizedX(0.60f, customLeft, customRight))
    }

    @Test
    fun milestone4_testE_detectionRetainsCorrectClassConfidenceAndPosition() {
        val testCases = listOf(
            Triple("refrigerator", 0.80f, BoundingBox(0.80f, 0.1f, 0.96f, 0.9f)), // centerX = 0.88 -> RIGHT
            Triple("bed", 0.73f, BoundingBox(0.02f, 0.2f, 0.26f, 0.8f)),          // centerX = 0.14 -> LEFT
            Triple("handbag", 0.50f, BoundingBox(0.45f, 0.4f, 0.55f, 0.6f))        // centerX = 0.50 -> CENTER
        )

        val detections = testCases.mapIndexed { index, (label, conf, box) ->
            Detection(
                classId = index,
                label = label,
                confidence = conf,
                boundingBox = box
            )
        }

        // 1. Refrigerator
        val det0 = detections[0]
        assertEquals("refrigerator", det0.className)
        assertEquals("refrigerator", det0.label)
        assertEquals(0.80f, det0.confidence, 0.001f)
        assertEquals(0.88f, det0.normalizedCenterX, 0.001f)
        assertEquals(HorizontalPosition.RIGHT, det0.position)

        // 2. Bed
        val det1 = detections[1]
        assertEquals("bed", det1.className)
        assertEquals("bed", det1.label)
        assertEquals(0.73f, det1.confidence, 0.001f)
        assertEquals(0.14f, det1.normalizedCenterX, 0.001f)
        assertEquals(HorizontalPosition.LEFT, det1.position)

        // 3. Handbag
        val det2 = detections[2]
        assertEquals("handbag", det2.className)
        assertEquals("handbag", det2.label)
        assertEquals(0.50f, det2.confidence, 0.001f)
        assertEquals(0.50f, det2.normalizedCenterX, 0.001f)
        assertEquals(HorizontalPosition.CENTER, det2.position)
    }

    @Test
    fun milestone4_testF_letterboxTransformedCoordinatesProduceCorrectHorizontalPosition() {
        // Upright camera frame: 480x640 letterboxed into 640x640 model input
        // scale = 1.0f, padX = 80f, padY = 0f
        val transform = LetterboxTransform(
            scale = 1.0f,
            padX = 80f,
            padY = 0f,
            originalWidth = 480,
            originalHeight = 640,
            targetWidth = 640,
            targetHeight = 640
        )

        // Scenario 1: Object at model center (cx=320, cy=320, w=100, h=100)
        // In original image: (320 - 80) / 480 = 240 / 480 = 0.50f -> CENTER
        val centerBox = transform.mapBoxToNormalized(cx = 320f, cy = 320f, width = 100f, height = 100f)
        assertEquals(0.50f, centerBox.centerX, 0.001f)
        assertEquals(HorizontalPosition.CENTER, HorizontalPosition.fromNormalizedX(centerBox.centerX))

        // Scenario 2: Object at model cx = 220f
        // If uncorrected model-space is mistakenly used: 220 / 640 = 0.34375f (> 0.33, incorrectly labeled CENTER)
        // Correct unletterbox transform: (220 - 80) / 480 = 140 / 480 = 0.2917f (< 0.33, correctly labeled LEFT)
        val leftBox = transform.mapBoxToNormalized(cx = 220f, cy = 320f, width = 60f, height = 60f)
        assertEquals(140f / 480f, leftBox.centerX, 0.001f)
        assertTrue("Normalized center must be < 0.33", leftBox.centerX < 0.33f)
        assertEquals(HorizontalPosition.LEFT, HorizontalPosition.fromNormalizedX(leftBox.centerX))

        // Scenario 3: Object at model cx = 420f
        // If uncorrected model-space is mistakenly used: 420 / 640 = 0.65625f (< 0.67, incorrectly labeled CENTER)
        // Correct unletterbox transform: (420 - 80) / 480 = 340 / 480 = 0.7083f (> 0.67, correctly labeled RIGHT)
        val rightBox = transform.mapBoxToNormalized(cx = 420f, cy = 320f, width = 60f, height = 60f)
        assertEquals(340f / 480f, rightBox.centerX, 0.001f)
        assertTrue("Normalized center must be >= 0.67", rightBox.centerX >= 0.67f)
        assertEquals(HorizontalPosition.RIGHT, HorizontalPosition.fromNormalizedX(rightBox.centerX))
    }

    @Test
    fun milestone4_testG_previewCoordinateMapper_preservesSemanticPositionAndGeometry() {
        val viewWidth = 1080f
        val viewHeight = 2400f
        val streamWidth = 480f
        val streamHeight = 640f

        val boxLeft = BoundingBox(left = 0.05f, top = 0.2f, right = 0.15f, bottom = 0.4f)   // centerX = 0.10f -> LEFT
        val boxCenter = BoundingBox(left = 0.45f, top = 0.2f, right = 0.55f, bottom = 0.4f) // centerX = 0.50f -> CENTER
        val boxRight = BoundingBox(left = 0.85f, top = 0.2f, right = 0.95f, bottom = 0.4f)  // centerX = 0.90f -> RIGHT

        val rectLeft = PreviewCoordinateMapper.mapBoxToView(boxLeft, viewWidth, viewHeight, streamWidth, streamHeight)
        val rectCenter = PreviewCoordinateMapper.mapBoxToView(boxCenter, viewWidth, viewHeight, streamWidth, streamHeight)
        val rectRight = PreviewCoordinateMapper.mapBoxToView(boxRight, viewWidth, viewHeight, streamWidth, streamHeight)

        // 1. Strict relative horizontal order preservation
        assertTrue(rectLeft.centerX < rectCenter.centerX)
        assertTrue(rectCenter.centerX < rectRight.centerX)

        // 2. Exact visual centering on screen: center of stream (0.50f) aligns with center of view (1080 / 2 = 540)
        assertEquals(viewWidth / 2f, rectCenter.centerX, 0.001f)

        // 3. Symmetry preservation: distance from screen center to left and right boxes must match
        val distLeft = rectCenter.centerX - rectLeft.centerX
        val distRight = rectRight.centerX - rectCenter.centerX
        assertEquals(distLeft, distRight, 0.001f)

        // 4. Uniform scaling: width and height scale by the exact same scale factor (no stretching)
        // Stream aspect = 480/640 = 0.75, View aspect = 1080/2400 = 0.45 (View is taller, so stream scales to fill height)
        // Scale = 2400 / 640 = 3.75
        val expectedBoxWidth = boxCenter.width * (streamWidth * (viewHeight / streamHeight))
        val expectedBoxHeight = boxCenter.height * (streamHeight * (viewHeight / streamHeight))
        assertEquals(expectedBoxWidth, rectCenter.width, 0.001f)
        assertEquals(expectedBoxHeight, rectCenter.height, 0.001f)

        // 5. Handling raw unrotated buffer dimensions (640, 480) yields identical mapping in portrait view
        val rectFromUnrotated = PreviewCoordinateMapper.mapBoxToView(boxCenter, viewWidth, viewHeight, 640f, 480f)
        assertEquals(rectCenter.left, rectFromUnrotated.left, 0.001f)
        assertEquals(rectCenter.top, rectFromUnrotated.top, 0.001f)
    }

    @Test
    fun detectorStates_verifyTypeHierarchyAndStatusCodes() {
        val uninit: DetectorState = DetectorState.Uninitialized
        val ready: DetectorState = DetectorState.Ready
        val missing: DetectorState = DetectorState.ModelMissing("models/yolov8n.tflite", "File missing")
        val loadError: DetectorState = DetectorState.ModelLoadError("Init failed")
        val schemaMismatch: DetectorState = DetectorState.ModelSchemaMismatch("Embedded NMS detected", "Found 4 outputs")
        val inferenceError: DetectorState = DetectorState.InferenceError("Buffer overflow")
        val closed: DetectorState = DetectorState.Closed

        assertEquals(DetectorStatusCode.INITIALIZING, uninit.statusCode)
        assertEquals(DetectorStatusCode.MODEL_READY, ready.statusCode)
        assertEquals(DetectorStatusCode.MODEL_MISSING, missing.statusCode)
        assertEquals(DetectorStatusCode.MODEL_LOAD_ERROR, loadError.statusCode)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, schemaMismatch.statusCode)
        assertEquals(DetectorStatusCode.INFERENCE_ERROR, inferenceError.statusCode)
        assertEquals(DetectorStatusCode.CLOSED, closed.statusCode)

        assertEquals("MODEL MISSING", missing.userStatusMessage)
        assertEquals("MODEL LOAD ERROR", loadError.userStatusMessage)
        assertEquals("MODEL SCHEMA MISMATCH", schemaMismatch.userStatusMessage)
        assertEquals("INFERENCE ERROR", inferenceError.userStatusMessage)
    }

    @Test
    fun modelValidator_validNCHWInputSchema_accepted() {
        val config = DetectorConfig()
        // Actual model contract: [1, 3, 640, 640] FLOAT32 -> [1, 84, 8400] FLOAT32
        val result = ModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 640, 640)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 84, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = config
        )

        assertTrue(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_READY, result.statusCode)
        assertEquals(1, result.inputTensorCount)
        assertEquals(1, result.outputTensorCount)
    }

    @Test
    fun modelValidator_invalidNHWCInputSchema_rejected() {
        val config = DetectorConfig()
        // Previously assumed NHWC schema [1, 640, 640, 3] must now be rejected
        val result = ModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 640, 640, 3)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 84, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = config
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertNotNull(result.failureReason)
        assertTrue(result.failureReason!!.contains("NHWC"))
    }

    @Test
    fun modelValidator_embeddedNmsExport_failsSchemaCheck() {
        val config = DetectorConfig()
        // Embedded NMS exports produce 4 output tensors: boxes, classes, scores, count
        val result = ModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 640, 640)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(
                intArrayOf(1, 300, 4),
                intArrayOf(1, 300),
                intArrayOf(1, 300),
                intArrayOf(1)
            ),
            outputTypes = listOf(
                DataType.FLOAT32,
                DataType.FLOAT32,
                DataType.FLOAT32,
                DataType.FLOAT32
            ),
            config = config
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertNotNull(result.failureReason)
        assertTrue(result.failureReason!!.contains("embedded NMS"))
    }

    @Test
    fun modelValidator_mismatchedInputResolution_failsSchemaCheck() {
        val config = DetectorConfig(inputWidth = 640, inputHeight = 640)
        val result = ModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 320, 320)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 84, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = config
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertTrue(result.failureReason!!.contains("Input shape"))
    }

    @Test
    fun modelValidator_invalidDatatype_failsSchemaCheck() {
        val config = DetectorConfig()
        val result = ModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 640, 640)),
            inputTypes = listOf(DataType.UINT8),
            outputShapes = listOf(intArrayOf(1, 84, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = config
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertTrue(result.failureReason!!.contains("datatype"))
    }

    @Test
    fun modelValidator_incorrectOutputFeatures_failsSchemaCheck() {
        val config = DetectorConfig()
        // Output tensor lacks 84 features (e.g. only 20 features)
        val result = ModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 640, 640)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 20, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = config
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertTrue(result.failureReason!!.contains("Output shape"))
    }

    @Test
    fun imagePreprocessor_planarNCHWChannelPackingAndRGBOrder() {
        // Construct 2x2 synthetic pixel array:
        // Pixel 0 (0,0): Pure Red   (A=255, R=255, G=0,   B=0)
        // Pixel 1 (0,1): Pure Green (A=255, R=0,   G=255, B=0)
        // Pixel 2 (1,0): Pure Blue  (A=255, R=0,   G=0,   B=255)
        // Pixel 3 (1,1): White      (A=255, R=255, G=255, B=255)
        val pixels = intArrayOf(
            0xFFFF0000.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt(),
            0xFFFFFFFF.toInt()
        )

        val buffer = ImagePreprocessor.packPixelsToFloatBuffer(
            pixels = pixels,
            width = 2,
            height = 2,
            layout = TensorLayout.NCHW
        )

        // Total floats: 1 * 3 * 2 * 2 = 12 floats
        val floats = FloatArray(12)
        buffer.asFloatBuffer().get(floats)

        // Channel 0: All 4 Red floats
        assertEquals(1.0f, floats[0], 0.001f) // Pixel 0 Red = 255 -> 1.0f
        assertEquals(0.0f, floats[1], 0.001f) // Pixel 1 Red = 0   -> 0.0f
        assertEquals(0.0f, floats[2], 0.001f) // Pixel 2 Red = 0   -> 0.0f
        assertEquals(1.0f, floats[3], 0.001f) // Pixel 3 Red = 255 -> 1.0f

        // Channel 1: All 4 Green floats
        assertEquals(0.0f, floats[4], 0.001f) // Pixel 0 Green = 0   -> 0.0f
        assertEquals(1.0f, floats[5], 0.001f) // Pixel 1 Green = 255 -> 1.0f
        assertEquals(0.0f, floats[6], 0.001f) // Pixel 2 Green = 0   -> 0.0f
        assertEquals(1.0f, floats[7], 0.001f) // Pixel 3 Green = 255 -> 1.0f

        // Channel 2: All 4 Blue floats
        assertEquals(0.0f, floats[8], 0.001f)  // Pixel 0 Blue = 0   -> 0.0f
        assertEquals(0.0f, floats[9], 0.001f)  // Pixel 1 Blue = 0   -> 0.0f
        assertEquals(1.0f, floats[10], 0.001f) // Pixel 2 Blue = 255 -> 1.0f
        assertEquals(1.0f, floats[11], 0.001f) // Pixel 3 Blue = 255 -> 1.0f
    }

    @Test
    fun imagePreprocessor_pixelNormalization_exactRange() {
        // Pixel with R=0, G=128, B=255
        val pixel = (0xFF shl 24) or (0x00 shl 16) or (0x80 shl 8) or 0xFF
        val pixels = intArrayOf(pixel)

        val buffer = ImagePreprocessor.packPixelsToFloatBuffer(
            pixels = pixels,
            width = 1,
            height = 1,
            layout = TensorLayout.NCHW
        )

        val floats = FloatArray(3)
        buffer.asFloatBuffer().get(floats)

        // R = 0/255 = 0.0f
        assertEquals(0.0f, floats[0], 0.0001f)
        // G = 128/255 ≈ 0.50196f
        assertEquals(128f / 255f, floats[1], 0.0001f)
        // B = 255/255 = 1.0f
        assertEquals(1.0f, floats[2], 0.0001f)
    }

    @Test
    fun letterboxTransform_mapsCoordinatesAccurately() {
        val transform = LetterboxTransform(
            scale = 1.0f,
            padX = 80f,
            padY = 0f,
            originalWidth = 480,
            originalHeight = 640,
            targetWidth = 640,
            targetHeight = 640
        )

        val mapped = transform.mapBoxToNormalized(
            cx = 320f,
            cy = 320f,
            width = 160f,
            height = 320f
        )

        assertEquals(0.3333f, mapped.left, 0.001f)
        assertEquals(0.25f, mapped.top, 0.001f)
        assertEquals(0.6667f, mapped.right, 0.001f)
        assertEquals(0.75f, mapped.bottom, 0.001f)
    }

    private class FakeObjectDetector(
        initialState: DetectorState = DetectorState.Uninitialized
    ) : ObjectDetector {
        override val state = MutableStateFlow(initialState)
        override val config = DetectorConfig()
        override val labels = listOf("person", "chair")

        var initializeCalled = false
        var closeCalled = false

        override fun initialize(): Boolean {
            initializeCalled = true
            state.value = DetectorState.Ready
            return true
        }

        override fun detect(bitmap: Bitmap, orientationDegrees: Int): List<Detection> {
            return listOf(
                Detection(
                    classId = 0,
                    label = "person",
                    confidence = 0.95f,
                    boundingBox = BoundingBox(0.1f, 0.1f, 0.5f, 0.5f)
                )
            )
        }

        override fun close() {
            closeCalled = true
            state.value = DetectorState.Closed
        }
    }

    @Test
    fun yoloFrameAnalyzer_initializationAndReset() {
        val fakeDetector = FakeObjectDetector()
        val analyzer = YoloFrameAnalyzer(fakeDetector)

        assertTrue(fakeDetector.initializeCalled)
        assertTrue(analyzer.yoloState.value.isReady)
        assertEquals("READY", analyzer.yoloState.value.statusMessage)
        assertEquals(DetectorStatusCode.MODEL_READY, analyzer.yoloState.value.statusCode)

        analyzer.reset()
        assertTrue(analyzer.yoloState.value.isReady)
    }

    @Test
    fun yoloFrameAnalyzer_reportsModelMissingWhenNotReady() {
        val fakeDetector = object : ObjectDetector {
            override val state = MutableStateFlow<DetectorState>(
                DetectorState.ModelMissing("models/yolov8n.tflite", "File not found")
            )
            override val config = DetectorConfig()
            override val labels = emptyList<String>()
            override fun initialize(): Boolean = false
            override fun detect(bitmap: Bitmap, orientationDegrees: Int) = emptyList<Detection>()
            override fun close() {}
        }

        val analyzer = YoloFrameAnalyzer(fakeDetector)
        assertFalse(analyzer.yoloState.value.isReady)
        assertEquals(DetectorStatusCode.MODEL_MISSING, analyzer.yoloState.value.statusCode)
        assertEquals("MODEL MISSING", analyzer.yoloState.value.statusMessage)
    }

    @Test
    fun yoloFrameAnalyzer_reportsSchemaMismatchProperly() {
        val fakeDetector = object : ObjectDetector {
            override val state = MutableStateFlow<DetectorState>(
                DetectorState.ModelSchemaMismatch("Embedded NMS detected", "Expected raw YOLOv8 tensor")
            )
            override val config = DetectorConfig()
            override val labels = emptyList<String>()
            override fun initialize(): Boolean = false
            override fun detect(bitmap: Bitmap, orientationDegrees: Int) = emptyList<Detection>()
            override fun close() {}
        }

        val analyzer = YoloFrameAnalyzer(fakeDetector)
        assertFalse(analyzer.yoloState.value.isReady)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, analyzer.yoloState.value.statusCode)
        assertEquals("MODEL SCHEMA MISMATCH", analyzer.yoloState.value.statusMessage)
    }
}
