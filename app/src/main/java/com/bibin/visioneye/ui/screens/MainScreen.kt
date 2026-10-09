package com.bibin.visioneye.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibin.visioneye.core.mode.VisionMode
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
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = HighContrastBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "VISIONEYE",
                style = MaterialTheme.typography.headlineLarge,
                color = HighContrastYellow,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.semantics {
                    contentDescription = "VisionEye Application Header"
                }
            )

            Text(
                text = "AI Assistance for Visually Impaired Users",
                style = MaterialTheme.typography.bodyMedium,
                color = HighContrastTextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Assistance Actions (Prominent Direct Entry Points)
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PRIMARY ASSISTANCE",
                        style = MaterialTheme.typography.labelLarge,
                        color = HighContrastYellow,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 1. Start Navigation Camera Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavigateBlue)
                            .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                            .clickable(onClick = onStartCamera)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Start Navigation Assistance. Opens camera to detect obstacles and guide walking path. Double tap to activate."
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(text = "🚶", fontSize = 26.sp)
                            Column {
                                Text(
                                    text = "START NAVIGATION",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HighContrastWhite,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Live obstacle detection & alerts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HighContrastWhite.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // 2. Read Text Button (Direct entry to READ Mode)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ReadGreen)
                            .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                            .clickable(onClick = onStartReadMode)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Read Text. Opens camera to scan English pages, documents, and signs and read them aloud. Double tap to activate."
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(text = "📖", fontSize = 26.sp)
                            Column {
                                Text(
                                    text = "READ TEXT",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HighContrastBlack,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Scan English document & speak aloud",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HighContrastBlack.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 3. Scan Currency Button (Direct entry to CURRENCY Mode)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CurrencyGold)
                            .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                            .clickable(onClick = onStartCurrencyMode)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Scan Currency. Opens camera to identify Indian rupee banknotes and announce their denomination. Double tap to activate."
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(text = "💵", fontSize = 26.sp)
                            Column {
                                Text(
                                    text = "SCAN CURRENCY",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HighContrastBlack,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Identify Indian banknotes (₹10 - ₹500)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HighContrastBlack.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 3. Recognize People Button (Direct entry to PEOPLE Mode Face Recognition)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PeoplePurple)
                            .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                            .clickable(onClick = onStartPeopleMode)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Recognize People. Opens camera to identify saved faces like Father or Mother. Double tap to activate."
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(text = "👥", fontSize = 26.sp)
                            Column {
                                Text(
                                    text = "RECOGNIZE PEOPLE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HighContrastWhite,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Identify saved individuals by name",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HighContrastWhite.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // People Sub-actions: SAVE PERSON & MANAGE SAVED PEOPLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HighContrastCard)
                                .border(1.dp, PeoplePurple, RoundedCornerShape(10.dp))
                                .clickable(onClick = onSavePerson)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Save Person. Enroll new face into local storage. Double tap to activate."
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "➕ SAVE PERSON",
                                style = MaterialTheme.typography.labelMedium,
                                color = PeoplePurple,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HighContrastCard)
                                .border(1.dp, HighContrastBorder, RoundedCornerShape(10.dp))
                                .clickable(onClick = onManagePeople)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Manage Saved People. View and delete saved face profiles. Double tap to activate."
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📋 MANAGE PEOPLE",
                                style = MaterialTheme.typography.labelMedium,
                                color = HighContrastWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Active Mode Banner
            ActiveModeBanner(
                mode = currentMode,
                onStartCamera = onStartCamera,
                onStartReadMode = onStartReadMode,
                onStartCurrencyMode = onStartCurrencyMode,
                onStartPeopleMode = onStartPeopleMode,
                onSavePerson = onSavePerson,
                onManagePeople = onManagePeople
            )

            // Architecture Mode Rule Card
            ArchitectureRuleCard(currentMode = currentMode)

            // Safety Prototype Notice
            SafetyNoticeCard()

            // Mode Selector Heading
            Text(
                text = "SELECT OPERATIONAL MODE",
                style = MaterialTheme.typography.titleLarge,
                color = HighContrastWhite,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            // Mode Grid Buttons
            VisionMode.entries.forEach { mode ->
                ModeSelectionButton(
                    mode = mode,
                    isSelected = mode == currentMode,
                    onClick = {
                        if (mode == VisionMode.READ) {
                            onStartReadMode()
                        } else if (mode == VisionMode.CURRENCY) {
                            onStartCurrencyMode()
                        } else if (mode == VisionMode.PEOPLE) {
                            onStartPeopleMode()
                        } else if (mode == VisionMode.NAVIGATE) {
                            if (mode == currentMode) {
                                onStartCamera()
                            } else {
                                onModeSelected(mode)
                            }
                        } else {
                            onModeSelected(mode)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActiveModeBanner(
    mode: VisionMode,
    onStartCamera: () -> Unit,
    onStartReadMode: () -> Unit = {},
    onStartCurrencyMode: () -> Unit = {},
    onStartPeopleMode: () -> Unit = {},
    onSavePerson: () -> Unit = {},
    onManagePeople: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accentColor = getModeAccentColor(mode)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, accentColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ACTIVE MODE",
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                letterSpacing = 1.sp
            )

            Text(
                text = mode.displayName.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = HighContrastWhite,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodyLarge,
                color = HighContrastWhite,
                textAlign = TextAlign.Center
            )

            // Direct camera assistance button when in NAVIGATE mode
            if (mode == VisionMode.NAVIGATE) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavigateBlue)
                        .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                        .clickable(onClick = onStartCamera)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Open live navigation camera assistance. Double tap to start rear camera scanning."
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "📷", fontSize = 22.sp)
                        Text(
                            text = "START CAMERA ASSISTANCE",
                            style = MaterialTheme.typography.titleMedium,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Direct camera reading button when in READ mode
            if (mode == VisionMode.READ) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ReadGreen)
                        .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                        .clickable(onClick = onStartReadMode)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Start reading. Opens camera to scan English text and read aloud. Double tap to start."
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "📖", fontSize = 22.sp)
                        Text(
                            text = "START READING (CAMERA)",
                            style = MaterialTheme.typography.titleMedium,
                            color = HighContrastBlack,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Direct camera currency scanning button when in CURRENCY mode
            if (mode == VisionMode.CURRENCY) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CurrencyGold)
                        .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                        .clickable(onClick = onStartCurrencyMode)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Start currency scanning. Opens camera to identify Indian rupee banknotes and announce value. Double tap to start."
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "💵", fontSize = 22.sp)
                        Text(
                            text = "START CURRENCY SCANNER",
                            style = MaterialTheme.typography.titleMedium,
                            color = HighContrastBlack,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Direct camera scanning and management buttons when in PEOPLE mode
            if (mode == VisionMode.PEOPLE) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PeoplePurple)
                        .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                        .clickable(onClick = onStartPeopleMode)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Start face recognition. Opens camera to identify saved people. Double tap to start."
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "👥", fontSize = 22.sp)
                        Text(
                            text = "START FACE RECOGNITION",
                            style = MaterialTheme.typography.titleMedium,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HighContrastCard)
                            .border(1.dp, PeoplePurple, RoundedCornerShape(10.dp))
                            .clickable(onClick = onSavePerson)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Save person. Enroll new face into device."
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "➕ SAVE PERSON",
                            style = MaterialTheme.typography.labelMedium,
                            color = PeoplePurple,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HighContrastCard)
                            .border(1.dp, HighContrastBorder, RoundedCornerShape(10.dp))
                            .clickable(onClick = onManagePeople)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Manage saved people. View and delete profiles."
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📋 MANAGE PEOPLE",
                            style = MaterialTheme.typography.labelMedium,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HighContrastCard, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "📢 \"${mode.announcement}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HighContrastCyan,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ArchitectureRuleCard(
    currentMode: VisionMode,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HighContrastCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HighContrastBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(HighContrastYellow)
                )
                Text(
                    text = "Mode-Based AI Architecture",
                    style = MaterialTheme.typography.labelLarge,
                    color = HighContrastYellow
                )
            }

            Text(
                text = "Only the AI and sensor pipelines required for ${currentMode.displayName} mode are active. All other models are dormant to prevent thermal throttling and battery drain.",
                style = MaterialTheme.typography.bodyMedium,
                color = HighContrastTextMuted
            )
        }
    }
}

