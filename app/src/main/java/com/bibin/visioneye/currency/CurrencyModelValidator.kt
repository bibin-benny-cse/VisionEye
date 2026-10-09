package com.bibin.visioneye.currency

import com.bibin.visioneye.ai.DetectorStatusCode
import com.bibin.visioneye.ai.ModelValidationReport
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * Model validation utility for YOLO11n Indian Currency detection models.
 *
 * Enforces the inspected model contract:
 * 1. Exactly 1 input tensor with rank 4, shape [1, 3, 640, 640] (NCHW), and FLOAT32 datatype.
 *    Rejects NHWC models ([1, 640, 640, 3]) with [DetectorStatusCode.MODEL_SCHEMA_MISMATCH].
 * 2. Exactly 1 raw prediction output tensor with shape [1, 10, 8400] and FLOAT32 datatype.
 *    Rejects embedded NMS models (4 output tensors) and mismatched class models with [DetectorStatusCode.MODEL_SCHEMA_MISMATCH].
 */
object CurrencyModelValidator {

    /**
     * Validates an active TensorFlow Lite [interpreter] against [config].
     */
    fun validate(interpreter: Interpreter, config: CurrencyDetectorConfig): ModelValidationReport {
        val inputCount = interpreter.inputTensorCount
        val inputShapes = mutableListOf<IntArray>()
        val inputTypes = mutableListOf<DataType>()

        for (i in 0 until inputCount) {
            val tensor = interpreter.getInputTensor(i)
            inputShapes.add(tensor.shape())
            inputTypes.add(tensor.dataType())
        }

        val outputCount = interpreter.outputTensorCount
        val outputShapes = mutableListOf<IntArray>()
        val outputTypes = mutableListOf<DataType>()

        for (i in 0 until outputCount) {
            val tensor = interpreter.getOutputTensor(i)
            outputShapes.add(tensor.shape())
            outputTypes.add(tensor.dataType())
        }

        return validateTensorSchema(
            inputShapes = inputShapes,
            inputTypes = inputTypes,
            outputShapes = outputShapes,
            outputTypes = outputTypes,
            config = config
        )
    }

