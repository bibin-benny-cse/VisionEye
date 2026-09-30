package com.bibin.visioneye.ai

import com.bibin.visioneye.core.contract.ModeAwareComponent
import com.bibin.visioneye.core.mode.VisionMode

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
 * Architectural interface for AI Model Management.
 *
 * Guarantees that only the model associated with the active mode is loaded
 * and scheduled for execution, enforcing compute and memory efficiency.
 *
 * (Model runners e.g. TFLite/ONNX to be added in future milestone)
 */
interface AiModelManager : ModeAwareComponent {
    /**
     * Currently active AI task, if any.
     */
    val currentTask: AiTaskType?

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