@Composable
private fun ModeSelectionButton(
    mode: VisionMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = getModeAccentColor(mode)
    val isSos = mode == VisionMode.SOS

    val backgroundColor = when {
        isSelected -> accentColor
        isSos -> HighContrastSurface
        else -> HighContrastCard
    }

    val textColor = when {
        isSelected -> HighContrastBlack
        isSos -> EmergencyRed
        else -> HighContrastWhite
    }

    val borderColor = when {
        isSelected -> HighContrastWhite
        isSos -> EmergencyRed
        else -> HighContrastBorder
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${mode.displayName} Mode. ${mode.description}. " +
                        if (isSelected) "Currently active." else "Double tap to activate."
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = mode.displayName.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) HighContrastBlack else HighContrastTextMuted,
                    maxLines = 1
                )
            }

            if (isSelected) {
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = HighContrastBlack,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun SafetyNoticeCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HighContrastCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HighContrastYellow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.titleLarge
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Safety Notice (Prototype)",
                    style = MaterialTheme.typography.labelLarge,
                    color = HighContrastYellow
                )
                Text(
                    text = "VisionEye is an assistive prototype and NOT a replacement for a white cane. It is not a guaranteed safety system.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HighContrastWhite
                )
            }
        }
    }
}

private fun getModeAccentColor(mode: VisionMode): Color {
    return when (mode) {
        VisionMode.NAVIGATE -> NavigateBlue
        VisionMode.READ -> ReadGreen
        VisionMode.CURRENCY -> CurrencyGold
        VisionMode.PEOPLE -> PeoplePurple
        VisionMode.NAVIGATION -> NavigationTeal
        VisionMode.SOS -> EmergencyRed
    }
}
