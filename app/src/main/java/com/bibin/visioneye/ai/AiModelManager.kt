package com.bibin.visioneye.ai

import com.bibin.visioneye.core.contract.ModeAwareComponent
import com.bibin.visioneye.core.mode.VisionMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Enumerates discrete AI inference tasks corresponding to operational modes.
 */
enum class AiTaskType(val associatedMode: VisionMode) {
    OBJECT_AND_DEPTH_DETECTION(VisionMode.NAVIGATE),
    DOCUMENT_OCR(VisionMode.READ),
    CURRENCY_CLASSIFICATION(VisionMode.CURRENCY),
    FACE_RECOGNITION(VisionMode.PEOPLE)
}

/**
 * Output abstraction for AI inference.
 */
interface AiInferenceResult {
    val inferenceTimeMs: Long
}

/**
 * Architectural interface for AI Model Management.
 *
 * Enforces key development principles:
 * 1. Keep AI models behind clean interfaces.
 * 2. Do NOT run every AI model continuously (only active mode runs).
 * 3. Heavy inference must NEVER run on the UI thread (uses background dispatcher).
 * 4. Prefer on-device processing (TFLite, ONNX, MediaPipe) where practical.
 */
interface AiModelManager : ModeAwareComponent {
    /**
     * Currently active AI task, if any.
     */
    val currentTask: AiTaskType?

    /**
     * Dedicated background dispatcher for heavy machine learning inference.
     */
    val inferenceDispatcher: CoroutineDispatcher
        get() = Dispatchers.Default

    /**
     * Set of modes where AI models are executed.
     */
    override val supportedModes: Set<VisionMode>
        get() = setOf(
            VisionMode.NAVIGATE,
            VisionMode.READ,
            VisionMode.CURRENCY,
            VisionMode.PEOPLE
        )

    /**
     * Prepares and loads the model required for [task].
     */
    fun loadModelForTask(task: AiTaskType)

    /**
     * Unloads active models to free memory and GPU delegates.
     */
    fun unloadActiveModels()
}
