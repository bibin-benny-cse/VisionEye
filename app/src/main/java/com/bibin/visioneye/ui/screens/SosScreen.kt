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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.hideFromAccessibility
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
import com.bibin.visioneye.ui.components.VisionHeader
import com.bibin.visioneye.ui.components.VoiceControlButton
import com.bibin.visioneye.ui.theme.EmergencyRed
import com.bibin.visioneye.ui.theme.HighContrastBlack
import com.bibin.visioneye.ui.theme.HighContrastBorder
import com.bibin.visioneye.ui.theme.HighContrastCard
import com.bibin.visioneye.ui.theme.HighContrastCyan
import com.bibin.visioneye.ui.theme.HighContrastSurface
import com.bibin.visioneye.ui.theme.HighContrastTextMuted
import com.bibin.visioneye.ui.theme.HighContrastWhite
import com.bibin.visioneye.ui.theme.HighContrastYellow
import com.bibin.visioneye.voice.DictationFieldType
import com.bibin.visioneye.voice.ScreenVoiceActions
import com.bibin.visioneye.voice.VoiceActionResult
import com.bibin.visioneye.voice.VoiceController
import com.bibin.visioneye.voice.VoiceScreenContext
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
    voiceController: VoiceController? = null,
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

    DisposableEffect(isEditingContact, contactNameInput, contactPhoneInput, emergencyState, voiceController) {
        if (voiceController != null) {
            voiceController.setScreenContext(VoiceScreenContext.SOS_EMERGENCY)
            voiceController.setDictationConsumer { fieldType, text ->
                when (fieldType) {
                    DictationFieldType.EMERGENCY_CONTACT_NAME -> {
                        val clean = text.trim()
                        contactNameInput = clean
                        VoiceActionResult.Success("Contact name set to $clean.")
                    }
                    DictationFieldType.EMERGENCY_CONTACT_PHONE -> {
                        val normalized = ScreenVoiceActions.normalizeSpokenPhoneNumber(text)
                        contactPhoneInput = normalized
                        VoiceActionResult.Success("Phone number set to $normalized.")
                    }
                    else -> VoiceActionResult.Error("Unsupported field")
                }
            }

            val actions = ScreenVoiceActions.createSosActions(
                emergencyController = emergencyController,
                onExitSos = {
                    emergencyController.deactivate()
                    onExitSos()
                },
                getIsEditingContact = { isEditingContact },
                setIsEditingContact = { isEditingContact = it },
                getContactNameInput = { contactNameInput },
                setContactNameInput = { contactNameInput = it },
                getContactPhoneInput = { contactPhoneInput },
                setContactPhoneInput = { contactPhoneInput = it },
                onSaveContact = {
                    val trimmedName = contactNameInput.trim()
                    val trimmedPhone = contactPhoneInput.trim()
                    val newContact = EmergencyContact(trimmedName, trimmedPhone)
                    emergencyController.contactRepository.saveContact(newContact)
                    contact = newContact
                    contactError = null
                }
            )
            voiceController.registry.registerAll(actions)
        }
        onDispose {
            voiceController?.registry?.unregisterForContext(VoiceScreenContext.SOS_EMERGENCY)
            voiceController?.setDictationConsumer(null)
        }
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Back Button
            VisionHeader(
                title = "EMERGENCY SOS",
                subtitle = "Assistance & Location Dispatch",
                onBack = {
                    emergencyController.deactivate()
                    onExitSos()
                },
                backLabel = "Return to main menu",
                badgeText = "SOS",
                badgeColor = EmergencyRed
            )

            VoiceControlButton(
                voiceController = voiceController,
                modifier = Modifier.fillMaxWidth()
            )

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 16.sp,
                                modifier = Modifier.semantics { hideFromAccessibility() }
                            )
                            Text(
                                text = "SMS PERMISSION REQUIRED",
                                style = MaterialTheme.typography.titleSmall,
                                color = HighContrastYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Grant SMS permission so VisionEye can automatically send emergency coordinates to your contact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HighContrastWhite
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HighContrastYellow)
                                .clickable { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Grant SMS Permission"
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "GRANT PERMISSION",
                                style = MaterialTheme.typography.labelLarge,
                                color = HighContrastBlack,
                                fontWeight = FontWeight.ExtraBold
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
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.5.dp, HighContrastCyan, RoundedCornerShape(6.dp))
                                    .clickable {
                                        contactNameInput = contact?.name ?: ""
                                        contactPhoneInput = contact?.phoneNumber ?: ""
                                        isEditingContact = true
                                    }
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "Edit emergency contact"
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "EDIT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HighContrastCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                    text = contact?.name ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HighContrastWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "📞",
                                    fontSize = 16.sp,
                                    modifier = Modifier.semantics { hideFromAccessibility() }
                                )
                                Text(
                                    text = contact?.phoneNumber ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = HighContrastCyan
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "⚠️",
                                    fontSize = 16.sp,
                                    modifier = Modifier.semantics { hideFromAccessibility() }
                                )
                                Text(
                                    text = "No emergency contact configured.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmergencyRed
                                )
                            }
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "STATUS",
                        style = MaterialTheme.typography.labelMedium,
                        color = HighContrastTextMuted,
                        letterSpacing = 1.sp
                    )

                    when (val s = emergencyState) {
                        is EmergencyState.Idle -> {
                            Text(
                                text = "READY",
                                style = MaterialTheme.typography.headlineSmall,
                                color = HighContrastWhite,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Tap the button below to initiate emergency assistance countdown.",
                                style = MaterialTheme.typography.bodySmall,
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = HighContrastWhite,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is EmergencyState.AcquiringLocation -> {
                            Text(
                                text = "ACQUIRING GPS...",
                                style = MaterialTheme.typography.titleLarge,
                                color = HighContrastCyan,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Determining device coordinates before sending...",
                                style = MaterialTheme.typography.bodySmall,
                                color = HighContrastTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        is EmergencyState.Sending -> {
                            Text(
                                text = "SENDING SOS...",
                                style = MaterialTheme.typography.titleLarge,
                                color = HighContrastCyan,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Dispatching emergency SMS via telephony...",
                                style = MaterialTheme.typography.bodySmall,
                                color = HighContrastTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        is EmergencyState.Sent -> {
                            Text(
                                text = "SOS SENT",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Emergency message submitted to ${s.recipient}.\nSent at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(s.timestampMs))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = HighContrastWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                        is EmergencyState.Failed -> {
                            Text(
                                text = "SOS FAILED",
                                style = MaterialTheme.typography.titleLarge,
                                color = EmergencyRed,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = s.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = HighContrastWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                        is EmergencyState.Cancelled -> {
                            Text(
                                text = "SOS CANCELLED",
                                style = MaterialTheme.typography.titleLarge,
                                color = HighContrastYellow,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Emergency dispatch was aborted.",
                                style = MaterialTheme.typography.bodySmall,
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
                            .defaultMinSize(minHeight = 72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(HighContrastWhite)
                            .border(3.dp, EmergencyRed, RoundedCornerShape(14.dp))
                            .clickable { emergencyController.cancelSos() }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Cancel SOS countdown"
                            }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🛑",
                                fontSize = 28.sp,
                                modifier = Modifier.semantics { hideFromAccessibility() }
                            )
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
                            .defaultMinSize(minHeight = 64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(HighContrastCard)
                            .border(2.dp, HighContrastBorder, RoundedCornerShape(14.dp))
                            .clickable { emergencyController.cancelSos() }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Cancel sending SOS"
                            }
                            .padding(14.dp),
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
                    // Large Trigger Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(EmergencyRed)
                            .border(3.dp, HighContrastWhite, RoundedCornerShape(14.dp))
                            .clickable { emergencyController.triggerSos() }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Trigger Emergency SOS. Starts a 3 second countdown before dispatching your location to your contact."
                            }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🚨",
                                fontSize = 28.sp,
                                modifier = Modifier.semantics { hideFromAccessibility() }
                            )
                            Text(
                                text = "TRIGGER SOS",
                                style = MaterialTheme.typography.titleLarge,
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
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HighContrastCard)
                    .border(1.dp, HighContrastBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        emergencyController.deactivate()
                        onExitSos()
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Return to Main Menu"
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "◀",
                        fontSize = 16.sp,
                        color = HighContrastWhite,
                        modifier = Modifier.semantics { hideFromAccessibility() }
                    )
                    Text(
                        text = "RETURN TO MAIN MENU",
                        style = MaterialTheme.typography.titleSmall,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
