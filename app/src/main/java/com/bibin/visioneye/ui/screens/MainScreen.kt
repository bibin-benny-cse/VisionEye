package com.bibin.visioneye.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.ui.components.VisionActionCard
import com.bibin.visioneye.ui.components.VisionButton
import com.bibin.visioneye.ui.components.VisionSafetyBanner
import com.bibin.visioneye.ui.components.VisionSectionTitle
import com.bibin.visioneye.ui.theme.CurrencyGold
import com.bibin.visioneye.ui.theme.EmergencyRed
import com.bibin.visioneye.ui.theme.HighContrastBlack
import com.bibin.visioneye.ui.theme.HighContrastBorder
import com.bibin.visioneye.ui.theme.HighContrastCard
import com.bibin.visioneye.ui.theme.HighContrastCyan
import com.bibin.visioneye.ui.theme.HighContrastSurface
import com.bibin.visioneye.ui.theme.HighContrastTextMuted
import com.bibin.visioneye.ui.theme.HighContrastWhite
import com.bibin.visioneye.ui.theme.HighContrastYellow
import com.bibin.visioneye.ui.theme.NavigateBlue
import com.bibin.visioneye.ui.theme.NavigationTeal
import com.bibin.visioneye.ui.theme.PeoplePurple
import com.bibin.visioneye.ui.theme.ReadGreen

/**
 * Accessibility-first dashboard for VisionEye designed for blind and visually impaired users.
 *
 * Design features:
 * - WCAG AAA high-contrast dark theme (#000000 background)
 * - Minimum 48dp+ touch targets across all interactive elements
 * - Compact top header respecting system status bar insets and display cutouts
 * - Compact, non-intrusive safety notice reinforcing white cane / guide dog primacy
 * - Concise active mode indicator avoiding redundant announcements
 * - Clear primary assistance modes with separate, non-conflicting People Directory buttons
 * - Section headings with Compose semantic heading() for predictable TalkBack navigation
 * - Responsive layouts that scale cleanly with system font sizes without text clipping
 */
