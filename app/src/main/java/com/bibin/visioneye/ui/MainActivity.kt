package com.bibin.visioneye.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bibin.visioneye.VisionEyeApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as VisionEyeApplication
        val modeManager = app.modeManager
        val cameraController = app.cameraController

        setContent {
            VisionEyeApp(
                modeManager = modeManager,
                cameraController = cameraController
            )
        }
    }
}
