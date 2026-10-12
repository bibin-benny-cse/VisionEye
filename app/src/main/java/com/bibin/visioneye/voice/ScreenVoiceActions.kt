package com.bibin.visioneye.voice

import com.bibin.visioneye.camera.CameraController
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.currency.CurrencyDenomination
import com.bibin.visioneye.emergency.EmergencyContact
import com.bibin.visioneye.emergency.EmergencyController
import com.bibin.visioneye.navigation.GpsStatus
import com.bibin.visioneye.navigation.NavigationController
import com.bibin.visioneye.navigation.guidance.GuidanceStatus
import com.bibin.visioneye.people.PeopleRepository
import com.bibin.visioneye.people.PeopleState
import com.bibin.visioneye.people.SavedPerson
import com.bibin.visioneye.read.ReadState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Factory and registrar mapping exact visible UI labels to canonical voice commands
 * across all VisionEye screens.
 *
 * Core rule: The canonical spoken phrase matches the exact visible label in the UI.
 */
object ScreenVoiceActions {

    // =========================================================================
    // 1. MAIN SCREEN DASHBOARD
    // =========================================================================

    fun createMainScreenActions(
        onStartCamera: () -> Unit,
        onStartReadMode: () -> Unit,
        onStartCurrencyMode: () -> Unit,
        onStartPeopleMode: () -> Unit,
        onSavePerson: () -> Unit,
        onManagePeople: () -> Unit,
        onStartNavigation: () -> Unit,
        onOpenSos: () -> Unit
    ): List<VoiceAction> {
        return listOf(
            VoiceAction(
                id = "main_navigate",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "NAVIGATE",
                canonicalCommand = "Navigate",
                aliases = listOf("Navigate mode", "Start navigate", "Obstacle detection"),
                description = "Open camera-based obstacle detection",
                onExecute = {
                    onStartCamera()
                    VoiceActionResult.Success("Opening Navigate mode. Scanning path for obstacles.")
                }
            ),
            VoiceAction(
                id = "main_read_text",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "READ TEXT",
                canonicalCommand = "Read text",
                aliases = listOf("Read", "Read mode", "Scan document"),
                description = "Open document-reading mode",
                onExecute = {
                    onStartReadMode()
                    VoiceActionResult.Success("Opening Read text mode. Align documents in camera view.")
                }
            ),
            VoiceAction(
                id = "main_scan_currency",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "SCAN CURRENCY",
                canonicalCommand = "Scan currency",
                aliases = listOf("Currency", "Currency mode", "Scan rupee"),
                description = "Open banknote-recognition mode",
                onExecute = {
                    onStartCurrencyMode()
                    VoiceActionResult.Success("Opening Scan currency mode. Hold banknote in camera view.")
                }
            ),
            VoiceAction(
                id = "main_recognize_people",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "RECOGNIZE PEOPLE",
                canonicalCommand = "Recognize people",
                aliases = listOf("People", "People mode", "Who is this"),
                description = "Open saved-person identification mode",
                onExecute = {
                    onStartPeopleMode()
                    VoiceActionResult.Success("Opening Recognize people mode. Scanning for registered faces.")
                }
            ),
            VoiceAction(
                id = "main_save_person",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "SAVE PERSON",
                canonicalCommand = "Save person",
                aliases = listOf("Enroll person", "Enroll new person", "Add person"),
                description = "Open face enrollment flow",
                onExecute = {
                    onSavePerson()
                    VoiceActionResult.Success("Opening face enrollment flow.")
                }
            ),
            VoiceAction(
                id = "main_manage_people",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "MANAGE PEOPLE",
                canonicalCommand = "Manage people",
                aliases = listOf("People directory", "Saved people", "Face directory"),
                description = "Open saved-person management",
                onExecute = {
                    onManagePeople()
                    VoiceActionResult.Success("Opening saved people directory.")
                }
            ),
            VoiceAction(
                id = "main_outdoor_gps",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "OUTDOOR GPS NAVIGATION",
                canonicalCommand = "Outdoor GPS navigation",
                aliases = listOf("Navigation", "GPS navigation", "Start navigation", "Outdoor navigation"),
                description = "Open GPS wayfinding",
                onExecute = {
                    onStartNavigation()
                    VoiceActionResult.Success("Opening Outdoor GPS navigation.")
                }
            ),
            VoiceAction(
                id = "main_emergency_sos",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "EMERGENCY SOS",
                canonicalCommand = "Emergency SOS",
                aliases = listOf("Emergency", "SOS", "Open SOS"),
                description = "Open the emergency screen only",
                onExecute = {
                    // Critical safety requirement: Opens screen ONLY without dispatching
                    onOpenSos()
                    VoiceActionResult.Success("Opening Emergency SOS screen. Emergency has not been dispatched.")
                }
            )
        )
    }

    // =========================================================================
    // 2. CAMERA ASSISTANCE SCREEN (NAVIGATE, CURRENCY, PEOPLE, READ)
    // =========================================================================