@Composable
fun MainScreen(
    currentMode: VisionMode,
    onModeSelected: (VisionMode) -> Unit,
    onStartCamera: () -> Unit = {},
    onStartReadMode: () -> Unit = {},
    onStartCurrencyMode: () -> Unit = {},
    onStartPeopleMode: () -> Unit = {},
    onSavePerson: () -> Unit = {},
    onManagePeople: () -> Unit = {},
    voiceController: com.bibin.visioneye.voice.VoiceController? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    androidx.compose.runtime.DisposableEffect(voiceController) {
        if (voiceController != null) {
            voiceController.setScreenContext(com.bibin.visioneye.voice.VoiceScreenContext.MAIN_DASHBOARD)
            val actions = com.bibin.visioneye.voice.ScreenVoiceActions.createMainScreenActions(
                onStartCamera = onStartCamera,
                onStartReadMode = onStartReadMode,
                onStartCurrencyMode = onStartCurrencyMode,
                onStartPeopleMode = onStartPeopleMode,
                onSavePerson = onSavePerson,
                onManagePeople = onManagePeople,
                onStartNavigation = { onModeSelected(VisionMode.NAVIGATION) },
                onOpenSos = { onModeSelected(VisionMode.SOS) }
            )
            voiceController.registry.registerAll(actions)
        }
        onDispose {
            voiceController?.registry?.unregisterForContext(com.bibin.visioneye.voice.VoiceScreenContext.MAIN_DASHBOARD)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(HighContrastBlack),
        color = HighContrastBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // =================================================================
            // 1. COMPACT APPLICATION HEADER
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics(mergeDescendants = true) {
                        heading()
                        contentDescription = "VisionEye. AI Assistive Mobility."
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "VisionEye",
                    style = MaterialTheme.typography.headlineMedium,
                    color = HighContrastYellow,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "AI Assistive Mobility",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Medium
                )
            }

            // =================================================================
            // 2. COMPACT PROTOTYPE SAFETY NOTICE
            // =================================================================
            VisionSafetyBanner()

            // =================================================================
            // 2.5 ACCESSIBLE VOICE CONTROL BUTTON ("Speak a command")
            // =================================================================
            if (voiceController != null) {
                com.bibin.visioneye.ui.components.VoiceControlButton(voiceController = voiceController)
            }

            // =================================================================
            // 3. CONCISE CURRENT ACTIVE MODE INDICATOR
            // =================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, HighContrastYellow)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "Current mode: ${currentMode.displayName}."
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Current mode:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HighContrastTextMuted
                        )
                        Text(
                            text = currentMode.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HighContrastYellow)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "✓ ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = HighContrastBlack,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // =================================================================
            // 4. PRIMARY ASSISTANCE MODES
            // =================================================================
            VisionSectionTitle("PRIMARY ASSISTANCE")

            // A. NAVIGATE Mode Quick Action
            VisionActionCard(
                title = "NAVIGATE",
                subtitle = "Obstacle detection & proximity alerts",
                iconGlyph = "🚶",
                accentColor = NavigateBlue,
                isSelected = currentMode == VisionMode.NAVIGATE,
                accessibilityLabel = "Navigate. Live obstacle detection and proximity alerts.",
                onClick = onStartCamera
            )

            // B. READ TEXT Quick Action
            VisionActionCard(
                title = "READ TEXT",
                subtitle = "Scan documents, signs & speak aloud",
                iconGlyph = "📖",
                accentColor = ReadGreen,
                isSelected = currentMode == VisionMode.READ,
                accessibilityLabel = "Read text. Scans printed documents and reads them aloud.",
                onClick = onStartReadMode
            )

            // C. SCAN CURRENCY Quick Action
            VisionActionCard(
                title = "SCAN CURRENCY",
                subtitle = "Identify Indian banknotes (₹10 to ₹500)",
                iconGlyph = "💵",
                accentColor = CurrencyGold,
                isSelected = currentMode == VisionMode.CURRENCY,
                accessibilityLabel = "Scan currency. Identifies Indian rupee banknotes and announces denomination.",
                onClick = onStartCurrencyMode
            )

            // D. RECOGNIZE PEOPLE Quick Action
            VisionActionCard(
                title = "RECOGNIZE PEOPLE",
                subtitle = "Identify saved individuals by face",
                iconGlyph = "👥",
                accentColor = PeoplePurple,
                isSelected = currentMode == VisionMode.PEOPLE,
                accessibilityLabel = "Recognize people. Identifies saved individuals by face.",
                onClick = onStartPeopleMode
            )

            // People Directory Sub-Actions (Separated to prevent nested click conflicts)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VisionButton(
                    text = "SAVE PERSON",
                    iconGlyph = "➕",
                    backgroundColor = HighContrastSurface,
                    contentColor = PeoplePurple,
                    borderColor = PeoplePurple,
                    borderWidth = 1.dp,
                    minHeight = 48.dp,
                    accessibilityLabel = "Save person. Open camera to enroll a new face profile.",
                    onClick = onSavePerson,
                    modifier = Modifier.weight(1f)
                )

                VisionButton(
                    text = "MANAGE PEOPLE",
                    iconGlyph = "📋",
                    backgroundColor = HighContrastSurface,
                    contentColor = HighContrastWhite,
                    borderColor = HighContrastBorder,
                    borderWidth = 1.dp,
                    minHeight = 48.dp,
                    accessibilityLabel = "Manage people. View and manage registered face profiles.",
                    onClick = onManagePeople,
                    modifier = Modifier.weight(1f)
                )
            }

            // =================================================================
            // 5. OUTDOOR WAYFINDING
            // =================================================================
            VisionSectionTitle("OUTDOOR WAYFINDING")

            VisionActionCard(
                title = "OUTDOOR GPS NAVIGATION",
                subtitle = "Pedestrian route guidance & wayfinding",
                iconGlyph = "🧭",
                accentColor = NavigationTeal,
                isSelected = currentMode == VisionMode.NAVIGATION,
                accessibilityLabel = "Outdoor GPS navigation. Pedestrian route guidance and destination wayfinding.",
                onClick = { onModeSelected(VisionMode.NAVIGATION) }
            )

            // =================================================================
            // 6. EMERGENCY ASSISTANCE
            // =================================================================
            VisionSectionTitle("EMERGENCY ASSISTANCE", color = EmergencyRed)

            VisionActionCard(
                title = "EMERGENCY SOS",
                subtitle = "Broadcast GPS location & notify contacts",
                iconGlyph = "🚨",
                accentColor = EmergencyRed,
                isSelected = currentMode == VisionMode.SOS,
                cardContainerColor = EmergencyRed,
                badgeText = if (currentMode == VisionMode.SOS) "✓ ACTIVE" else "ALERT",
                badgeColor = HighContrastWhite,
                badgeTextColor = EmergencyRed,
                accessibilityLabel = "Emergency SOS. Broadcast GPS location and notify emergency contacts.",
                onClick = { onModeSelected(VisionMode.SOS) }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
