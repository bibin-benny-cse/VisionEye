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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bibin.visioneye.camera.CameraController
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
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val enrollmentState by cameraController.enrollmentState.collectAsState()

    var personNameInput by remember { mutableStateOf("") }
    var isCameraActive by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(cameraController.checkCameraPermission())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted && personNameInput.isNotBlank()) {
            isCameraActive = true
            cameraController.startPersonEnrollment(personNameInput.trim())
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
            // Step 1: Enter Name
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(HighContrastCard)
                            .border(2.dp, HighContrastYellow, RoundedCornerShape(12.dp))
                            .clickable(onClick = onCancel)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Cancel enrollment"
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "◀ CANCEL", color = HighContrastYellow, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "SAVE PERSON",
                        style = MaterialTheme.typography.titleLarge,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = HighContrastCard),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PeoplePurple)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "👤 Step 1: Who are you enrolling?",
                            style = MaterialTheme.typography.titleMedium,
                            color = PeoplePurple,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enter the name VisionEye should speak when this person is seen (e.g. \"Father\", \"Mother\", or \"Arun\").",
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
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = HighContrastWhite, fontWeight = FontWeight.Bold),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Father", "Mother", "Arun").forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HighContrastCard)
                                .border(1.dp, HighContrastYellow, RoundedCornerShape(8.dp))
                                .clickable { personNameInput = suggestion }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Set name to $suggestion"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = suggestion, color = HighContrastYellow, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Start Camera Capture Button
                val isNameValid = personNameInput.isNotBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isNameValid) PeoplePurple else HighContrastCard)
                        .border(2.dp, if (isNameValid) HighContrastWhite else HighContrastBorder, RoundedCornerShape(14.dp))
                        .clickable(enabled = isNameValid) {
                            if (!hasCameraPermission) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                isCameraActive = true
                                cameraController.startPersonEnrollment(personNameInput.trim())
                            }
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = if (isNameValid) "Start face capture for ${personNameInput.trim()}" else "Enter a person name first"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "START FACE CAPTURE 📷",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isNameValid) HighContrastWhite else HighContrastTextMuted,
                        fontWeight = FontWeight.ExtraBold
                    )
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
                        .padding(16.dp),
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
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HighContrastBlack.copy(alpha = 0.85f))
                                .border(2.dp, HighContrastYellow, RoundedCornerShape(12.dp))
                                .clickable {
                                    cameraController.stopPersonEnrollment()
                                    cameraController.unbindCamera()
                                    isCameraActive = false
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "◀ CANCEL", color = HighContrastYellow, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HighContrastBlack.copy(alpha = 0.85f))
                                .border(2.dp, PeoplePurple, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ENROLLING: ${enrollmentState.personName.uppercase()}",
                                color = HighContrastWhite,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Middle: Temporary Debug Diagnostics HUD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = HighContrastBlack.copy(alpha = 0.92f)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HighContrastCyan)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🛠 ENROLLMENT DIAGNOSTICS (DEBUG ONLY)",
                                style = MaterialTheme.typography.labelSmall,
                                color = HighContrastCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "CAMERA: ${if (enrollmentState.diagnostics.isCameraConnected) "CONNECTED" else "NOT CONNECTED"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (enrollmentState.diagnostics.isCameraConnected) ReadGreen else HighContrastYellow,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "FRAMES RECEIVED: ${enrollmentState.diagnostics.framesReceived}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HighContrastWhite
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "FACE DETECTIONS: ${enrollmentState.diagnostics.faceDetectionsCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HighContrastWhite
                                )
                                Text(
                                    text = "ACCEPTED SAMPLES: ${enrollmentState.diagnostics.acceptedSamples}/5",
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
                            enrollmentState.diagnostics.lastFaceMetrics?.let { metrics ->
                                Text(
                                    text = "METRICS: $metrics",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HighContrastTextMuted
                                )
                            }
                            enrollmentState.diagnostics.lastDetectorError?.let { err ->
                                Text(
                                    text = "DETECTOR ERROR: $err",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.ui.graphics.Color(0xFFFF1744),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Bottom Guidance HUD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = HighContrastBlack.copy(alpha = 0.90f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, if (enrollmentState.isComplete) ReadGreen else HighContrastYellow)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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

                            // Spoken / On-Screen Guidance Message
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
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ReadGreen)
                                        .clickable {
                                            cameraController.stopPersonEnrollment()
                                            cameraController.unbindCamera()
                                            onEnrollmentComplete()
                                        }
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Finish enrollment and return to main screen"
                                        },
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
