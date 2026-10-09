package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.ImagePreprocessor
import com.bibin.visioneye.ai.LetterboxTransform
import com.bibin.visioneye.ai.TensorLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tensorflow.lite.DataType

/**
 * Unit tests for Currency preprocessing and coordinate mapping.
 */
class CurrencyPreprocessingTest {

    @Test
    fun currencyDetectorConfig_matchesInspectedYolo11nModel() {
        val config = CurrencyDetectorConfig()

        assertEquals("models/currency_yolo11n.tflite", config.modelPath)
        assertEquals("models/currency_labels.txt", config.labelPath)
        assertEquals(1, config.batchSize)
        assertEquals(3, config.inputChannels)
        assertEquals(640, config.inputWidth)
        assertEquals(640, config.inputHeight)
        assertEquals(TensorLayout.NCHW, config.inputLayout)
        assertEquals(DataType.FLOAT32, config.inputDataType)
        assertEquals(10, config.expectedOutputChannels) // 4 coords + 6 classes
        assertEquals(8400, config.expectedOutputAnchors)
        assertEquals(0.50f, config.confidenceThreshold, 0.001f)
        assertEquals(0.45f, config.iouThreshold, 0.001f)
        assertEquals(10, config.maxDetections)
        assertEquals(4, config.numThreads)

        // Element count: 1 * 3 * 640 * 640 = 1,228,800 floats
        assertEquals(1 * 3 * 640 * 640, config.inputElementCount)
        // Byte count: 1,228,800 * 4 bytes = 4,915,200 bytes
        assertEquals(1 * 3 * 640 * 640 * 4, config.inputByteCount)
    }

    @Test
    fun planarNCHWChannelPacking_matchesModelExpectation() {
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

        val floats = FloatArray(12)
        buffer.asFloatBuffer().get(floats)

        // Red planar channel (first 4 floats)
        assertEquals(1.0f, floats[0], 0.001f) // Pixel 0
        assertEquals(0.0f, floats[1], 0.001f) // Pixel 1
        assertEquals(0.0f, floats[2], 0.001f) // Pixel 2
        assertEquals(1.0f, floats[3], 0.001f) // Pixel 3

        // Green planar channel (middle 4 floats)
        assertEquals(0.0f, floats[4], 0.001f) // Pixel 0
        assertEquals(1.0f, floats[5], 0.001f) // Pixel 1
        assertEquals(0.0f, floats[6], 0.001f) // Pixel 2
        assertEquals(1.0f, floats[7], 0.001f) // Pixel 3

        // Blue planar channel (last 4 floats)
        assertEquals(0.0f, floats[8], 0.001f)  // Pixel 0
        assertEquals(0.0f, floats[9], 0.001f)  // Pixel 1
        assertEquals(1.0f, floats[10], 0.001f) // Pixel 2
        assertEquals(1.0f, floats[11], 0.001f) // Pixel 3
    }

    @Test
    fun letterboxCoordinateMapping_preservesBoundingBoxes() {
        // Original 480x640 frame letterboxed into 640x640:
        // Scale = 1.0, padX = 80, padY = 0
        val transform = LetterboxTransform(
            scale = 1.0f,
            padX = 80f,
            padY = 0f,
            originalWidth = 480,
            originalHeight = 640,
            targetWidth = 640,
            targetHeight = 640
        )

        // Box centered at cx=320, cy=320, w=160, h=320 on model 640x640 canvas
        // On model canvas: left = 240, right = 400, top = 160, bottom = 480
        // Subtract padX=80: left = 160, right = 320
        // Normalized to 480 width: left = 160/480 = 0.3333, right = 320/480 = 0.6667
        // Normalized to 640 height: top = 160/640 = 0.25, bottom = 480/640 = 0.75
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
        assertTrue(mapped.width > 0f)
        assertTrue(mapped.height > 0f)
    }
}
