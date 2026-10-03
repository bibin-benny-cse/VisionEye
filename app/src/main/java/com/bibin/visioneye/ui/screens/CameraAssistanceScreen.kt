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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.bibin.visioneye.ai.Detection
import com.bibin.visioneye.ai.PreviewCoordinateMapper
import com.bibin.visioneye.camera.CameraController
import com.bibin.visioneye.camera.CameraState
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.ui.theme.HighContrastBlack
import com.bibin.visioneye.ui.theme.HighContrastBorder
import com.bibin.visioneye.ui.theme.HighContrastCard
import com.bibin.visioneye.ui.theme.HighContrastCyan
import com.bibin.visioneye.ui.theme.HighContrastSurface
import com.bibin.visioneye.ui.theme.HighContrastTextMuted
import com.bibin.visioneye.ui.theme.HighContrastWhite
import com.bibin.visioneye.ui.theme.HighContrastYellow
import com.bibin.visioneye.ui.theme.NavigateBlue
import com.bibin.visioneye.ui.theme.ReadGreen

/**
 * Live Camera Assistance Screen for VisionEye.
 *
 * Provides:
 * - Real-time rear camera preview via CameraX and Compose [AndroidView]
 * - Runtime CAMERA permission request handling
 * - Clean shutdown of camera resources via BackHandler and onDispose
 * - Accessible high-contrast status overlays and controls
 */
