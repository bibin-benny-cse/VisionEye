package com.bibin.visioneye

import android.app.Application
import com.bibin.visioneye.camera.CameraController
import com.bibin.visioneye.camera.CameraManager
import com.bibin.visioneye.core.mode.DefaultModeManager
import com.bibin.visioneye.core.mode.ModeManager
import com.bibin.visioneye.speech.DefaultSpeechController
import com.bibin.visioneye.speech.SpeechController

/**
 * Base Application class for VisionEye.
 *
 * Initializes application-wide singletons including the central [ModeManager],
 * the audio [SpeechController], and the mode-aware [CameraController].
 */
class VisionEyeApplication : Application() {

    lateinit var modeManager: ModeManager
        private set

    lateinit var speechController: SpeechController
        private set

    lateinit var cameraController: CameraController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val mm = DefaultModeManager()
        modeManager = mm

        val sc = DefaultSpeechController(this)
        speechController = sc

        val cm = CameraManager(this, speechController = sc)
        cameraController = cm
        // Register camera controller to mode transitions for automatic pause/stop
        mm.addListener(cm)
    }

    companion object {
        lateinit var instance: VisionEyeApplication
            private set
    }
}
