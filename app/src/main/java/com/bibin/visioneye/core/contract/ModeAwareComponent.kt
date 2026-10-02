package com.bibin.visioneye.core.contract

import com.bibin.visioneye.core.mode.ModeChangeListener
import com.bibin.visioneye.core.mode.VisionMode

/**
 * Architectural contract for components and subsystems in VisionEye.
 *
 * Enforces the rule: "Do NOT run all AI models continuously.
 * The application will use a mode-based architecture to activate
 * only the functionality required by the current task."
 *
 * Every subsystem (camera, AI, fusion, navigation, emergency) implements
 * or coordinates with this contract to sleep/pause when its associated
 * mode is inactive.
 */
interface ModeAwareComponent : ModeChangeListener {
    /**
     * The set of [VisionMode]s in which this component must be active.
     */
    val supportedModes: Set<VisionMode>

    /**
     * Checks if this component should be active for the specified mode.
     */
    fun isEnabledFor(mode: VisionMode): Boolean = supportedModes.contains(mode)

    /**
     * Invoked when the system operational mode changes.
     * Implementations should activate or deactivate their workers accordingly.
     *
     * @param newMode The newly active mode.
     * @param previousMode The mode that was exited.
     */
    override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode)
}
