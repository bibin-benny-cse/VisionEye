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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

/**
 * Accessibility-first dashboard for VisionEye designed specifically for blind
 * and visually impaired users.
 *
 * Design features:
 * - WCAG AAA high-contrast dark theme (#000000 background)
 * - Large touch targets (minimum 52dp to 76dp height)
 * - Clear visual and logical hierarchy with reduced vertical scrolling
 * - Four prominent primary vision quick actions: NAVIGATE, READ, CURRENCY, and PEOPLE
 * - Clearly labeled secondary action for outdoor GPS NAVIGATION
 * - Visually and semantically distinct emergency action for SOS
 * - People directory tools (Save Person & Manage People) neatly integrated into the PEOPLE workflow
 * - Non-color selection indicators and compound accessibility announcements
 * - No nonfunctional microphone button
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
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =================================================================
            // 1. APPLICATION HEADER & SUBTITLE
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        heading()
                        contentDescription = "VisionEye. Assistive AI Mobility and Environmental Perception System."
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "VISIONEYE",
                    style = MaterialTheme.typography.headlineLarge,
                    color = HighContrastYellow,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "AI Assistive Mobility System",
                    style = MaterialTheme.typography.bodyLarge,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            // =================================================================
            // 2. SAFETY NOTICE CARD (PROTOTYPE NOTICE)
            // =================================================================
            SafetyNoticeCard()

            // =================================================================
            // 3. CURRENT ACTIVE MODE INDICATOR
            // =================================================================
            ActiveStatusBanner(currentMode = currentMode)

            // =================================================================
            // 4. PRIMARY VISION MODES (FOUR PROMINENT QUICK ACTIONS)
            // =================================================================
            Text(
                text = "PRIMARY VISION ASSISTANCE",
                style = MaterialTheme.typography.labelLarge,
                color = HighContrastYellow,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .semantics { heading() }
            )

            // A. NAVIGATE Mode Quick Action
            QuickActionCard(
                title = "NAVIGATE",
                subtitle = "Live obstacle detection & proximity alerts",
                iconGlyph = "🚶",
                accentColor = NavigateBlue,
                isSelected = currentMode == VisionMode.NAVIGATE,
                accessibilityLabel = "Navigate assistance. Real-time obstacle detection and proximity alerts.",
                onClick = onStartCamera
            )

            // B. READ TEXT Quick Action
            QuickActionCard(
                title = "READ TEXT",
                subtitle = "Scan printed documents, signs & speak aloud",
                iconGlyph = "📖",
                accentColor = ReadGreen,
                isSelected = currentMode == VisionMode.READ,
                accessibilityLabel = "Read text. Scans printed documents, signs, and labels and reads them aloud.",
                onClick = onStartReadMode
            )

            // C. SCAN CURRENCY Quick Action
            QuickActionCard(
                title = "SCAN CURRENCY",
                subtitle = "Identify Indian banknotes (₹10 to ₹500)",
                iconGlyph = "💵",
                accentColor = CurrencyGold,
                isSelected = currentMode == VisionMode.CURRENCY,
                accessibilityLabel = "Scan currency. Identifies Indian rupee banknotes from 10 to 500 rupees and announces denomination.",
                onClick = onStartCurrencyMode
            )

            // D. RECOGNIZE PEOPLE Quick Action (with People Directory workflow)
            PeopleWorkflowCard(
                isSelected = currentMode == VisionMode.PEOPLE,
                onStartPeopleMode = onStartPeopleMode,
                onSavePerson = onSavePerson,
                onManagePeople = onManagePeople
            )

            // =================================================================
            // 5. SECONDARY ACTION: OUTDOOR GPS NAVIGATION
            // =================================================================
            Text(
                text = "OUTDOOR WAYFINDING",
                style = MaterialTheme.typography.labelLarge,
                color = HighContrastYellow,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .semantics { heading() }
            )

            SecondaryActionCard(
                title = "OUTDOOR GPS NAVIGATION",
                subtitle = "Turn-by-turn pedestrian route guidance",
                iconGlyph = "🧭",
                accentColor = NavigationTeal,
                isSelected = currentMode == VisionMode.NAVIGATION,
                accessibilityLabel = "Outdoor GPS navigation. Turn-by-turn pedestrian route guidance and destination wayfinding.",
                onClick = { onModeSelected(VisionMode.NAVIGATION) }
            )

            // =================================================================
            // 6. EMERGENCY ACTION: SOS
            // =================================================================
            Text(
                text = "EMERGENCY ASSISTANCE",
                style = MaterialTheme.typography.labelLarge,
                color = EmergencyRed,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .semantics { heading() }
            )

            EmergencySosCard(
                isSelected = currentMode == VisionMode.SOS,
                onClick = { onModeSelected(VisionMode.SOS) }
            )

            // =================================================================
            // 7. COMPACT ARCHITECTURE FOOTNOTE
            // =================================================================
            ArchitectureFootnote(currentMode = currentMode)

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Prominent primary vision action card with large touch target, WCAG AAA contrast,
 * and clear non-color active status indicator.
 */
@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    iconGlyph: String,
    accentColor: Color,
    isSelected: Boolean,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) HighContrastWhite else HighContrastBorder
    val borderWidth = if (isSelected) 3.dp else 2.dp
    val containerColor = if (isSelected) HighContrastSurface else HighContrastCard

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (isSelected) "Active" else "Inactive"
                contentDescription = "$title. $accessibilityLabel"
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Color accent indicator bar with glyph
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iconGlyph,
                        fontSize = 24.sp,
                        modifier = Modifier.semantics { hideFromAccessibility() }
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HighContrastTextMuted,
                        maxLines = 1
                    )
                }
            }

            // Non-color selection indicator badge
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(HighContrastWhite)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
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
    }
}

