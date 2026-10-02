package com.bibin.visioneye

import android.app.Application
import com.bibin.visioneye.camera.CameraController
import com.bibin.visioneye.camera.CameraManager
import com.bibin.visioneye.core.mode.DefaultModeManager
import com.bibin.visioneye.core.mode.ModeManager

/**
 * Base Application class for VisionEye.
 *
 * Initializes application-wide singletons including the central [ModeManager]
 * and the mode-aware [CameraController].
 */
class VisionEyeApplication : Application() {

    lateinit var modeManager: ModeManager
        private set

    lateinit var cameraController: CameraController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val mm = DefaultModeManager()
        modeManager = mm

        val cm = CameraManager(this)
        cameraController = cm
        // Register camera controller to mode transitions for automatic pause/stop
        mm.addListener(cm)
    }

    companion object {
        lateinit var instance: VisionEyeApplication
            private set
    }
}
