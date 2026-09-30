package com.bibin.visioneye.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bibin.visioneye.core.mode.ModeManager
import com.bibin.visioneye.ui.screens.MainScreen
import com.bibin.visioneye.ui.theme.VisionEyeTheme

@Composable
fun VisionEyeApp(
    modeManager: ModeManager
) {
    val currentMode by modeManager.currentMode.collectAsState()

    VisionEyeTheme {
        MainScreen(
            currentMode = currentMode,
            onModeSelected = { selectedMode ->
                modeManager.setMode(selectedMode)
            }
        )
    }
}