/**
 * PEOPLE workflow card integrating face recognition scanning with direct access to
 * the People Directory (enrolling new faces and managing saved profiles).
 */
@Composable
private fun PeopleWorkflowCard(
    isSelected: Boolean,
    onStartPeopleMode: () -> Unit,
    onSavePerson: () -> Unit,
    onManagePeople: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) HighContrastWhite else HighContrastBorder
    val borderWidth = if (isSelected) 3.dp else 2.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = HighContrastCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main Face Recognition Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) HighContrastSurface else HighContrastCard)
                    .clickable(onClick = onStartPeopleMode)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        stateDescription = if (isSelected) "Active" else "Inactive"
                        contentDescription = "Recognize People. Face recognition for registered family and friends."
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PeoplePurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "👥",
                                fontSize = 24.sp,
                                modifier = Modifier.semantics { hideFromAccessibility() }
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "RECOGNIZE PEOPLE",
                                style = MaterialTheme.typography.titleLarge,
                                color = HighContrastWhite,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Identify saved individuals by name",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HighContrastTextMuted,
                                maxLines = 1
                            )
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(HighContrastWhite)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
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
            }

            // People Directory Sub-Actions: Enroll New Person & Manage Directory
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Enroll New Person Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HighContrastSurface)
                        .border(1.dp, PeoplePurple, RoundedCornerShape(8.dp))
                        .clickable(onClick = onSavePerson)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = "Save Person. Open camera to enroll a new face profile into local storage."
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "➕",
                            fontSize = 14.sp,
                            modifier = Modifier.semantics { hideFromAccessibility() }
                        )
                        Text(
                            text = "SAVE PERSON",
                            style = MaterialTheme.typography.labelLarge,
                            color = PeoplePurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 2. Manage Directory Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HighContrastSurface)
                        .border(1.dp, HighContrastBorder, RoundedCornerShape(8.dp))
                        .clickable(onClick = onManagePeople)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = "Manage People. View and manage registered face profiles."
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📋",
                            fontSize = 14.sp,
                            modifier = Modifier.semantics { hideFromAccessibility() }
                        )
                        Text(
                            text = "MANAGE PEOPLE",
                            style = MaterialTheme.typography.labelLarge,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Secondary outdoor GPS navigation action card.
 */
@Composable
private fun SecondaryActionCard(
    title: String,
    subtitle: String,
    iconGlyph: String,
    accentColor: Color,
    isSelected: Boolean,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) HighContrastWhite else HighContrastBorder
    val borderWidth = if (isSelected) 3.dp else 2.dp
    val containerColor = if (isSelected) HighContrastSurface else HighContrastCard

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (isSelected) "Active" else "Inactive"
                contentDescription = "$title. $accessibilityLabel"
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iconGlyph,
                        fontSize = 22.sp,
                        modifier = Modifier.semantics { hideFromAccessibility() }
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HighContrastTextMuted,
                        maxLines = 1
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(HighContrastWhite)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
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
    }
}

/**
 * Visually distinct Emergency SOS action card with maximum contrast and accessible wording.
 */
@Composable
private fun EmergencySosCard(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(EmergencyRed)
            .border(3.dp, HighContrastWhite, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (isSelected) "Active" else "Inactive"
                contentDescription = "Emergency SOS. Broadcast GPS location and notify emergency contacts."
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HighContrastWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🚨",
                        fontSize = 26.sp,
                        modifier = Modifier.semantics { hideFromAccessibility() }
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "EMERGENCY SOS",
                        style = MaterialTheme.typography.titleLarge,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Broadcast GPS location & notify contacts",
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(HighContrastWhite)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSelected) "✓ ACTIVE" else "ALERT",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmergencyRed,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Compact high-contrast status banner displaying the currently active mode
 * with both visual badge and TalkBack live announcement.
 */
@Composable
private fun ActiveStatusBanner(
    currentMode: VisionMode,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, HighContrastYellow, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Current active mode: ${currentMode.displayName}. ${currentMode.description}"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "ACTIVE MODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = currentMode.displayName.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = currentMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = HighContrastTextMuted,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(HighContrastYellow)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "READY",
                    style = MaterialTheme.typography.labelSmall,
                    color = HighContrastBlack,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Prototype safety notice emphasizing that VisionEye is a secondary aid to white canes.
 */
@Composable
private fun SafetyNoticeCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, HighContrastYellow, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = HighContrastCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Safety notice. VisionEye is an assistive prototype and not a replacement for a white cane or guide dog."
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { hideFromAccessibility() }
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Assistive Prototype Notice",
                    style = MaterialTheme.typography.labelSmall,
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Secondary aid to complement a cane or guide dog. Not a guaranteed collision prevention system.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HighContrastWhite
                )
            }
        }
    }
}

/**
 * Informational note confirming mode isolation.
 */
@Composable
private fun ArchitectureFootnote(
    currentMode: VisionMode,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HighContrastCard),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HighContrastBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Mode-Based AI Architecture",
                style = MaterialTheme.typography.labelSmall,
                color = HighContrastYellow,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Only the AI model required for ${currentMode.displayName} is running. Inactive models remain unloaded to preserve battery life and thermals.",
                style = MaterialTheme.typography.bodySmall,
                color = HighContrastTextMuted
            )
        }
    }
}