@Composable
fun CameraAssistanceScreen(
    currentMode: VisionMode,
    cameraController: CameraController,
    onStopCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraState by cameraController.state.collectAsState()
    val diagnostics by cameraController.diagnostics.collectAsState()
    val yoloState by cameraController.yoloState.collectAsState()

    var showDebugOverlay by remember {
        mutableStateOf(true) // Development-only bounding-box overlay active in dev
    }

    var hasCameraPermission by remember {
        mutableStateOf(cameraController.checkCameraPermission())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            cameraController.startCamera()
        }
    }

    // Intercept hardware back button to safely unbind camera and return
    BackHandler {
        cameraController.stopCamera()
        onStopCamera()
    }

    // Safely unbind and release camera whenever this screen leaves composition
    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraController.unbindCamera()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = HighContrastBlack
    ) {
        if (!hasCameraPermission) {
            // Permission request screen
            CameraPermissionPrompt(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onCancel = {
                    cameraController.stopCamera()
                    onStopCamera()
                }
            )
        } else {
            // Live camera view and HUD
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. CameraX Live Preview
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                            // Bind camera when PreviewView surface provider is available
                            cameraController.bindCamera(lifecycleOwner, this.surfaceProvider)
                        }
                    },
                    update = { previewView ->
                        // Re-bind if necessary when state changes
                        if (cameraState is CameraState.Idle) {
                            cameraController.bindCamera(lifecycleOwner, previewView.surfaceProvider)
                        }
                    }
                )

                // 2. Development-only Bounding-Box Overlay (NAVIGATE mode)
                if (showDebugOverlay && currentMode == VisionMode.NAVIGATE && yoloState.isReady && yoloState.detections.isNotEmpty()) {
                    DetectionOverlay(
                        detections = yoloState.detections,
                        streamWidth = diagnostics.imageWidth,
                        streamHeight = diagnostics.imageHeight
                    )
                }

                // 3. High-Contrast Overlay HUD
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar: Back/Stop button and Mode/State indicator
                    TopBarControls(
                        currentMode = currentMode,
                        cameraState = cameraState,
                        onStopCamera = {
                            cameraController.stopCamera()
                            onStopCamera()
                        }
                    )

                    // Bottom Bar: Status information, diagnostics, and YOLO dev status
                    BottomStatusOverlay(
                        currentMode = currentMode,
                        cameraState = cameraState,
                        diagnostics = diagnostics,
                        yoloState = yoloState,
                        showDebugOverlay = showDebugOverlay,
                        onToggleOverlay = { showDebugOverlay = !showDebugOverlay }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarControls(
    currentMode: VisionMode,
    cameraState: CameraState,
    onStopCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stop / Back Action Button
        Box(
            modifier = Modifier
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HighContrastBlack.copy(alpha = 0.85f))
                .border(2.dp, HighContrastYellow, RoundedCornerShape(12.dp))
                .clickable(onClick = onStopCamera)
                .semantics {
                    role = Role.Button
                    contentDescription = "Stop camera assistance and return to mode selection"
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "◀",
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "STOP CAMERA",
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // Mode and Live Status Badge
        Box(
            modifier = Modifier
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HighContrastBlack.copy(alpha = 0.85f))
                .border(2.dp, NavigateBlue, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val statusDotColor = when (cameraState) {
                    is CameraState.Streaming -> ReadGreen
                    is CameraState.Initializing -> HighContrastYellow
                    is CameraState.Error -> Color.Red
                    else -> HighContrastTextMuted
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )

                Text(
                    text = currentMode.displayName.uppercase(),
                    color = HighContrastWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun BottomStatusOverlay(
    currentMode: VisionMode,
    cameraState: CameraState,
    diagnostics: com.bibin.visioneye.camera.FrameAnalysisDiagnostics,
    yoloState: com.bibin.visioneye.ai.YoloDebugState,
    showDebugOverlay: Boolean = true,
    onToggleOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, HighContrastBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = HighContrastBlack.copy(alpha = 0.88f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CAMERA FEED (REAR)",
                    style = MaterialTheme.typography.labelMedium,
                    color = HighContrastCyan,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = when (cameraState) {
                        is CameraState.Streaming -> "LIVE SCANNING"
                        is CameraState.Initializing -> "INITIALIZING..."
                        is CameraState.Paused -> "PAUSED"
                        is CameraState.Error -> "ERROR"
                        is CameraState.Idle -> "IDLE"
                        is CameraState.PermissionRequired -> "PERMISSION REQUIRED"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (cameraState) {
                        is CameraState.Streaming -> ReadGreen
                        is CameraState.Error -> Color.Red
                        else -> HighContrastYellow
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            if (cameraState is CameraState.Error) {
                Text(
                    text = cameraState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red
                )
            } else {
                Text(
                    text = "Scanning forward path. Obstacle detection pipeline connected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HighContrastWhite
                )

                // Lightweight development frame-analysis status
                if (cameraState is CameraState.Streaming) {
                    val analysisText = if (diagnostics.analyzedFrameCount > 0L) {
                        val fpsFormatted = String.format(java.util.Locale.US, "%.1f", diagnostics.approximateFps)
                        "Analysis: $fpsFormatted FPS (target: 5.0) • Frame #${diagnostics.analyzedFrameCount} (${diagnostics.imageWidth}×${diagnostics.imageHeight})"
                    } else {
                        "Analysis: Initializing frame pipeline (5.0 FPS target)..."
                    }
                    Text(
                        text = analysisText,
                        style = MaterialTheme.typography.labelSmall,
                        color = HighContrastCyan,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Development-only YOLO status (NAVIGATE mode)
                    if (currentMode == VisionMode.NAVIGATE) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HighContrastCard, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DEV: YOLOv8n STATUS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HighContrastYellow,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (yoloState.isReady) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (showDebugOverlay) HighContrastYellow else HighContrastSurface)
                                                    .border(1.dp, HighContrastBorder, RoundedCornerShape(6.dp))
                                                    .clickable { onToggleOverlay() }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (showDebugOverlay) "BOXES: ON" else "BOXES: OFF",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (showDebugOverlay) HighContrastBlack else HighContrastWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (yoloState.isReady) "READY (${yoloState.inferenceTimeMs} ms)" else yoloState.statusMessage,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (yoloState.isReady) ReadGreen else HighContrastYellow,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (yoloState.isReady) {
                                    if (yoloState.detections.isNotEmpty()) {
                                        Text(
                                            text = "Objects (${yoloState.detections.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HighContrastCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            yoloState.detections.take(4).forEach { det ->
                                                Text(
                                                    text = "${det.className} ${(det.confidence * 100).toInt()}% ${det.position.name}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = HighContrastWhite,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        // Context-Aware Decision Engine: Selected Prioritized Alerts
                                        if (yoloState.selectedAlerts.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "SELECTED ALERTS (${yoloState.selectedAlerts.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HighContrastYellow,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                yoloState.selectedAlerts.forEach { alert ->
                                                    Text(
                                                        text = "• ${alert.message}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = HighContrastWhite,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                if (yoloState.suppressedAlertCount > 0) {
                                                    Text(
                                                        text = "Suppressed: ${yoloState.suppressedAlertCount} detection(s)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = HighContrastTextMuted
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Objects: 0 detected",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HighContrastTextMuted
                                        )
                                    }
                                } else {
                                    Text(
                                        text = yoloState.statusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HighContrastTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "⚠️ White cane recommended. Prototype assistive system.",
                style = MaterialTheme.typography.labelSmall,
                color = HighContrastTextMuted
            )
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, HighContrastYellow, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "📷",
                    fontSize = 48.sp
                )

                Text(
                    text = "CAMERA PERMISSION REQUIRED",
                    style = MaterialTheme.typography.titleLarge,
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "VisionEye needs access to your phone's rear camera to detect obstacles, guide navigation, and inspect walking paths.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = HighContrastWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Grant Permission Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HighContrastYellow)
                        .clickable(onClick = onRequestPermission)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Grant camera permission"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GRANT PERMISSION",
                        style = MaterialTheme.typography.titleMedium,
                        color = HighContrastBlack,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Cancel Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HighContrastCard)
                        .border(1.dp, HighContrastBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onCancel)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Cancel and return to mode selection"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CANCEL & RETURN",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Development-only bounding-box and position visualization overlay.
 *
 * Renders on-screen bounding boxes with object class, confidence score,
 * and horizontal position (LEFT, CENTER, RIGHT).
 * Projects normalized detection coordinates into display view space
 * matching [PreviewView.ScaleType.FILL_CENTER].
 */
@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    streamWidth: Int,
    streamHeight: Int,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height

        val effectiveStreamW = if (streamWidth > 0) streamWidth.toFloat() else 480f
        val effectiveStreamH = if (streamHeight > 0) streamHeight.toFloat() else 640f

        detections.forEach { det ->
            val viewRect = PreviewCoordinateMapper.mapBoxToView(
                box = det.boundingBox,
                viewWidth = viewW,
                viewHeight = viewH,
                streamWidth = effectiveStreamW,
                streamHeight = effectiveStreamH
            )

            // 1. Draw bounding box rectangle
            drawRect(
                color = HighContrastYellow,
                topLeft = Offset(viewRect.left, viewRect.top),
                size = Size(viewRect.width, viewRect.height),
                style = Stroke(width = 3.dp.toPx())
            )

            // 2. Measure label pill text: e.g. "refrigerator 80% RIGHT"
            val labelText = "${det.className} ${(det.confidence * 100).toInt()}% ${det.position.name}"
            val textLayoutResult = textMeasurer.measure(
                text = labelText,
                style = TextStyle(
                    color = HighContrastBlack,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            val badgePadHorizontal = 6.dp.toPx()
            val badgePadVertical = 3.dp.toPx()
            val badgeWidth = textLayoutResult.size.width + badgePadHorizontal * 2
            val badgeHeight = textLayoutResult.size.height + badgePadVertical * 2

            // Position label badge above box if possible, or inside top of box if at upper boundary
            val badgeLeft = viewRect.left.coerceIn(0f, (viewW - badgeWidth).coerceAtLeast(0f))
            val badgeTop = if (viewRect.top >= badgeHeight) {
                viewRect.top - badgeHeight
            } else {
                viewRect.top
            }

            // Draw badge background pill
            drawRoundRect(
                color = HighContrastYellow,
                topLeft = Offset(badgeLeft, badgeTop),
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Draw label text inside badge
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    badgeLeft + badgePadHorizontal,
                    badgeTop + badgePadVertical
                )
            )
        }
    }
}

