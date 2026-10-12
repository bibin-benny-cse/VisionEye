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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.bibin.visioneye.people.PeopleRepository
import com.bibin.visioneye.people.SavedPerson
import com.bibin.visioneye.ui.components.VisionHeader
import com.bibin.visioneye.ui.components.VisionSectionTitle
import com.bibin.visioneye.ui.theme.EmergencyRed
import com.bibin.visioneye.ui.theme.HighContrastBlack
import com.bibin.visioneye.ui.theme.HighContrastBorder
import com.bibin.visioneye.ui.theme.HighContrastCard
import com.bibin.visioneye.ui.theme.HighContrastSurface
import com.bibin.visioneye.ui.theme.HighContrastTextMuted
import com.bibin.visioneye.ui.theme.HighContrastWhite
import com.bibin.visioneye.ui.theme.HighContrastYellow
import com.bibin.visioneye.ui.theme.PeoplePurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Accessible screen allowing visually impaired users and caregivers to review and delete saved people profiles.
 *
 * All stored profiles exist exclusively on the local device.
 */
@Composable
fun ManagePeopleScreen(
    repository: PeopleRepository?,
    onBack: () -> Unit,
    onAddNewPerson: () -> Unit,
    voiceController: com.bibin.visioneye.voice.VoiceController? = null,
    modifier: Modifier = Modifier
) {
    var peopleList by remember {
        mutableStateOf(repository?.getSavedPeople() ?: emptyList())
    }
    var personToDelete by remember { mutableStateOf<SavedPerson?>(null) }

    androidx.compose.runtime.DisposableEffect(peopleList, personToDelete, voiceController) {
        if (voiceController != null) {
            voiceController.setScreenContext(com.bibin.visioneye.voice.VoiceScreenContext.MANAGE_PEOPLE)
            val actions = com.bibin.visioneye.voice.ScreenVoiceActions.createManagePeopleActions(
                repository = repository,
                onBack = onBack,
                onAddNewPerson = onAddNewPerson,
                getPersonToDelete = { personToDelete },
                setPersonToDelete = { personToDelete = it },
                onConfirmDelete = { person ->
                    repository?.deletePerson(person.id)
                    peopleList = repository?.getSavedPeople() ?: emptyList()
                }
            )
            voiceController.registry.registerAll(actions)
        }
        onDispose {
            voiceController?.registry?.unregisterForContext(com.bibin.visioneye.voice.VoiceScreenContext.MANAGE_PEOPLE)
        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header with Back Button
            VisionHeader(
                title = "SAVED PEOPLE",
                subtitle = "On-Device Face Directory",
                onBack = onBack,
                backLabel = "Back to main menu"
            )

            // Persistent Accessible Voice Control Button
            if (voiceController != null) {
                com.bibin.visioneye.ui.components.VoiceControlButton(voiceController = voiceController)
            }

            // Privacy and Safety Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HighContrastCard),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PeoplePurple)
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
                            text = "🔒",
                            fontSize = 14.sp,
                            modifier = Modifier.semantics { hideFromAccessibility() }
                        )
                        Text(
                            text = "On-Device Privacy Guaranteed",
                            style = MaterialTheme.typography.labelMedium,
                            color = PeoplePurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "All biometric vectors and names are stored locally on this phone. No biometric data is ever sent to external servers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastTextMuted
                    )
                }
            }

            // Enroll New Person Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PeoplePurple)
                    .border(2.dp, HighContrastWhite, RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddNewPerson)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Enroll new person. Opens camera to capture and save a new face."
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "➕",
                        fontSize = 18.sp,
                        color = HighContrastWhite,
                        modifier = Modifier.semantics { hideFromAccessibility() }
                    )
                    Text(
                        text = "ENROLL NEW PERSON",
                        style = MaterialTheme.typography.titleMedium,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (peopleList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "👥",
                            fontSize = 44.sp,
                            modifier = Modifier.semantics { hideFromAccessibility() }
                        )
                        Text(
                            text = "No saved people yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap 'ENROLL NEW PERSON' to save Father, Mother, or a friend.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HighContrastTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                VisionSectionTitle(
                    title = "ENROLLED PROFILES (${peopleList.size})",
                    color = HighContrastYellow
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(peopleList, key = { it.id }) { person ->
                        SavedPersonItem(
                            person = person,
                            onDelete = { personToDelete = person }
                        )
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        personToDelete?.let { person ->
            AlertDialog(
                onDismissRequest = { personToDelete = null },
                containerColor = HighContrastSurface,
                title = {
                    Text(
                        text = "DELETE \"${person.name.uppercase()}\"?",
                        color = EmergencyRed,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove ${person.name} from saved people? Their face recognition vector will be permanently deleted from this device.",
                        color = HighContrastWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 90.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmergencyRed)
                            .clickable {
                                repository?.deletePerson(person.id)
                                peopleList = repository?.getSavedPeople() ?: emptyList()
                                personToDelete = null
                            }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Confirm deletion of ${person.name}"
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DELETE",
                            color = HighContrastWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                dismissButton = {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 90.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HighContrastCard)
                            .border(1.dp, HighContrastBorder, RoundedCornerShape(8.dp))
                            .clickable { personToDelete = null }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Cancel deletion"
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CANCEL",
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SavedPersonItem(
    person: SavedPerson,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatted = remember(person.enrolledAt) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(person.enrolledAt))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, HighContrastBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = HighContrastCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${person.embeddings.size} face samples • Enrolled $dateFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = HighContrastTextMuted
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HighContrastSurface)
                    .border(2.dp, EmergencyRed, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Delete ${person.name}"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🗑️",
                    fontSize = 20.sp,
                    modifier = Modifier.semantics { hideFromAccessibility() }
                )
            }
        }
    }
}
