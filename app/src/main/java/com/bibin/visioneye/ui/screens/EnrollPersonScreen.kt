package com.bibin.visioneye.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bibin.visioneye.camera.CameraController
import com.bibin.visioneye.ui.components.VisionHeader
import com.bibin.visioneye.ui.components.VoiceControlButton
import com.bibin.visioneye.ui.theme.HighContrastBlack
import com.bibin.visioneye.ui.theme.HighContrastBorder
import com.bibin.visioneye.ui.theme.HighContrastCard
import com.bibin.visioneye.ui.theme.HighContrastCyan
import com.bibin.visioneye.ui.theme.HighContrastSurface
import com.bibin.visioneye.ui.theme.HighContrastTextMuted
import com.bibin.visioneye.ui.theme.HighContrastWhite
import com.bibin.visioneye.ui.theme.HighContrastYellow
import com.bibin.visioneye.ui.theme.PeoplePurple
import com.bibin.visioneye.ui.theme.ReadGreen

/**
 * Screen guiding the user or caregiver through the face enrollment ("SAVE PERSON") flow.
 *
 * Captures multiple high-quality face samples, extracts embeddings on-device,
 * and saves them with the entered person name.
 */
@Composable
fun EnrollPersonScreen(
    cameraController: CameraController,
    onEnrollmentComplete: () -> Unit,
    onCancel: () -> Unit,
    voiceController: com.bibin.visioneye.voice.VoiceController? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val enrollmentState by cameraController.enrollmentState.collectAsState()

    var personNameInput by remember { mutableStateOf("") }
    var hasConsent by remember { mutableStateOf(false) }
    var isCameraActive by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(cameraController.checkCameraPermission())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted && personNameInput.isNotBlank() && hasConsent) {
            isCameraActive = true
            cameraController.startPersonEnrollment(personNameInput.trim())
        }
    }

    androidx.compose.runtime.DisposableEffect(isCameraActive, personNameInput, hasConsent, enrollmentState, voiceController) {
        if (voiceController != null) {
            voiceController.setScreenContext(com.bibin.visioneye.voice.VoiceScreenContext.ENROLL_PERSON)
            voiceController.setDictationConsumer { fieldType, text ->
                if (fieldType == com.bibin.visioneye.voice.DictationFieldType.PERSON_NAME) {
                    val clean = text.trim()
                    personNameInput = clean
                    com.bibin.visioneye.voice.VoiceActionResult.Success("Person name set to $clean.")
                } else {
                    com.bibin.visioneye.voice.VoiceActionResult.Error("Unsupported field")
                }
            }
            val actions = com.bibin.visioneye.voice.ScreenVoiceActions.createEnrollPersonActions(
                isCameraActive = isCameraActive,
                onCancel = {
                    if (isCameraActive) {
                        cameraController.stopPersonEnrollment()
                        cameraController.unbindCamera()
                        isCameraActive = false
                    } else {
                        onCancel()
                    }
                },
                getPersonName = { personNameInput },
                setPersonName = { personNameInput = it },
                getConsentChecked = { hasConsent },
                setConsentChecked = { hasConsent = it },
                onStartCapture = {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        isCameraActive = true
                        cameraController.startPersonEnrollment(personNameInput.trim())
                    }
                },
                isComplete = { enrollmentState.isComplete },
                onDone = {
                    cameraController.stopPersonEnrollment()
                    cameraController.unbindCamera()
                    onEnrollmentComplete()
                }
            )
            voiceController.registry.registerAll(actions)
        }
        onDispose {
            voiceController?.registry?.unregisterForContext(com.bibin.visioneye.voice.VoiceScreenContext.ENROLL_PERSON)
            voiceController?.setDictationConsumer(null)
        }
    }

    BackHandler {
        if (isCameraActive) {
            cameraController.stopPersonEnrollment()
            cameraController.unbindCamera()
            isCameraActive = false
        } else {
            onCancel()
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            if (isCameraActive) {
                cameraController.stopPersonEnrollment()
                cameraController.unbindCamera()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = HighContrastBlack
    ) {
        if (!isCameraActive) {
            val step1ScrollState = rememberScrollState()

            // Step 1: Enter Name
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .verticalScroll(step1ScrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Cancel Button
                VisionHeader(
                    title = "SAVE PERSON",
                    subtitle = "Step 1: Identity Information",
                    onBack = onCancel,
                    backLabel = "Cancel enrollment"
                )

                VoiceControlButton(
                    voiceController = voiceController,
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = HighContrastCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PeoplePurple)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "👤",
                                fontSize = 18.sp,
                                modifier = Modifier.semantics { hideFromAccessibility() }
                            )
                            Text(
                                text = "Who are you enrolling?",
                                style = MaterialTheme.typography.titleMedium,
                                color = PeoplePurple,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Enter the name VisionEye should speak when this person is seen in camera view (e.g. \"Father\", \"Mother\", or \"Arun\").",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HighContrastWhite
                        )
                    }
                }

                OutlinedTextField(
                    value = personNameInput,
                    onValueChange = { personNameInput = it },
                    label = { Text("Person Name", color = HighContrastYellow) },
                    placeholder = { Text("e.g. Father", color = HighContrastTextMuted) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HighContrastYellow,
                        unfocusedBorderColor = HighContrastBorder,
                        focusedTextColor = HighContrastWhite,
                        unfocusedTextColor = HighContrastWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Enter person name"
                        }
                )

                // Quick Suggestion Chips
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Quick suggestions:",
                        style = MaterialTheme.typography.labelMedium,
                        color = HighContrastTextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Father", "Mother", "Arun").forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HighContrastCard)
                                    .border(1.5.dp, HighContrastYellow, RoundedCornerShape(8.dp))
                                    .clickable { personNameInput = suggestion }
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "Set name to $suggestion"
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = suggestion,
                                    color = HighContrastYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                // Informed Consent Checkbox / Row
                val consentBorderColor = if (hasConsent) ReadGreen else HighContrastBorder
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { hasConsent = !hasConsent }
                        .semantics {
                            role = Role.Checkbox
                            contentDescription = if (hasConsent) {
                                "Consent obtained. Explicit consent granted."
                            } else {
                                "Consent obtained not checked. Explicit consent required before capture."
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = HighContrastCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, consentBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = hasConsent,
                            onCheckedChange = { hasConsent = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = ReadGreen,
                                uncheckedColor = HighContrastWhite,
                                checkmarkColor = HighContrastBlack
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Consent obtained",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (hasConsent) ReadGreen else HighContrastWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Explicit informed consent to capture and locally store biometric face embeddings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HighContrastTextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Start Camera Capture Button
                val isNameValid = personNameInput.isNotBlank()
                val isReadyToCapture = isNameValid && hasConsent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isReadyToCapture) PeoplePurple else HighContrastCard)
                        .border(
                            2.dp,
                            if (isReadyToCapture) HighContrastWhite else HighContrastBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = isReadyToCapture) {
                            if (!hasCameraPermission) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                isCameraActive = true
                                cameraController.startPersonEnrollment(personNameInput.trim())
                            }
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = when {
                                !isNameValid -> "Start face capture disabled. Enter a person name first."
                                !hasConsent -> "Start face capture disabled. Consent obtained must be checked."
                                else -> "Start face capture for ${personNameInput.trim()}"
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "START FACE CAPTURE",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isReadyToCapture) HighContrastWhite else HighContrastTextMuted,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "📷",
                            fontSize = 18.sp,
                            modifier = Modifier.semantics { hideFromAccessibility() }
                        )
                    }
                }
            }
        } else {
            // Step 2: Live Camera Capture & Guidance HUD
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            cameraController.bindCamera(lifecycleOwner, this.surfaceProvider)
                        }
                    }
                )

                // Top & Bottom Overlays
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 80.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HighContrastBlack.copy(alpha = 0.85f))
                                .border(2.dp, HighContrastYellow, RoundedCornerShape(10.dp))
                                .clickable {
                                    cameraController.stopPersonEnrollment()
                                    cameraController.unbindCamera()
                                    isCameraActive = false
                                }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Cancel enrollment"
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "◀",
                                    color = HighContrastYellow,
                                    fontSize = 14.sp,
                                    modifier = Modifier.semantics { hideFromAccessibility() }
                                )
                                Text(
                                    text = "CANCEL",
                                    color = HighContrastYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HighContrastBlack.copy(alpha = 0.85f))
                                .border(2.dp, PeoplePurple, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ENROLLING: ${enrollmentState.personName.uppercase()}",
                                color = HighContrastWhite,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    VoiceControlButton(
                        voiceController = voiceController,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Middle: Diagnostics HUD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = HighContrastBlack.copy(alpha = 0.92f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HighContrastCyan)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🛠",
                                    fontSize = 14.sp,
                                    modifier = Modifier.semantics { hideFromAccessibility() }
                                )
                                Text(
                                    text = "ENROLLMENT DIAGNOSTICS (DEV)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HighContrastCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "CAMERA: ${if (enrollmentState.diagnostics.isCameraConnected) "CONNECTED" else "WAITING"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (enrollmentState.diagnostics.isCameraConnected) ReadGreen else HighContrastYellow,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ACCEPTED: ${enrollmentState.diagnostics.acceptedSamples}/5",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (enrollmentState.diagnostics.acceptedSamples > 0) ReadGreen else HighContrastYellow,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "LAST FACE STATE: ${enrollmentState.diagnostics.lastFaceState}",
                                style = MaterialTheme.typography.bodySmall,
                                color = HighContrastWhite
                            )
                            Text(
                                text = "LAST REJECTION: ${enrollmentState.diagnostics.lastRejectionReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (enrollmentState.diagnostics.lastRejectionReason.startsWith("NONE")) ReadGreen else HighContrastYellow
                            )
                        }
                    }

                    // Bottom Guidance HUD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = HighContrastBlack.copy(alpha = 0.92f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (enrollmentState.isComplete) ReadGreen else HighContrastYellow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (enrollmentState.isComplete) "ENROLLMENT COMPLETE" else "FACE GUIDANCE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (enrollmentState.isComplete) ReadGreen else HighContrastYellow,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "${enrollmentState.samplesCaptured} / ${enrollmentState.targetSamples} SAMPLES",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HighContrastCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Progress Bar
                            val progress = (enrollmentState.samplesCaptured.toFloat() / enrollmentState.targetSamples.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = if (enrollmentState.isComplete) ReadGreen else PeoplePurple,
                                trackColor = HighContrastCard
                            )

                            // Guidance Message
                            Text(
                                text = enrollmentState.guidanceMessage,
                                style = MaterialTheme.typography.titleMedium,
                                color = HighContrastWhite,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics {
                                    contentDescription = enrollmentState.guidanceMessage
                                }
                            )

                            if (enrollmentState.isComplete) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ReadGreen)
                                        .clickable {
                                            cameraController.stopPersonEnrollment()
                                            cameraController.unbindCamera()
                                            onEnrollmentComplete()
                                        }
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Done. Finish enrollment and return to main screen."
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "DONE (RETURN TO MAIN)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HighContrastBlack,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
