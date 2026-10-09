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
import com.bibin.visioneye.navigation.NavigationController
import com.bibin.visioneye.ui.screens.CameraAssistanceScreen
import com.bibin.visioneye.ui.screens.EnrollPersonScreen
import com.bibin.visioneye.ui.screens.MainScreen
import com.bibin.visioneye.ui.screens.ManagePeopleScreen
import com.bibin.visioneye.ui.screens.NavigationGpsScreen
import com.bibin.visioneye.ui.theme.VisionEyeTheme

/**
 * Root Composable for VisionEye.
 *
 * Coordinates navigation between the mode selection dashboard ([MainScreen]),
 * the live vision scanning HUD ([CameraAssistanceScreen]), the face enrollment flow ([EnrollPersonScreen]),
 * the locally enrolled profiles manager ([ManagePeopleScreen]), and the outdoor GPS wayfinding diagnostics ([NavigationGpsScreen]).
 */
@Composable
fun VisionEyeApp(
    modeManager: ModeManager,
    cameraController: CameraController,
    navigationController: NavigationController,
    emergencyController: com.bibin.visioneye.emergency.EmergencyController
) {
    val currentMode by modeManager.currentMode.collectAsState()
    var isCameraActive by rememberSaveable { mutableStateOf(false) }
    var isEnrollingPerson by rememberSaveable { mutableStateOf(false) }
    var isManagingPeople by rememberSaveable { mutableStateOf(false) }

    VisionEyeTheme {
        when {
            isEnrollingPerson -> {
                EnrollPersonScreen(
                    cameraController = cameraController,
                    onEnrollmentComplete = {
                        isEnrollingPerson = false
                    },
                    onCancel = {
                        isEnrollingPerson = false
                    }
                )
            }
            isManagingPeople -> {
                ManagePeopleScreen(
                    repository = cameraController.peopleRepository,
                    onBack = {
                        isManagingPeople = false
                    },
                    onAddNewPerson = {
                        isManagingPeople = false
                        isEnrollingPerson = true
                    }
                )
            }
            isCameraActive && currentMode.requiresCamera -> {
                CameraAssistanceScreen(
                    currentMode = currentMode,
                    cameraController = cameraController,
                    onStopCamera = {
                        isCameraActive = false
                    }
                )
            }
            currentMode == VisionMode.NAVIGATION -> {
                NavigationGpsScreen(
                    navigationController = navigationController,
                    onStopNavigation = {
                        modeManager.setMode(VisionMode.NAVIGATE)
                    }
                )
            }
            currentMode == VisionMode.SOS -> {
                com.bibin.visioneye.ui.screens.SosScreen(
                    emergencyController = emergencyController,
                    onExitSos = {
                        modeManager.setMode(VisionMode.NAVIGATE)
                    }
                )
            }
            else -> {
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
                    },
                    onStartCurrencyMode = {
                        modeManager.setMode(VisionMode.CURRENCY)
                        isCameraActive = true
                    },
                    onStartPeopleMode = {
                        modeManager.setMode(VisionMode.PEOPLE)
                        isCameraActive = true
                    },
                    onSavePerson = {
                        isEnrollingPerson = true
                    },
                    onManagePeople = {
                        isManagingPeople = true
                    }
                )
            }
        }
    }
}
