package com.bibin.visioneye.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bibin.visioneye.emergency.EmergencyContact
import com.bibin.visioneye.emergency.EmergencyController
import com.bibin.visioneye.emergency.EmergencyState
import com.bibin.visioneye.ui.theme.EmergencyRed
import com.bibin.visioneye.ui.theme.HighContrastBlack
import com.bibin.visioneye.ui.theme.HighContrastBorder
import com.bibin.visioneye.ui.theme.HighContrastCard
import com.bibin.visioneye.ui.theme.HighContrastCyan
import com.bibin.visioneye.ui.theme.HighContrastSurface
import com.bibin.visioneye.ui.theme.HighContrastTextMuted
import com.bibin.visioneye.ui.theme.HighContrastWhite
import com.bibin.visioneye.ui.theme.HighContrastYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated high-contrast screen for VisionEye SOS emergency assistance.
 *
 * Implements:
 * - Large, accessible "TRIGGER SOS" control with haptic/auditory feedback
 * - Accidental trigger prevention countdown with immediate "CANCEL SOS" option
 * - In-app emergency contact configuration with phone number validation
 * - Clear visual and spoken indicators for location acquisition, transmission, and completion
 * - WCAG AAA compliance, large touch targets, and full TalkBack semantics
 *
 * @param emergencyController Controller orchestrating emergency dispatch and state.
 * @param onExitSos Callback invoked when the user exits SOS mode to return to dashboard.
 */
