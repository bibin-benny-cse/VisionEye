package com.bibin.visioneye.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibin.visioneye.ui.theme.EmergencyRed
import com.bibin.visioneye.ui.theme.HighContrastBlack
import com.bibin.visioneye.ui.theme.HighContrastBorder
import com.bibin.visioneye.ui.theme.HighContrastCard
import com.bibin.visioneye.ui.theme.HighContrastCyan
import com.bibin.visioneye.ui.theme.HighContrastWhite
import com.bibin.visioneye.ui.theme.HighContrastYellow
import com.bibin.visioneye.voice.VoiceController
import com.bibin.visioneye.voice.VoiceInputState

/**
 * Accessible, high-contrast, persistent voice-control button for blind and visually impaired users.
 *
 * Implements:
 * - Compact floating microphone button (58dp, WCAG AAA 56-64dp touch target) or full-width button
 * - TalkBack semantics with explicit button role, content description, and state description
 * - Single-utterance activation flow with cancel on re-tap
 * - Runtime microphone permission launcher
 * - Crisp vector high-contrast yellow microphone icon
 */
@Composable
fun VoiceControlButton(
    voiceController: VoiceController?,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    if (voiceController == null) return

    val voiceState by voiceController.state.collectAsState()
    val isListening = voiceController.isListening()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        voiceController.onPermissionResult(granted)
    }

    // Pulse animation when listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderPulse"
    )

    val currentBorderColor = when (voiceState) {
        is VoiceInputState.Listening,
        is VoiceInputState.Dictating -> HighContrastYellow.copy(alpha = pulseBorderAlpha)
        is VoiceInputState.Processing -> HighContrastCyan
        is VoiceInputState.Error -> EmergencyRed
        else -> HighContrastYellow
    }

    val currentContainerColor = when (voiceState) {
        is VoiceInputState.Listening,
        is VoiceInputState.Dictating -> HighContrastYellow
        is VoiceInputState.Processing -> HighContrastCard
        is VoiceInputState.SpeakingResult -> HighContrastCard
        else -> HighContrastBlack
    }

    val currentContentColor = when (voiceState) {
        is VoiceInputState.Listening,
        is VoiceInputState.Dictating -> HighContrastBlack
        is VoiceInputState.Processing -> HighContrastCyan
        is VoiceInputState.Error -> EmergencyRed
        else -> HighContrastYellow
    }

    val stateText = when (val s = voiceState) {
        is VoiceInputState.Listening -> "Listening..."
        is VoiceInputState.Dictating -> "Listening for ${s.fieldType.displayName}..."
        is VoiceInputState.Processing -> "Processing..."
        is VoiceInputState.SpeakingResult -> "Speaking..."
        is VoiceInputState.Error -> "Error"
        is VoiceInputState.RequestingPermission -> "Permission needed"
        VoiceInputState.Idle -> "Idle"
    }

    val buttonText = when (val s = voiceState) {
        is VoiceInputState.Listening -> "LISTENING..."
        is VoiceInputState.Dictating -> "SAY ${s.fieldType.displayName.uppercase()}..."
        is VoiceInputState.Processing -> "PROCESSING..."
        else -> "Speak a command"
    }

    val accessibilityDescription = when (voiceState) {
        is VoiceInputState.Listening -> "Listening for your voice command. Speak now."
        is VoiceInputState.Dictating -> "Dictation active. Say text to fill field."
        is VoiceInputState.Processing -> "Processing voice input."
        else -> "Speak a command. Activates single utterance voice input."
    }

    val clickAction = {
        if (isListening) {
            voiceController.stopListening()
        } else {
            if (!voiceController.checkMicrophonePermission()) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                voiceController.startListening()
            }
        }
    }

    if (isCompact) {
        // Compact floating microphone button (58dp touch target)
        val compactShape = CircleShape
        Box(
            modifier = modifier
                .size(58.dp)
                .clip(compactShape)
                .background(currentContainerColor)
                .border(2.5.dp, currentBorderColor, compactShape)
                .clickable(onClick = clickAction)
                .semantics {
                    role = Role.Button
                    contentDescription = accessibilityDescription
                    stateDescription = stateText
                },
            contentAlignment = Alignment.Center
        ) {
            MicrophoneIcon(
                color = currentContentColor,
                modifier = Modifier
                    .size(28.dp)
                    .semantics { hideFromAccessibility() }
            )
        }
    } else {
        // Standard full-width accessible action card button
        val fullWidthShape = RoundedCornerShape(12.dp)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(fullWidthShape)
                .background(currentContainerColor)
                .border(2.5.dp, currentBorderColor, fullWidthShape)
                .clickable(onClick = clickAction)
                .semantics {
                    role = Role.Button
                    contentDescription = accessibilityDescription
                    stateDescription = stateText
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MicrophoneIcon(
                    color = currentContentColor,
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { hideFromAccessibility() }
                )
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.titleMedium,
                    color = currentContentColor,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Crisp high-contrast vector microphone icon matching VisionEye's WCAG AAA theme.
 */
@Composable
fun MicrophoneIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Microphone head (rounded capsule)
        val headWidth = w * 0.38f
        val headHeight = h * 0.48f
        val headLeft = (w - headWidth) / 2f
        val headTop = h * 0.10f

        drawRoundRect(
            color = color,
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(headWidth / 2f, headWidth / 2f)
        )

        // U-shaped cradle
        val cradleStroke = (w * 0.085f).coerceAtLeast(2f)
        val cradleWidth = w * 0.62f
        val cradleHeight = h * 0.42f
        val cradleLeft = (w - cradleWidth) / 2f
        val cradleTop = headTop + headHeight * 0.20f

        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cradleLeft, cradleTop),
            size = Size(cradleWidth, cradleHeight),
            style = Stroke(width = cradleStroke, cap = StrokeCap.Round)
        )

        // Stem
        val stemTop = cradleTop + cradleHeight
        val stemBottom = h * 0.86f
        drawLine(
            color = color,
            start = Offset(w / 2f, stemTop),
            end = Offset(w / 2f, stemBottom),
            strokeWidth = cradleStroke,
            cap = StrokeCap.Round
        )

        // Base horizontal bar
        val baseWidth = w * 0.44f
        drawLine(
            color = color,
            start = Offset((w - baseWidth) / 2f, stemBottom),
            end = Offset((w + baseWidth) / 2f, stemBottom),
            strokeWidth = cradleStroke,
            cap = StrokeCap.Round
        )
    }
}
