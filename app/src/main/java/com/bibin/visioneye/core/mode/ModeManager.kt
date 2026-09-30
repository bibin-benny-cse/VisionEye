package com.bibin.visioneye.core.mode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Callback listener invoked when the system switches between modes.
 */
fun interface ModeChangeListener {
    /**
     * Triggered when the active VisionMode transitions.
     *
     * @param newMode The mode now active.
     * @param previousMode The mode being exited.
     */
    fun onModeChanged(newMode: VisionMode, previousMode: VisionMode)
}

/**
 * Contract for managing the active operational mode in VisionEye.
 *
 * Ensures that subsystems only run AI pipelines and sensor capture
 * pertinent to the active [VisionMode].
 */
interface ModeManager {
    /**
     * Observable state flow of the current [VisionMode].
     */
    val currentMode: StateFlow<VisionMode>

    /**
     * Retrieves the current mode synchronously.
     */
    fun getMode(): VisionMode

    /**
     * Requests a transition to a new [VisionMode].
     *
     * @param mode Target mode to switch into.
     * @return True if mode was transitioned, false if already in target mode.
     */
    fun setMode(mode: VisionMode): Boolean

    /**
     * Registers a listener to be notified when the mode changes.
     */
    fun addListener(listener: ModeChangeListener)

    /**
     * Unregisters a previously added mode listener.
     */
    fun removeListener(listener: ModeChangeListener)
}

/**
 * Thread-safe default implementation of [ModeManager].
 */
class DefaultModeManager(
    initialMode: VisionMode = VisionMode.DEFAULT
) : ModeManager {

    private val _currentMode = MutableStateFlow(initialMode)
    override val currentMode: StateFlow<VisionMode> = _currentMode.asStateFlow()

    private val listeners = CopyOnWriteArrayList<ModeChangeListener>()

    @Synchronized
    override fun getMode(): VisionMode {
        return _currentMode.value
    }

    @Synchronized
    override fun setMode(mode: VisionMode): Boolean {
        val previous = _currentMode.value
        if (previous == mode) {
            return false
        }

        _currentMode.value = mode

        // Notify registered listeners of the mode transition
        for (listener in listeners) {
            listener.onModeChanged(mode, previous)
        }
        return true
    }

    override fun addListener(listener: ModeChangeListener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeListener(listener: ModeChangeListener) {
        listeners.remove(listener)
    }
}