    fun createCameraAssistanceActions(
        currentMode: VisionMode,
        cameraController: CameraController,
        onStopCamera: () -> Unit,
        getShowDebugOverlay: () -> Boolean,
        onToggleOverlay: () -> Unit
    ): List<VoiceAction> {
        val screenContext = when (currentMode) {
            VisionMode.NAVIGATE -> VoiceScreenContext.CAMERA_NAVIGATE
            VisionMode.READ -> VoiceScreenContext.CAMERA_READ
            VisionMode.CURRENCY -> VoiceScreenContext.CAMERA_CURRENCY
            VisionMode.PEOPLE -> VoiceScreenContext.CAMERA_PEOPLE
            else -> VoiceScreenContext.CAMERA_NAVIGATE
        }

        val actions = mutableListOf<VoiceAction>()

        // 1. Universal Stop Camera
        actions.add(
            VoiceAction(
                id = "camera_stop",
                screenContext = screenContext,
                visibleLabel = "STOP CAMERA",
                canonicalCommand = "Stop camera",
                aliases = listOf("Stop", "Exit camera", "Close camera", "Back"),
                description = "Stop camera assistance and return to mode selection",
                onExecute = {
                    cameraController.stopCamera()
                    onStopCamera()
                    VoiceActionResult.Success("Camera stopped. Returning to dashboard.")
                }
            )
        )

        // 2. Mode-specific controls
        when (currentMode) {
            VisionMode.NAVIGATE -> {
                actions.add(
                    VoiceAction(
                        id = "camera_boxes_toggle",
                        screenContext = screenContext,
                        visibleLabel = if (getShowDebugOverlay()) "BOXES: ON" else "BOXES: OFF",
                        canonicalCommand = if (getShowDebugOverlay()) "Boxes off" else "Boxes on",
                        aliases = listOf("Toggle boxes", "Boxes on", "Boxes off", "Bounding boxes"),
                        description = "Toggle detection bounding boxes on display",
                        onExecute = {
                            onToggleOverlay()
                            val newState = if (getShowDebugOverlay()) "on" else "off"
                            VoiceActionResult.Success("Bounding boxes turned $newState.")
                        }
                    )
                )
                actions.add(
                    VoiceAction(
                        id = "camera_navigate_status",
                        screenContext = screenContext,
                        visibleLabel = "NAVIGATE",
                        canonicalCommand = "Navigate",
                        aliases = listOf("Status", "Obstacle status"),
                        description = "Report current obstacle detection status",
                        onExecute = {
                            val yolo = cameraController.yoloState.value
                            val count = yolo.detections.size
                            val msg = if (count > 0) {
                                "$count object(s) detected in forward path."
                            } else {
                                "Path clear. Scanning forward for obstacles."
                            }
                            VoiceActionResult.Success(msg)
                        }
                    )
                )
            }

            VisionMode.CURRENCY -> {
                actions.add(
                    VoiceAction(
                        id = "currency_query_note",
                        screenContext = screenContext,
                        visibleLabel = "CURRENCY",
                        canonicalCommand = "What note is this?",
                        aliases = listOf("What note is this", "What note", "Which note", "Check currency", "Currency"),
                        description = "Identify banknote currently visible in camera view",
                        onExecute = {
                            val state = cameraController.currencyState.value
                            val msg = if (state.isConfirmed && state.confirmedDenominations.isNotEmpty()) {
                                CurrencyDenomination.formatSpokenAnnouncement(state.confirmedDenominations)
                            } else if (state.detections.isNotEmpty()) {
                                "Detecting ${state.detections.first().spokenName}. Hold steady."
                            } else if (!state.isReady) {
                                "Currency recognition model is initializing. Hold banknote flat."
                            } else {
                                "No Indian banknote detected in camera view. Hold note flat in front of camera."
                            }
                            VoiceActionResult.Success(msg)
                        }
                    )
                )
                actions.add(
                    VoiceAction(
                        id = "currency_repeat_denomination",
                        screenContext = screenContext,
                        visibleLabel = "REPEAT LAST DENOMINATION",
                        canonicalCommand = "Repeat the last denomination",
                        aliases = listOf("Repeat denomination", "Repeat last denomination", "Repeat note"),
                        description = "Repeat the last confirmed currency denomination",
                        onExecute = {
                            val state = cameraController.currencyState.value
                            val msg = state.lastSpokenAnnouncement
                                ?: if (state.confirmedDenominations.isNotEmpty()) {
                                    CurrencyDenomination.formatSpokenAnnouncement(state.confirmedDenominations)
                                } else {
                                    "No banknote denomination has been recognized yet."
                                }
                            VoiceActionResult.Success(msg)
                        }
                    )
                )
            }

            VisionMode.PEOPLE -> {
                actions.add(
                    VoiceAction(
                        id = "people_query_who",
                        screenContext = screenContext,
                        visibleLabel = "PEOPLE",
                        canonicalCommand = "Who is this?",
                        aliases = listOf("Who is this", "Who is that", "Recognize", "People"),
                        description = "Identify person in camera view against saved profiles",
                        onExecute = {
                            val state = cameraController.peopleState.value
                            val msg = if (state.isConfirmed) {
                                when {
                                    state.recognizedNames.isNotEmpty() ->
                                        "${PeopleState.formatNames(state.recognizedNames)} recognized ahead in camera view."
                                    state.hasUnknownPerson ->
                                        "Unknown person detected ahead in camera view."
                                    else -> "1 person confirmed ahead."
                                }
                            } else {
                                state.statusMessage
                            }
                            VoiceActionResult.Success(msg)
                        }
                    )
                )
                actions.add(
                    VoiceAction(
                        id = "people_boxes_toggle",
                        screenContext = screenContext,
                        visibleLabel = if (getShowDebugOverlay()) "BOXES: ON" else "BOXES: OFF",
                        canonicalCommand = if (getShowDebugOverlay()) "Boxes off" else "Boxes on",
                        aliases = listOf("Toggle boxes", "Boxes on", "Boxes off"),
                        description = "Toggle face detection bounding boxes",
                        onExecute = {
                            onToggleOverlay()
                            val newState = if (getShowDebugOverlay()) "on" else "off"
                            VoiceActionResult.Success("Face bounding boxes turned $newState.")
                        }
                    )
                )
            }

            VisionMode.READ -> {
                actions.add(
                    VoiceAction(
                        id = "read_capture_page",
                        screenContext = screenContext,
                        visibleLabel = "CAPTURE PAGE NOW",
                        canonicalCommand = "Capture page now",
                        aliases = listOf("Capture page", "Capture now", "Take picture", "Read page"),
                        description = "Trigger immediate document capture for OCR reading",
                        isAvailable = {
                            val state = cameraController.readState.value
                            state is ReadState.Searching || state is ReadState.PageDetected || state is ReadState.PageBoundaryNotDetected
                        },
                        onExecute = {
                            val state = cameraController.readState.value
                            if (state is ReadState.Capturing || state is ReadState.ProcessingOcr) {
                                VoiceActionResult.Error("Document OCR is currently processing. Please wait.")
                            } else {
                                cameraController.capturePageNow()
                                VoiceActionResult.Success("Capturing page for document reading.")
                            }
                        }
                    )
                )
                actions.add(
                    VoiceAction(
                        id = "read_retry_scan",
                        screenContext = screenContext,
                        visibleLabel = "RETRY SCANNING",
                        canonicalCommand = "Retry scanning",
                        aliases = listOf("Retry", "Retry scan", "Scan again"),
                        description = "Retry document scan after alignment issue",
                        isAvailable = {
                            val state = cameraController.readState.value
                            state is ReadState.PageBoundaryNotDetected
                        },
                        onExecute = {
                            cameraController.scanAgain()
                            VoiceActionResult.Success("Retrying document scan. Align page in view.")
                        }
                    )
                )
                actions.add(
                    VoiceAction(
                        id = "read_scan_another",
                        screenContext = screenContext,
                        visibleLabel = "SCAN ANOTHER DOCUMENT",
                        canonicalCommand = "Scan another document",
                        aliases = listOf("Scan another page", "Another document", "Scan again"),
                        description = "Scan another printed page or sign",
                        isAvailable = {
                            val state = cameraController.readState.value
                            state is ReadState.Completed || state is ReadState.Error
                        },
                        onExecute = {
                            cameraController.scanAgain()
                            VoiceActionResult.Success("Ready to scan another document.")
                        }
                    )
                )
                actions.add(
                    VoiceAction(
                        id = "read_repeat_text",
                        screenContext = screenContext,
                        visibleLabel = "REPEAT TEXT",
                        canonicalCommand = "Repeat text",
                        aliases = listOf("Repeat", "Read again", "Repeat reading"),
                        description = "Repeat reading the captured text aloud",
                        onExecute = {
                            val state = cameraController.readState.value
                            val msg = when (state) {
                                is ReadState.Reading -> "Currently reading: \"${state.currentChunk}\""
                                is ReadState.Completed -> "Reading completed."
                                is ReadState.ProcessingOcr -> "Document OCR is processing. Please wait."
                                is ReadState.Searching -> "Searching for printed text in camera view."
                                is ReadState.Error -> state.message
                                else -> "No text has been read yet."
                            }
                            VoiceActionResult.Success(msg)
                        }
                    )
                )
            }

            else -> {}
        }

        return actions
    }

