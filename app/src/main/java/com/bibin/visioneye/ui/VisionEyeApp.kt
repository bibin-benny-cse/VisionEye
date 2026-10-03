package com.bibin.visioneye.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bibin.visioneye.camera.CameraController
import com.bibin.visioneye.core.mode.ModeManager
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.ui.screens.CameraAssistanceScreen
import com.bibin.visioneye.ui.screens.MainScreen
import com.bibin.visioneye.ui.theme.VisionEyeTheme

/**
 * Root Composable for VisionEye.
 *
 * Coordinates navigation between the mode selection dashboard ([MainScreen])
 * and the active [CameraAssistanceScreen] when live obstacle scanning or text reading is invoked.
 */
@Composable
fun VisionEyeApp(
    modeManager: ModeManager,
    cameraController: CameraController
) {
    val currentMode by modeManager.currentMode.collectAsState()
    var isCameraActive by rememberSaveable { mutableStateOf(false) }

    VisionEyeTheme {
        if (isCameraActive && currentMode.requiresCamera) {
            CameraAssistanceScreen(
                currentMode = currentMode,
                cameraController = cameraController,
                onStopCamera = {
                    isCameraActive = false
                }
            )
        } else {
            MainScreen(
                currentMode = currentMode,
                onModeSelected = { selectedMode ->
                    modeManager.setMode(selectedMode)
                },
                onStartCamera = {
                    modeManager.setMode(VisionMode.NAVIGATE)
                    isCameraActive = true
                },
                onStartReadMode = {
                    modeManager.setMode(VisionMode.READ)
                    isCameraActive = true
                }
            )
        }
    }
}
