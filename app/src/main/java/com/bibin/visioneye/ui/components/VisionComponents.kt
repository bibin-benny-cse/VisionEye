package com.bibin.visioneye.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibin.visioneye.ui.theme.HighContrastBlack
import com.bibin.visioneye.ui.theme.HighContrastBorder
import com.bibin.visioneye.ui.theme.HighContrastCard
import com.bibin.visioneye.ui.theme.HighContrastSurface
import com.bibin.visioneye.ui.theme.HighContrastTextMuted
import com.bibin.visioneye.ui.theme.HighContrastWhite
import com.bibin.visioneye.ui.theme.HighContrastYellow

/**
 * Compact application and screen header respecting TalkBack heading hierarchy
 * and system bars.
 */
@Composable
fun VisionHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back to main menu",
    badgeText: String? = null,
    badgeColor: Color = HighContrastYellow,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 80.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HighContrastCard)
                    .border(2.dp, HighContrastYellow, RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .semantics {
                        role = Role.Button
                        contentDescription = backLabel
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
                        text = "BACK",
                        color = HighContrastYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (onBack != null) 10.dp else 0.dp)
                .semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = if (subtitle != null) "$title. $subtitle" else title
                },
            horizontalAlignment = if (onBack != null) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = HighContrastYellow,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.2f))
                    .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Compact, accessible prototype safety notice highlighting white cane / guide dog
 * priority without dominating screen space.
 */
@Composable
fun VisionSafetyBanner(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HighContrastCard),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HighContrastYellow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Safety notice: VisionEye is a supplementary assistive prototype. It does not replace a white cane or guide dog."
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 18.sp,
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
                    text = "Supplementary aid to white cane or guide dog. Not a guaranteed collision prevention system.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HighContrastWhite
                )
            }
        }
    }
}

/**
 * Reusable, high-contrast action card designed with minimum 48dp+ touch target,
 * responsive multi-line subtitle support, and TalkBack semantics.
 */
@Composable
fun VisionActionCard(
    title: String,
    subtitle: String,
    iconGlyph: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    accessibilityLabel: String = "$title. $subtitle",
    badgeText: String? = null,
    badgeColor: Color = HighContrastWhite,
    badgeTextColor: Color = HighContrastBlack,
    cardContainerColor: Color? = null
) {
    val borderColor = if (isSelected) HighContrastWhite else HighContrastBorder
    val borderWidth = if (isSelected) 3.dp else 1.5.dp
    val container = cardContainerColor ?: if (isSelected) HighContrastSurface else HighContrastCard

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (isSelected) "Active" else "Inactive"
                contentDescription = accessibilityLabel
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastTextMuted,
                        lineHeight = 16.sp
                    )
                }
            }

            val badgeToDisplay = badgeText ?: if (isSelected) "✓ ACTIVE" else null
            if (badgeToDisplay != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeToDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeTextColor,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * High-contrast semantic section title.
 */
@Composable
fun VisionSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = HighContrastYellow
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
            .semantics { heading() }
    )
}

/**
 * Accessible button with minimum 48dp+ touch target and high contrast styling.
 */
@Composable
fun VisionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconGlyph: String? = null,
    backgroundColor: Color = HighContrastSurface,
    contentColor: Color = HighContrastWhite,
    borderColor: Color = HighContrastBorder,
    borderWidth: Dp = 1.dp,
    minHeight: Dp = 48.dp,
    accessibilityLabel: String = text
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibilityLabel
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (iconGlyph != null) {
                Text(
                    text = iconGlyph,
                    fontSize = 16.sp,
                    color = contentColor,
                    modifier = Modifier.semantics { hideFromAccessibility() }
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
