package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.DetectorStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tensorflow.lite.DataType

/**
 * Unit tests for [CurrencyModelValidator] enforcing the inspected model contract:
 * - Exactly 1 input tensor: [1, 3, 640, 640] FLOAT32 (NCHW)
 * - Exactly 1 output tensor: [1, 10, 8400] FLOAT32
 */
class CurrencyModelValidatorTest {

    private val defaultConfig = CurrencyDetectorConfig()

    @Test
    fun validateTensorSchema_validNCHWAndOutput_passes() {
        val result = CurrencyModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 640, 640)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 10, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = defaultConfig
        )

        assertTrue(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_READY, result.statusCode)
        assertEquals(1, result.inputTensorCount)
        assertEquals(1, result.outputTensorCount)
    }

    @Test
    fun validateTensorSchema_validTransposedOutput_passes() {
        // Models with shape [1, 8400, 10] are also valid
        val result = CurrencyModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 640, 640)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 8400, 10)),
            outputTypes = listOf(DataType.FLOAT32),
            config = defaultConfig
        )

        assertTrue(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_READY, result.statusCode)
    }

    @Test
    fun validateTensorSchema_nhwcInputLayout_rejected() {
        val result = CurrencyModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 640, 640, 3)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 10, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = defaultConfig
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertNotNull(result.failureReason)
        assertTrue(result.failureReason!!.contains("NHWC"))
    }

    @Test
    fun validateTensorSchema_wrongInputResolution_rejected() {
        val result = CurrencyModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 320, 320)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 10, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = defaultConfig
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertTrue(result.failureReason!!.contains("Input shape"))
    }

    @Test
    fun validateTensorSchema_wrongInputChannels_rejected() {
        val result = CurrencyModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 1, 640, 640)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 10, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = defaultConfig
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
    }

    @Test
    fun validateTensorSchema_wrongInputDataType_rejected() {
        val result = CurrencyModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 640, 640)),
            inputTypes = listOf(DataType.UINT8),
            outputShapes = listOf(intArrayOf(1, 10, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = defaultConfig
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertTrue(result.failureReason!!.contains("datatype"))
    }

    @Test
    fun validateTensorSchema_embeddedNmsExport_rejected() {
        // Embedded NMS outputs 4 tensors: boxes, classes, scores, count
        val result = CurrencyModelValidator.validateTensorSchema(
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
            config = defaultConfig
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertTrue(result.failureReason!!.contains("embedded NMS"))
    }

    @Test
    fun validateTensorSchema_mismatchedOutputFeatures_rejected() {
        // 84 features (COCO model) instead of 10 features (currency model)
        val result = CurrencyModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 640, 640)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 84, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = defaultConfig
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertTrue(result.failureReason!!.contains("Output shape"))
    }

    @Test
    fun validateTensorSchema_wrongOutputDataType_rejected() {
        val result = CurrencyModelValidator.validateTensorSchema(
            inputShapes = listOf(intArrayOf(1, 3, 640, 640)),
            inputTypes = listOf(DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 10, 8400)),
            outputTypes = listOf(DataType.UINT8),
            config = defaultConfig
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertTrue(result.failureReason!!.contains("datatype"))
    }

    @Test
    fun validateTensorSchema_multipleInputTensors_rejected() {
        val result = CurrencyModelValidator.validateTensorSchema(
            inputShapes = listOf(
                intArrayOf(1, 3, 640, 640),
                intArrayOf(1, 3, 640, 640)
            ),
            inputTypes = listOf(DataType.FLOAT32, DataType.FLOAT32),
            outputShapes = listOf(intArrayOf(1, 10, 8400)),
            outputTypes = listOf(DataType.FLOAT32),
            config = defaultConfig
        )

        assertFalse(result.isValid)
        assertEquals(DetectorStatusCode.MODEL_SCHEMA_MISMATCH, result.statusCode)
        assertTrue(result.failureReason!!.contains("Expected 1 input tensor"))
    }
}