@Composable
fun SosScreen(
    emergencyController: EmergencyController,
    onExitSos: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val emergencyState by emergencyController.state.collectAsState()
    val scrollState = rememberScrollState()

    var contact by remember { mutableStateOf(emergencyController.contactRepository.getContact()) }
    var isEditingContact by remember { mutableStateOf(contact == null) }
    var contactNameInput by remember { mutableStateOf(contact?.name ?: "") }
    var contactPhoneInput by remember { mutableStateOf(contact?.phoneNumber ?: "") }
    var contactError by remember { mutableStateOf<String?>(null) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSmsPermission = granted
    }

    BackHandler {
        emergencyController.deactivate()
        onExitSos()
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EMERGENCY SOS",
                        style = MaterialTheme.typography.headlineMedium,
                        color = EmergencyRed,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.semantics {
                            contentDescription = "Emergency SOS Screen"
                        }
                    )
                    Text(
                        text = "Assistance & Location Dispatch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HighContrastTextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(EmergencyRed.copy(alpha = 0.2f))
                        .border(2.dp, EmergencyRed, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🚨", fontSize = 22.sp)
                }
            }

            // SMS Permission Banner if missing
            if (!hasSmsPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
                    border = BorderStroke(2.dp, HighContrastYellow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚠️ SMS PERMISSION REQUIRED",
                            style = MaterialTheme.typography.titleSmall,
                            color = HighContrastYellow,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Grant SMS permission so VisionEye can automatically send emergency coordinates to your contact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HighContrastWhite
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HighContrastYellow)
                                .clickable { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Grant SMS Permission button"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "GRANT PERMISSION",
                                style = MaterialTheme.typography.labelLarge,
                                color = HighContrastBlack,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Emergency Contact Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HighContrastCard),
                border = BorderStroke(1.dp, HighContrastBorder),
                shape = RoundedCornerShape(14.dp)
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
                            text = "EMERGENCY CONTACT",
                            style = MaterialTheme.typography.labelLarge,
                            color = HighContrastYellow,
                            fontWeight = FontWeight.Bold
                        )

                        if (!isEditingContact && contact != null) {
                            Text(
                                text = "EDIT",
                                style = MaterialTheme.typography.labelMedium,
                                color = HighContrastCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        contactNameInput = contact?.name ?: ""
                                        contactPhoneInput = contact?.phoneNumber ?: ""
                                        isEditingContact = true
                                    }
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "Edit emergency contact"
                                    }
                            )
                        }
                    }

                    if (isEditingContact) {
                        OutlinedTextField(
                            value = contactNameInput,
                            onValueChange = { contactNameInput = it },
                            label = { Text("Contact Name (e.g. Mother, Guardian)", color = HighContrastTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = HighContrastWhite,
                                unfocusedTextColor = HighContrastWhite,
                                focusedBorderColor = HighContrastYellow,
                                unfocusedBorderColor = HighContrastBorder,
                                focusedLabelColor = HighContrastYellow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = contactPhoneInput,
                            onValueChange = { contactPhoneInput = it },
                            label = { Text("Phone Number (e.g. +919876543210)", color = HighContrastTextMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = HighContrastWhite,
                                unfocusedTextColor = HighContrastWhite,
                                focusedBorderColor = HighContrastYellow,
                                unfocusedBorderColor = HighContrastBorder,
                                focusedLabelColor = HighContrastYellow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        contactError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = EmergencyRed
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HighContrastYellow)
                                .clickable {
                                    val trimmedName = contactNameInput.trim()
                                    val trimmedPhone = contactPhoneInput.trim()
                                    if (trimmedName.isBlank()) {
                                        contactError = "Please enter a contact name"
                                    } else if (!EmergencyContact.isValidPhoneNumber(trimmedPhone)) {
                                        contactError = "Please enter a valid phone number (7-15 digits)"
                                    } else {
                                        val newContact = EmergencyContact(trimmedName, trimmedPhone)
                                        emergencyController.contactRepository.saveContact(newContact)
                                        contact = newContact
                                        contactError = null
                                        isEditingContact = false
                                    }
                                }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Save emergency contact"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SAVE CONTACT",
                                style = MaterialTheme.typography.titleSmall,
                                color = HighContrastBlack,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else {
                        if (contact != null) {
                            Text(
                                text = "👤 ${contact?.name}",
                                style = MaterialTheme.typography.titleLarge,
                                color = HighContrastWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "📞 ${contact?.phoneNumber}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = HighContrastCyan
                            )
                        } else {
                            Text(
                                text = "⚠️ No emergency contact configured.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmergencyRed
                            )
                        }
                    }
                }
            }

            // Current SOS Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HighContrastSurface),
                border = BorderStroke(2.dp, when (emergencyState) {
                    is EmergencyState.Countdown -> HighContrastYellow
                    is EmergencyState.AcquiringLocation, is EmergencyState.Sending -> HighContrastCyan
                    is EmergencyState.Sent -> Color(0xFF00E676)
                    is EmergencyState.Failed -> EmergencyRed
                    else -> HighContrastBorder
                }),
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
                        text = "STATUS",
                        style = MaterialTheme.typography.labelLarge,
                        color = HighContrastTextMuted,
                        letterSpacing = 1.sp
                    )

                    when (val s = emergencyState) {
                        is EmergencyState.Idle -> {
                            Text(
                                text = "READY",
                                style = MaterialTheme.typography.headlineMedium,
                                color = HighContrastWhite,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Tap the button below to initiate emergency assistance countdown.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HighContrastTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        is EmergencyState.Countdown -> {
                            Text(
                                text = "COUNTDOWN: ${s.secondsRemaining}",
                                style = MaterialTheme.typography.headlineLarge,
                                color = HighContrastYellow,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Sending emergency message in ${s.secondsRemaining} seconds...\nTap CANCEL SOS to abort.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = HighContrastWhite,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is EmergencyState.AcquiringLocation -> {
                            Text(
                                text = "ACQUIRING GPS...",
                                style = MaterialTheme.typography.headlineMedium,
                                color = HighContrastCyan,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Determining device coordinates before sending...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HighContrastTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        is EmergencyState.Sending -> {
                            Text(
                                text = "SENDING SOS...",
                                style = MaterialTheme.typography.headlineMedium,
                                color = HighContrastCyan,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Dispatching emergency SMS via telephony...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HighContrastTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        is EmergencyState.Sent -> {
                            Text(
                                text = "SOS SENT",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Emergency message submitted to ${s.recipient}.\nSent at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(s.timestampMs))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HighContrastWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                        is EmergencyState.Failed -> {
                            Text(
                                text = "SOS FAILED",
                                style = MaterialTheme.typography.headlineMedium,
                                color = EmergencyRed,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = s.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = HighContrastWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                        is EmergencyState.Cancelled -> {
                            Text(
                                text = "SOS CANCELLED",
                                style = MaterialTheme.typography.headlineMedium,
                                color = HighContrastYellow,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Emergency dispatch was aborted.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HighContrastTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Big Primary Action Buttons
            when (emergencyState) {
                is EmergencyState.Countdown -> {
                    // Huge Cancel Button during Countdown
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(HighContrastWhite)
                            .border(3.dp, EmergencyRed, RoundedCornerShape(16.dp))
                            .clickable { emergencyController.cancelSos() }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Cancel SOS Countdown. Double tap to stop sending emergency message."
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = "🛑", fontSize = 32.sp)
                            Text(
                                text = "CANCEL SOS",
                                style = MaterialTheme.typography.titleLarge,
                                color = EmergencyRed,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                is EmergencyState.AcquiringLocation, is EmergencyState.Sending -> {
                    // In-flight state: show progress with cancel option
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(HighContrastCard)
                            .border(2.dp, HighContrastBorder, RoundedCornerShape(16.dp))
                            .clickable { emergencyController.cancelSos() }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Cancel sending SOS"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SENDING... (TAP TO CANCEL)",
                            style = MaterialTheme.typography.titleMedium,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    // Ready / Idle / Sent / Failed / Cancelled -> Large Trigger Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EmergencyRed)
                            .border(3.dp, HighContrastWhite, RoundedCornerShape(16.dp))
                            .clickable { emergencyController.triggerSos() }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Trigger Emergency SOS. Starts a 3 second countdown before dispatching your location to your contact. Double tap to activate."
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = "🚨", fontSize = 32.sp)
                            Text(
                                text = "TRIGGER SOS",
                                style = MaterialTheme.typography.headlineSmall,
                                color = HighContrastWhite,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Return to Main Menu Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HighContrastCard)
                    .border(1.dp, HighContrastBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        emergencyController.deactivate()
                        onExitSos()
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Return to Main Menu"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "← RETURN TO MAIN MENU",
                    style = MaterialTheme.typography.titleMedium,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