    // =========================================================================
    // 3. OUTDOOR GPS NAVIGATION SCREEN
    // =========================================================================

    fun createNavigationGpsActions(
        navigationController: NavigationController,
        onStopNavigation: () -> Unit,
        getDestinationInput: () -> String,
        setDestinationInput: (String) -> Unit,
        onStartRouteSearch: (String) -> Unit,
        onCancelRouteSearch: () -> Unit
    ): List<VoiceAction> {
        val screenContext = VoiceScreenContext.NAVIGATION_GPS
        val actions = mutableListOf<VoiceAction>()

        // 1. Back button
        actions.add(
            VoiceAction(
                id = "nav_back",
                screenContext = screenContext,
                visibleLabel = "BACK",
                canonicalCommand = "Back",
                aliases = listOf("Go back", "Return"),
                description = "Exit outdoor navigation and return to dashboard",
                onExecute = {
                    navigationController.deactivate()
                    onStopNavigation()
                    VoiceActionResult.Success("Exiting navigation. Returning to dashboard.")
                }
            )
        )

        // 2. Destination Dictation Field
        actions.add(
            VoiceAction(
                id = "nav_enter_destination",
                screenContext = screenContext,
                visibleLabel = "Enter destination",
                canonicalCommand = "Enter destination",
                aliases = listOf("Destination", "Set destination", "Destination field"),
                description = "Select destination field and dictate place name",
                isDictation = true,
                onExecute = {
                    VoiceActionResult.StartDictation(
                        prompt = "Destination field ready. Say the place name.",
                        fieldType = DictationFieldType.NAVIGATION_DESTINATION
                    )
                }
            )
        )

        // 3. START / ROUTE button
        actions.add(
            VoiceAction(
                id = "nav_start_route",
                screenContext = screenContext,
                visibleLabel = "START / ROUTE",
                canonicalCommand = "Start route",
                aliases = listOf("Start route", "Route", "Find route", "Calculate route", "Start slash route"),
                description = "Calculate walking route to destination",
                isAvailable = { getDestinationInput().isNotBlank() },
                onExecute = {
                    val dest = getDestinationInput().trim()
                    if (dest.isBlank()) {
                        VoiceActionResult.Error("Please enter a destination name first.")
                    } else {
                        onStartRouteSearch(dest)
                        VoiceActionResult.Success("Searching walking route to $dest.")
                    }
                }
            )
        )

        // 4. CANCEL button
        actions.add(
            VoiceAction(
                id = "nav_cancel_search",
                screenContext = screenContext,
                visibleLabel = "CANCEL",
                canonicalCommand = "Cancel",
                aliases = listOf("Cancel search", "Clear destination"),
                description = "Cancel destination route search and clear input",
                onExecute = {
                    setDestinationInput("")
                    onCancelRouteSearch()
                    VoiceActionResult.Success("Route search cancelled.")
                }
            )
        )

        // 5. STOP NAVIGATION button
        actions.add(
            VoiceAction(
                id = "nav_stop_navigation",
                screenContext = screenContext,
                visibleLabel = "STOP NAVIGATION",
                canonicalCommand = "Stop navigation",
                aliases = listOf("Stop navigation", "Exit navigation", "Halt navigation"),
                description = "Stop outdoor GPS guidance and return to dashboard",
                onExecute = {
                    navigationController.deactivate()
                    onStopNavigation()
                    VoiceActionResult.Success("Navigation stopped.")
                }
            )
        )

        // 6. STOP GUIDANCE button (when guidance is active)
        actions.add(
            VoiceAction(
                id = "nav_stop_guidance",
                screenContext = screenContext,
                visibleLabel = "STOP GUIDANCE",
                canonicalCommand = "Stop guidance",
                aliases = listOf("Stop guidance", "End guidance"),
                description = "Halt turn-by-turn guidance and return to route overview",
                isAvailable = {
                    navigationController.diagnostics.value.guidanceProgress.status != GuidanceStatus.IDLE
                },
                onExecute = {
                    navigationController.stopGuidance()
                    VoiceActionResult.Success("Guidance stopped.")
                }
            )
        )

        // 7. Spoken Information Queries (Telemetry & Cards)
        actions.add(
            VoiceAction(
                id = "nav_query_gps_status",
                screenContext = screenContext,
                visibleLabel = "GPS STATUS",
                canonicalCommand = "Read GPS status",
                aliases = listOf("GPS status", "Check GPS", "GPS signal"),
                description = "Read current GPS acquisition status",
                onExecute = {
                    val diag = navigationController.diagnostics.value
                    val statusText = when (diag.status) {
                        GpsStatus.ACTIVE -> "GPS status is Active with accuracy plus or minus ${diag.locationFix?.accuracyMeters?.toInt() ?: 0} meters."
                        GpsStatus.ACQUIRING -> "GPS status: Acquiring satellite fix. Please wait."
                        GpsStatus.PERMISSION_DENIED -> "GPS status: Location permission denied."
                        GpsStatus.UNAVAILABLE -> "GPS status: Location service unavailable on this device."
                    }
                    VoiceActionResult.Success(statusText)
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "nav_query_telemetry",
                screenContext = screenContext,
                visibleLabel = "COORDINATE TELEMETRY",
                canonicalCommand = "Read coordinate telemetry",
                aliases = listOf("Where am I", "Where am i", "Current coordinates", "Read coordinates", "My location"),
                description = "Read current geographic coordinates and accuracy",
                onExecute = {
                    val fix = navigationController.diagnostics.value.locationFix
                    val msg = if (fix != null) {
                        val lat = String.format(Locale.US, "%.5f", fix.latitude)
                        val lon = String.format(Locale.US, "%.5f", fix.longitude)
                        val acc = fix.accuracyMeters.toInt()
                        "You are at latitude $lat, longitude $lon, with accuracy plus or minus $acc meters."
                    } else {
                        "GPS coordinates acquiring. No satellite fix available yet."
                    }
                    VoiceActionResult.Success(msg)
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "nav_query_compass",
                screenContext = screenContext,
                visibleLabel = "COMPASS",
                canonicalCommand = "Read compass",
                aliases = listOf("Compass", "Which way am I facing", "Heading", "Direction"),
                description = "Read current compass heading and cardinal direction",
                onExecute = {
                    val diag = navigationController.diagnostics.value
                    val msg = if (diag.headingDegrees != null && diag.cardinalDirection != null) {
                        "Facing ${diag.cardinalDirection}, heading ${diag.headingDegrees.toInt()} degrees."
                    } else {
                        "Compass sensor is currently initializing."
                    }
                    VoiceActionResult.Success(msg)
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "nav_query_instruction",
                screenContext = screenContext,
                visibleLabel = "Current instruction",
                canonicalCommand = "Read current instruction",
                aliases = listOf("Current instruction", "What is my next direction", "Repeat direction", "Next direction", "Read navigation guidance"),
                description = "Read active pedestrian guidance instruction",
                onExecute = {
                    val guidance = navigationController.diagnostics.value.guidanceProgress
                    val msg = if (guidance.status != GuidanceStatus.IDLE && guidance.currentInstruction.isNotBlank()) {
                        val dist = formatDistance(guidance.distanceToNextManeuver)
                        "${guidance.currentInstruction}. Distance: $dist."
                    } else {
                        "No navigation route is currently active."
                    }
                    VoiceActionResult.Success(msg)
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "nav_query_distance_maneuver",
                screenContext = screenContext,
                visibleLabel = "Distance to maneuver",
                canonicalCommand = "Read distance to maneuver",
                aliases = listOf("Distance to maneuver", "Distance to next turn", "How far to turn"),
                description = "Read distance to next turn or maneuver",
                onExecute = {
                    val guidance = navigationController.diagnostics.value.guidanceProgress
                    val msg = if (guidance.status != GuidanceStatus.IDLE) {
                        "Distance to next maneuver is ${formatDistance(guidance.distanceToNextManeuver)}."
                    } else {
                        "No navigation guidance is currently active."
                    }
                    VoiceActionResult.Success(msg)
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "nav_query_remaining_route",
                screenContext = screenContext,
                visibleLabel = "Remaining route",
                canonicalCommand = "Read remaining route",
                aliases = listOf("Remaining route", "How much distance is remaining", "Distance remaining"),
                description = "Read remaining distance to destination",
                onExecute = {
                    val diag = navigationController.diagnostics.value
                    val guidance = diag.guidanceProgress
                    val route = diag.currentRoute
                    val msg = if (guidance.status != GuidanceStatus.IDLE) {
                        "Remaining route distance is ${formatDistance(guidance.remainingRouteDistance)}."
                    } else if (route != null) {
                        "Total route distance is ${formatDistance(route.totalDistanceMeters)}."
                    } else {
                        "No destination route loaded."
                    }
                    VoiceActionResult.Success(msg)
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "nav_query_arrival_time",
                screenContext = screenContext,
                visibleLabel = "Estimated walking time",
                canonicalCommand = "How long until arrival?",
                aliases = listOf("How long until arrival", "Time remaining", "ETA", "Walking time"),
                description = "Read estimated time to destination arrival",
                onExecute = {
                    val route = navigationController.diagnostics.value.currentRoute
                    val msg = if (route != null) {
                        val totalMins = (route.totalDurationSeconds / 60.0).roundToInt()
                        val formattedDuration = if (totalMins >= 60) {
                            val hrs = totalMins / 60
                            val remMins = totalMins % 60
                            if (remMins > 0) "$hrs hours $remMins minutes" else "$hrs hours"
                        } else if (totalMins > 0) {
                            "$totalMins minutes"
                        } else {
                            "less than one minute"
                        }
                        "Estimated walking time is $formattedDuration to ${route.destination}."
                    } else {
                        "No route is currently loaded."
                    }
                    VoiceActionResult.Success(msg)
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "nav_query_step",
                screenContext = screenContext,
                visibleLabel = "Current step",
                canonicalCommand = "Read current step",
                aliases = listOf("Current step", "Step number", "Which step"),
                description = "Read the current navigation maneuver step number",
                onExecute = {
                    val guidance = navigationController.diagnostics.value.guidanceProgress
                    val msg = if (guidance.status != GuidanceStatus.IDLE) {
                        "Step ${guidance.currentStepIndex + 1} of ${guidance.totalSteps}."
                    } else {
                        "No active guidance steps."
                    }
                    VoiceActionResult.Success(msg)
                }
            )
        )

        return actions
    }

    // =========================================================================
    // 4. MANAGE PEOPLE SCREEN
    // =========================================================================

    fun createManagePeopleActions(
        repository: PeopleRepository?,
        onBack: () -> Unit,
        onAddNewPerson: () -> Unit,
        getPersonToDelete: () -> SavedPerson?,
        setPersonToDelete: (SavedPerson?) -> Unit,
        onConfirmDelete: (SavedPerson) -> Unit
    ): List<VoiceAction> {
        val screenContext = VoiceScreenContext.MANAGE_PEOPLE
        val actions = mutableListOf<VoiceAction>()

        // 1. Back
        actions.add(
            VoiceAction(
                id = "manage_people_back",
                screenContext = screenContext,
                visibleLabel = "BACK",
                canonicalCommand = "Back",
                aliases = listOf("Go back", "Return to main"),
                description = "Back to main menu",
                onExecute = {
                    onBack()
                    VoiceActionResult.Success("Returning to main menu.")
                }
            )
        )

        // 2. Enroll new person
        actions.add(
            VoiceAction(
                id = "manage_people_enroll",
                screenContext = screenContext,
                visibleLabel = "ENROLL NEW PERSON",
                canonicalCommand = "Enroll new person",
                aliases = listOf("Enroll person", "Add person", "Save person"),
                description = "Open camera to capture and save a new face profile",
                onExecute = {
                    onAddNewPerson()
                    VoiceActionResult.Success("Opening face enrollment flow.")
                }
            )
        )

        // 3. Delete confirmation actions (when dialog is active)
        actions.add(
            VoiceAction(
                id = "manage_people_confirm_delete",
                screenContext = screenContext,
                visibleLabel = "DELETE",
                canonicalCommand = "Delete",
                aliases = listOf("Confirm delete", "Confirm"),
                description = "Confirm deletion of selected face profile",
                isAvailable = { getPersonToDelete() != null },
                onExecute = {
                    val person = getPersonToDelete()
                    if (person != null) {
                        onConfirmDelete(person)
                        setPersonToDelete(null)
                        VoiceActionResult.Success("Profile for ${person.name} deleted.")
                    } else {
                        VoiceActionResult.Error("No profile selected for deletion.")
                    }
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "manage_people_cancel_delete",
                screenContext = screenContext,
                visibleLabel = "CANCEL",
                canonicalCommand = "Cancel",
                aliases = listOf("Cancel delete", "Dismiss"),
                description = "Cancel profile deletion",
                isAvailable = { getPersonToDelete() != null },
                onExecute = {
                    setPersonToDelete(null)
                    VoiceActionResult.Success("Deletion cancelled.")
                }
            )
        )

        // 4. Dynamic profile deletion commands for currently saved people
        val savedPeople = repository?.getSavedPeople() ?: emptyList()
        for (person in savedPeople) {
            actions.add(
                VoiceAction(
                    id = "delete_person_${person.id}",
                    screenContext = screenContext,
                    visibleLabel = "Delete ${person.name}",
                    canonicalCommand = "Delete profile for ${person.name}",
                    aliases = listOf("Delete ${person.name}", "Remove ${person.name}", "Delete profile for ${person.name}"),
                    description = "Delete profile for ${person.name}",
                    onExecute = {
                        setPersonToDelete(person)
                        VoiceActionResult.Success(
                            "Are you sure you want to delete profile for ${person.name}? Say Delete to confirm, or Cancel."
                        )
                    }
                )
            )
        }

        return actions
    }

    // =========================================================================
    // 5. ENROLL PERSON SCREEN
    // =========================================================================

    fun createEnrollPersonActions(
        isCameraActive: Boolean,
        onCancel: () -> Unit,
        getPersonName: () -> String,
        setPersonName: (String) -> Unit,
        getConsentChecked: () -> Boolean,
        setConsentChecked: (Boolean) -> Unit,
        onStartCapture: () -> Unit,
        isComplete: () -> Boolean,
        onDone: () -> Unit
    ): List<VoiceAction> {
        val screenContext = VoiceScreenContext.ENROLL_PERSON
        val actions = mutableListOf<VoiceAction>()

        if (!isCameraActive) {
            // Step 1: Identity & Consent
            actions.add(
                VoiceAction(
                    id = "enroll_back",
                    screenContext = screenContext,
                    visibleLabel = "BACK",
                    canonicalCommand = "Back",
                    aliases = listOf("Cancel", "Cancel enrollment"),
                    description = "Cancel enrollment and return to menu",
                    onExecute = {
                        onCancel()
                        VoiceActionResult.Success("Enrollment cancelled.")
                    }
                )
            )

            actions.add(
                VoiceAction(
                    id = "enroll_name_field",
                    screenContext = screenContext,
                    visibleLabel = "Person Name",
                    canonicalCommand = "Person name",
                    aliases = listOf("Enter name", "Name field"),
                    description = "Select person name field and begin dictation",
                    isDictation = true,
                    onExecute = {
                        VoiceActionResult.StartDictation(
                            prompt = "Person name field ready. Say the person's name.",
                            fieldType = DictationFieldType.PERSON_NAME
                        )
                    }
                )
            )

            // Suggestion chips
            listOf("Father", "Mother", "Arun").forEach { suggestion ->
                actions.add(
                    VoiceAction(
                        id = "enroll_chip_$suggestion",
                        screenContext = screenContext,
                        visibleLabel = suggestion,
                        canonicalCommand = suggestion,
                        description = "Set name to $suggestion",
                        onExecute = {
                            setPersonName(suggestion)
                            VoiceActionResult.Success("Person name set to $suggestion.")
                        }
                    )
                )
            }

            // Visible Consent Control (Core requirement from AGENTS.md & Phase 6)
            actions.add(
                VoiceAction(
                    id = "enroll_consent_action",
                    screenContext = screenContext,
                    visibleLabel = "Consent obtained",
                    canonicalCommand = "Consent obtained",
                    aliases = listOf("I confirm consent", "Confirm consent", "Consent"),
                    description = "Confirm explicit informed consent to enroll this face",
                    onExecute = {
                        val current = getConsentChecked()
                        val updated = !current
                        setConsentChecked(updated)
                        val status = if (updated) "confirmed" else "unconfirmed"
                        VoiceActionResult.Success("Enrollment consent $status.")
                    }
                )
            )

            // START FACE CAPTURE
            actions.add(
                VoiceAction(
                    id = "enroll_start_capture",
                    screenContext = screenContext,
                    visibleLabel = "START FACE CAPTURE",
                    canonicalCommand = "Start face capture",
                    aliases = listOf("Start capture", "Begin capture", "Take photos"),
                    description = "Begin camera-guided face capture",
                    isAvailable = { getPersonName().isNotBlank() && getConsentChecked() },
                    onExecute = {
                        val name = getPersonName().trim()
                        val consent = getConsentChecked()
                        when {
                            name.isBlank() -> {
                                VoiceActionResult.Error("Please enter a person name before starting face capture.")
                            }
                            !consent -> {
                                VoiceActionResult.Error("Explicit consent must be confirmed before enrolling face.")
                            }
                            else -> {
                                onStartCapture()
                                VoiceActionResult.Success("Starting camera face capture for $name.")
                            }
                        }
                    }
                )
            )
        } else {
            // Step 2: Live Camera Capture
            actions.add(
                VoiceAction(
                    id = "enroll_camera_cancel",
                    screenContext = screenContext,
                    visibleLabel = "CANCEL",
                    canonicalCommand = "Cancel",
                    aliases = listOf("Cancel capture", "Stop"),
                    description = "Cancel camera face capture and unbind camera",
                    onExecute = {
                        onCancel()
                        VoiceActionResult.Success("Face capture cancelled.")
                    }
                )
            )

            actions.add(
                VoiceAction(
                    id = "enroll_done",
                    screenContext = screenContext,
                    visibleLabel = "DONE (RETURN TO MAIN)",
                    canonicalCommand = "Done",
                    aliases = listOf("Done return to main", "Finish", "Complete enrollment"),
                    description = "Complete face enrollment and return to main screen",
                    isAvailable = { isComplete() },
                    onExecute = {
                        if (isComplete()) {
                            onDone()
                            VoiceActionResult.Success("Face profile successfully enrolled.")
                        } else {
                            VoiceActionResult.Error("Face capture is still in progress. Please wait for samples.")
                        }
                    }
                )
            )
        }

        return actions
    }

    // =========================================================================
    // 6. EMERGENCY SOS SCREEN
    // =========================================================================

    fun createSosActions(
        emergencyController: EmergencyController,
        onExitSos: () -> Unit,
        getIsEditingContact: () -> Boolean,
        setIsEditingContact: (Boolean) -> Unit,
        getContactNameInput: () -> String,
        setContactNameInput: (String) -> Unit,
        getContactPhoneInput: () -> String,
        setContactPhoneInput: (String) -> Unit,
        onSaveContact: () -> Unit
    ): List<VoiceAction> {
        val screenContext = VoiceScreenContext.SOS_EMERGENCY
        val actions = mutableListOf<VoiceAction>()

        // 1. Return to Main Menu / Back
        actions.add(
            VoiceAction(
                id = "sos_back",
                screenContext = screenContext,
                visibleLabel = "RETURN TO MAIN MENU",
                canonicalCommand = "Return to main menu",
                aliases = listOf("Back", "Return to main", "Exit SOS"),
                description = "Return to main menu and exit SOS mode",
                onExecute = {
                    emergencyController.deactivate()
                    onExitSos()
                    VoiceActionResult.Success("Exiting Emergency SOS. Returning to dashboard.")
                }
            )
        )

        // 2. Edit contact
        actions.add(
            VoiceAction(
                id = "sos_edit_contact",
                screenContext = screenContext,
                visibleLabel = "EDIT",
                canonicalCommand = "Edit",
                aliases = listOf("Edit contact", "Change contact"),
                description = "Edit emergency contact information",
                isAvailable = { !getIsEditingContact() },
                onExecute = {
                    setIsEditingContact(true)
                    VoiceActionResult.Success("Editing emergency contact. Say Contact Name or Phone Number.")
                }
            )
        )

        // 3. Contact editing dictation fields
        actions.add(
            VoiceAction(
                id = "sos_field_name",
                screenContext = screenContext,
                visibleLabel = "Contact Name",
                canonicalCommand = "Contact name",
                aliases = listOf("Name", "Edit name"),
                description = "Dictate emergency contact name",
                isAvailable = { getIsEditingContact() },
                isDictation = true,
                onExecute = {
                    VoiceActionResult.StartDictation(
                        prompt = "Contact name field ready. Say contact name.",
                        fieldType = DictationFieldType.EMERGENCY_CONTACT_NAME
                    )
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "sos_field_phone",
                screenContext = screenContext,
                visibleLabel = "Phone Number",
                canonicalCommand = "Phone number",
                aliases = listOf("Phone", "Number", "Mobile"),
                description = "Dictate emergency contact phone number",
                isAvailable = { getIsEditingContact() },
                isDictation = true,
                onExecute = {
                    VoiceActionResult.StartDictation(
                        prompt = "Phone number field ready. Say the phone number digits.",
                        fieldType = DictationFieldType.EMERGENCY_CONTACT_PHONE
                    )
                }
            )
        )

        actions.add(
            VoiceAction(
                id = "sos_save_contact",
                screenContext = screenContext,
                visibleLabel = "SAVE CONTACT",
                canonicalCommand = "Save contact",
                aliases = listOf("Save", "Save changes"),
                description = "Validate and save emergency contact",
                isAvailable = { getIsEditingContact() },
                onExecute = {
                    val name = getContactNameInput().trim()
                    val phone = getContactPhoneInput().trim()
                    if (name.isBlank()) {
                        VoiceActionResult.Error("Please enter a contact name.")
                    } else if (!EmergencyContact.isValidPhoneNumber(phone)) {
                        VoiceActionResult.Error("Please enter a valid phone number with 7 to 15 digits.")
                    } else {
                        onSaveContact()
                        setIsEditingContact(false)
                        VoiceActionResult.Success("Emergency contact saved: $name, $phone.")
                    }
                }
            )
        )

        // 4. TRIGGER SOS button
        actions.add(
            VoiceAction(
                id = "sos_trigger",
                screenContext = screenContext,
                visibleLabel = "TRIGGER SOS",
                canonicalCommand = "Trigger SOS",
                aliases = listOf("Trigger emergency", "Send SOS", "Send emergency"),
                description = "Initiate emergency assistance countdown",
                isAvailable = { !emergencyController.isTriggered },
                onExecute = {
                    emergencyController.triggerSos()
                    VoiceActionResult.Success("Emergency SOS triggered. Countdown started.")
                }
            )
        )

        // 5. CANCEL SOS button
        actions.add(
            VoiceAction(
                id = "sos_cancel",
                screenContext = screenContext,
                visibleLabel = "CANCEL SOS",
                canonicalCommand = "Cancel SOS",
                aliases = listOf("Cancel", "Abort SOS", "Cancel emergency"),
                description = "Cancel active emergency SOS countdown or dispatch",
                isAvailable = { emergencyController.isTriggered },
                onExecute = {
                    emergencyController.cancelSos()
                    VoiceActionResult.Success("Emergency SOS cancelled.")
                }
            )
        )

        return actions
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(Locale.US, "%.1f kilometers", meters / 1000.0)
        } else {
            "${meters.toInt()} meters"
        }
    }

    /**
     * Converts spoken numbers (e.g. "plus nine one nine eight...") into a clean phone number string.
     */
    fun normalizeSpokenPhoneNumber(spoken: String): String {
        val wordDigitMap = mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
            "plus" to "+"
        )
        var result = spoken.lowercase(Locale.ROOT)
        for ((word, digit) in wordDigitMap) {
            result = result.replace(word, digit)
        }
        return result.replace(Regex("[^0-9+]"), "")
    }
}
