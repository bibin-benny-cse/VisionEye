package com.bibin.visioneye.ai

import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * Diagnostic report generated after validating a TensorFlow Lite model schema.
 *
 * @property isLoaded Whether the model binary was successfully read and opened.
 * @property isValid Whether the model's tensor counts, shapes, and datatypes match YOLOv8n requirements.
 * @property statusCode The resulting [DetectorStatusCode].
 * @property inputTensorCount Number of input tensors found in the model.
 * @property inputShapes Dimensions of each input tensor.
 * @property inputDataTypes Data type of each input tensor.
 * @property outputTensorCount Number of output tensors found in the model.
 * @property outputShapes Dimensions of each output tensor.
 * @property outputDataTypes Data type of each output tensor.
 * @property failureReason Concise explanation if validation failed.
 * @property failureDetails Detailed diagnostic description for developer troubleshooting.
 */
data class ModelValidationReport(
    val isLoaded: Boolean,
    val isValid: Boolean,
    val statusCode: DetectorStatusCode,
    val inputTensorCount: Int = 0,
    val inputShapes: List<IntArray> = emptyList(),
    val inputDataTypes: List<DataType> = emptyList(),
    val outputTensorCount: Int = 0,
    val outputShapes: List<IntArray> = emptyList(),
    val outputDataTypes: List<DataType> = emptyList(),
    val failureReason: String? = null,
    val failureDetails: String? = null
) {
    /**
     * Formats a concise human-readable summary of model tensor structure.
     */
    fun formatSummary(): String {
        val inSummary = inputShapes.mapIndexed { idx, s ->
            "In#$idx: ${s.contentToString()} (${inputDataTypes.getOrNull(idx) ?: "UNKNOWN"})"
        }.joinToString(", ")

        val outSummary = outputShapes.mapIndexed { idx, s ->
            "Out#$idx: ${s.contentToString()} (${outputDataTypes.getOrNull(idx) ?: "UNKNOWN"})"
        }.joinToString(", ")

        return "Inputs ($inputTensorCount): [$inSummary] | Outputs ($outputTensorCount): [$outSummary]"
    }
}

/**
 * Model validation utility for YOLOv8 object detection models.
 *
 * Enforces the actual exported model contract:
 * 1. Exactly 1 input tensor with rank 4, shape [1, 3, 640, 640] (NCHW), and FLOAT32 datatype.
 *    Rejects NHWC models ([1, 640, 640, 3]) with [DetectorStatusCode.MODEL_SCHEMA_MISMATCH].
 * 2. Exactly 1 raw YOLO output tensor with shape [1, 84, 8400] and FLOAT32 datatype.
 *    Rejects embedded NMS models (e.g. 4 output tensors) with [DetectorStatusCode.MODEL_SCHEMA_MISMATCH].
 */
object ModelValidator {

    /**
     * Validates an active TensorFlow Lite [interpreter] against [config].
     */
    fun validate(interpreter: Interpreter, config: DetectorConfig): ModelValidationReport {
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
        config: DetectorConfig
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
                failureDetails = "The image preprocessor outputs normalized Float32 values. Quantized INT8/UINT8 inputs require quantization parameters."
            )
        }

        // 5. Verify output tensor count
        if (outputCount != 1) {
            val failureMsg = if (outputCount == 4) {
                "Model has 4 output tensors (embedded NMS detected). Current detector expects raw YOLOv8 tensor [1, 84, 8400]"
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
                    "Model was exported with embedded NMS (nms=True). Please export raw model without nms: 'yolo export model=yolov8n.pt format=tflite imgsz=640'."
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
                failureReason = "Output tensor rank is ${outShape.size} (expected 3D: [1, 84, 8400])",
                failureDetails = "Received output shape ${outShape.contentToString()}."
            )
        }

        // 7. Verify output shape matches raw YOLOv8n [1, 84, 8400]
        val isExpectedOutput = outShape[0] == 1 && outShape[1] == 84 && outShape[2] == 8400
        if (!isExpectedOutput) {
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
                failureReason = "Output shape ${outShape.contentToString()} does not match expected raw output [1, 84, 8400]",
                failureDetails = "Expected 84 features (4 coordinates + 80 class scores) across 8400 anchors. Found ${outShape.contentToString()}."
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

        // Passed all schema validation checks
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