    /**
     * Validates tensor shapes and datatypes. Separated from [Interpreter] to allow
     * comprehensive unit testing on host JVMs.
     */
    fun validateTensorSchema(
        inputShapes: List<IntArray>,
        inputTypes: List<DataType>,
        outputShapes: List<IntArray>,
        outputTypes: List<DataType>,
        config: CurrencyDetectorConfig
    ): ModelValidationReport {
        val inputCount = inputShapes.size
        val outputCount = outputShapes.size

        // 1. Verify input tensor count
        if (inputCount != 1) {
            return ModelValidationReport(
                isLoaded = true,
                isValid = false,
                statusCode = DetectorStatusCode.MODEL_SCHEMA_MISMATCH,
                inputTensorCount = inputCount,
                inputShapes = inputShapes,
                inputDataTypes = inputTypes,
                outputTensorCount = outputCount,
                outputShapes = outputShapes,
                outputDataTypes = outputTypes,
                failureReason = "Expected 1 input tensor, but found $inputCount",
                failureDetails = "Model must accept exactly 1 RGB image tensor. Found $inputCount tensors."
            )
        }

        val inShape = inputShapes[0]
        val inType = inputTypes[0]

        // 2. Verify input rank
        if (inShape.size != 4) {
            return ModelValidationReport(
                isLoaded = true,
                isValid = false,
                statusCode = DetectorStatusCode.MODEL_SCHEMA_MISMATCH,
                inputTensorCount = inputCount,
                inputShapes = inputShapes,
                inputDataTypes = inputTypes,
                outputTensorCount = outputCount,
                outputShapes = outputShapes,
                outputDataTypes = outputTypes,
                failureReason = "Input tensor rank is ${inShape.size} (expected 4D: [1, ${config.inputChannels}, ${config.inputHeight}, ${config.inputWidth}])",
                failureDetails = "Received input shape ${inShape.contentToString()}."
            )
        }

        // 3. Verify input shape matches NCHW [1, 3, 640, 640]
        val isExpectedNCHW = inShape[0] == config.batchSize &&
                inShape[1] == config.inputChannels &&
                inShape[2] == config.inputHeight &&
                inShape[3] == config.inputWidth

        if (!isExpectedNCHW) {
            val isNHWC = inShape[0] == config.batchSize &&
                    inShape[1] == config.inputHeight &&
                    inShape[2] == config.inputWidth &&
                    inShape[3] == config.inputChannels

            val failureMsg = if (isNHWC) {
                "Input shape ${inShape.contentToString()} is NHWC (channels-last). Expected NCHW (channels-first) [1, ${config.inputChannels}, ${config.inputHeight}, ${config.inputWidth}]"
            } else {
                "Input shape ${inShape.contentToString()} does not match expected NCHW [1, ${config.inputChannels}, ${config.inputHeight}, ${config.inputWidth}]"
            }

            return ModelValidationReport(
                isLoaded = true,
                isValid = false,
                statusCode = DetectorStatusCode.MODEL_SCHEMA_MISMATCH,
                inputTensorCount = inputCount,
                inputShapes = inputShapes,
                inputDataTypes = inputTypes,
                outputTensorCount = outputCount,
                outputShapes = outputShapes,
                outputDataTypes = outputTypes,
                failureReason = failureMsg,
                failureDetails = "Model input must be NCHW with shape [1, ${config.inputChannels}, ${config.inputHeight}, ${config.inputWidth}]. Received ${inShape.contentToString()}."
            )
        }

        // 4. Verify input datatype
        if (inType != config.inputDataType) {
            return ModelValidationReport(
                isLoaded = true,
                isValid = false,
                statusCode = DetectorStatusCode.MODEL_SCHEMA_MISMATCH,
                inputTensorCount = inputCount,
                inputShapes = inputShapes,
                inputDataTypes = inputTypes,
                outputTensorCount = outputCount,
                outputShapes = outputShapes,
                outputDataTypes = outputTypes,
                failureReason = "Input datatype is $inType (expected ${config.inputDataType})",
                failureDetails = "The image preprocessor outputs normalized Float32 values. Quantized inputs require quantization scales."
            )
        }

        // 5. Verify output tensor count
        if (outputCount != 1) {
            val failureMsg = if (outputCount == 4) {
                "Model has 4 output tensors (embedded NMS detected). Current detector expects raw YOLO output tensor [1, ${config.expectedOutputChannels}, ${config.expectedOutputAnchors}]"
            } else {
                "Expected 1 output tensor, but found $outputCount"
            }
            return ModelValidationReport(
                isLoaded = true,
                isValid = false,
                statusCode = DetectorStatusCode.MODEL_SCHEMA_MISMATCH,
                inputTensorCount = inputCount,
                inputShapes = inputShapes,
                inputDataTypes = inputTypes,
                outputTensorCount = outputCount,
                outputShapes = outputShapes,
                outputDataTypes = outputTypes,
                failureReason = failureMsg,
                failureDetails = if (outputCount == 4) {
                    "Model was exported with embedded NMS (nms=True). Currency detector requires raw output with nms=False."
                } else {
                    "Received $outputCount output tensors: ${outputShapes.map { it.contentToString() }}."
                }
            )
        }

        val outShape = outputShapes[0]
        val outType = outputTypes[0]

        // 6. Verify output rank
        if (outShape.size != 3) {
            return ModelValidationReport(
                isLoaded = true,
                isValid = false,
                statusCode = DetectorStatusCode.MODEL_SCHEMA_MISMATCH,
                inputTensorCount = inputCount,
                inputShapes = inputShapes,
                inputDataTypes = inputTypes,
                outputTensorCount = outputCount,
                outputShapes = outputShapes,
                outputDataTypes = outputTypes,
                failureReason = "Output tensor rank is ${outShape.size} (expected 3D: [1, ${config.expectedOutputChannels}, ${config.expectedOutputAnchors}])",
                failureDetails = "Received output shape ${outShape.contentToString()}."
            )
        }

        // 7. Verify output shape matches [1, 10, 8400] or [1, 8400, 10]
        val isChannelsFirst = outShape[0] == 1 &&
                outShape[1] == config.expectedOutputChannels &&
                outShape[2] == config.expectedOutputAnchors
        val isChannelsLast = outShape[0] == 1 &&
                outShape[1] == config.expectedOutputAnchors &&
                outShape[2] == config.expectedOutputChannels

        if (!isChannelsFirst && !isChannelsLast) {
            return ModelValidationReport(
                isLoaded = true,
                isValid = false,
                statusCode = DetectorStatusCode.MODEL_SCHEMA_MISMATCH,
                inputTensorCount = inputCount,
                inputShapes = inputShapes,
                inputDataTypes = inputTypes,
                outputTensorCount = outputCount,
                outputShapes = outputShapes,
                outputDataTypes = outputTypes,
                failureReason = "Output shape ${outShape.contentToString()} does not match expected raw output [1, ${config.expectedOutputChannels}, ${config.expectedOutputAnchors}]",
                failureDetails = "Expected ${config.expectedOutputChannels} features (4 coordinates + 6 currency classes) across ${config.expectedOutputAnchors} anchors. Found ${outShape.contentToString()}."
            )
        }

        // 8. Verify output datatype
        if (outType != DataType.FLOAT32) {
            return ModelValidationReport(
                isLoaded = true,
                isValid = false,
                statusCode = DetectorStatusCode.MODEL_SCHEMA_MISMATCH,
                inputTensorCount = inputCount,
                inputShapes = inputShapes,
                inputDataTypes = inputTypes,
                outputTensorCount = outputCount,
                outputShapes = outputShapes,
                outputDataTypes = outputTypes,
                failureReason = "Output datatype is $outType (expected FLOAT32)",
                failureDetails = "Post-processing requires Float32 scores and box coordinates."
            )
        }

        return ModelValidationReport(
            isLoaded = true,
            isValid = true,
            statusCode = DetectorStatusCode.MODEL_READY,
            inputTensorCount = inputCount,
            inputShapes = inputShapes,
            inputDataTypes = inputTypes,
            outputTensorCount = outputCount,
            outputShapes = outputShapes,
            outputDataTypes = outputTypes,
            failureReason = null,
            failureDetails = null
        )
    }
}
