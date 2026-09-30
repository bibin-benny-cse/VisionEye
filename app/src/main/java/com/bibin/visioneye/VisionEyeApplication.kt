package com.bibin.visioneye

import android.app.Application
import com.bibin.visioneye.core.mode.DefaultModeManager
import com.bibin.visioneye.core.mode.ModeManager

/**
 * Base Application class for VisionEye.
 *
 * Initializes application-wide singletons including the central [ModeManager].
 */
class VisionEyeApplication : Application() {

    lateinit var modeManager: ModeManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        modeManager = DefaultModeManager()
    }

    companion object {
        lateinit var instance: VisionEyeApplication
            private set
    }
}
