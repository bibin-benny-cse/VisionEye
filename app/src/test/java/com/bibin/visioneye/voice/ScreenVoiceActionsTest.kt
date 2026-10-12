package com.bibin.visioneye.voice

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.bibin.visioneye.ai.BoundingBox
import com.bibin.visioneye.ai.YoloDebugState
import com.bibin.visioneye.camera.CameraController
import com.bibin.visioneye.camera.CameraState
import com.bibin.visioneye.camera.FrameAnalysisDiagnostics
import com.bibin.visioneye.camera.FrameAnalyzer
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.currency.CurrencyDenomination
import com.bibin.visioneye.currency.CurrencyDetection
import com.bibin.visioneye.currency.CurrencyState
import com.bibin.visioneye.emergency.EmergencyContact
import com.bibin.visioneye.emergency.EmergencyContactRepository
import com.bibin.visioneye.emergency.EmergencyController
import com.bibin.visioneye.emergency.EmergencyState
import com.bibin.visioneye.navigation.GpsStatus
import com.bibin.visioneye.navigation.NavigationController
import com.bibin.visioneye.navigation.NavigationGpsDiagnostics
import com.bibin.visioneye.navigation.NavigationState
import com.bibin.visioneye.navigation.guidance.GuidanceProgress
import com.bibin.visioneye.navigation.guidance.GuidanceStatus
import com.bibin.visioneye.navigation.location.LocationFix
import com.bibin.visioneye.people.EnrollmentState
import com.bibin.visioneye.people.PeopleRepository
import com.bibin.visioneye.people.PeopleState
import com.bibin.visioneye.people.SavedPerson
import com.bibin.visioneye.read.ReadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fake CameraController for deterministic unit testing of Camera Assistance voice actions.
 */
class FakeCameraController(
    override val yoloState: StateFlow<YoloDebugState> = MutableStateFlow(YoloDebugState()),
    override val currencyState: StateFlow<CurrencyState> = MutableStateFlow(CurrencyState()),
    override val readState: StateFlow<ReadState> = MutableStateFlow(ReadState.Searching),
    override val peopleState: StateFlow<PeopleState> = MutableStateFlow(PeopleState()),
    override val enrollmentState: StateFlow<EnrollmentState> = MutableStateFlow(EnrollmentState())
) : CameraController {
    override val state: StateFlow<CameraState> = MutableStateFlow(CameraState.Streaming)
    override val diagnostics: StateFlow<FrameAnalysisDiagnostics> = MutableStateFlow(FrameAnalysisDiagnostics())
    override val hasPermission: Boolean = true

    var stopCameraCalled = false
    var scanAgainCalled = false
    var capturePageNowCalled = false

    override fun stopCamera() { stopCameraCalled = true }
    override fun scanAgain() { scanAgainCalled = true }
    override fun capturePageNow() { capturePageNowCalled = true }
    override fun checkCameraPermission(): Boolean = true
    override fun bindCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider?) {}
    override fun unbindCamera() {}
    override fun startCamera() {}
    override fun pauseCamera() {}
    override fun setFrameAnalyzer(analyzer: FrameAnalyzer) {}
    override fun clearFrameAnalyzer() {}
    override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {}
}

/**
 * Unit tests verifying exact visible UI labels and canonical voice commands in [ScreenVoiceActions].
 */
class ScreenVoiceActionsTest {

    // =========================================================================
    // 1. MAIN SCREEN COMMANDS
    // =========================================================================

    @Test
    fun mainScreen_mapsAllEightCanonicalVisibleLabels() = runBlocking {
        var startCameraCalled = false
        var startReadCalled = false
        var startCurrencyCalled = false
        var startPeopleCalled = false
        var savePersonCalled = false
        var managePeopleCalled = false
        var startNavigationCalled = false
        var openSosCalled = false

        val actions = ScreenVoiceActions.createMainScreenActions(
            onStartCamera = { startCameraCalled = true },
            onStartReadMode = { startReadCalled = true },
            onStartCurrencyMode = { startCurrencyCalled = true },
            onStartPeopleMode = { startPeopleCalled = true },
            onSavePerson = { savePersonCalled = true },
            onManagePeople = { managePeopleCalled = true },
            onStartNavigation = { startNavigationCalled = true },
            onOpenSos = { openSosCalled = true }
        )

        assertEquals(8, actions.size)

        // Verify exact visible labels
        val labels = actions.map { it.visibleLabel }.toSet()
        assertTrue(labels.contains("NAVIGATE"))
        assertTrue(labels.contains("READ TEXT"))
        assertTrue(labels.contains("SCAN CURRENCY"))
        assertTrue(labels.contains("RECOGNIZE PEOPLE"))
        assertTrue(labels.contains("SAVE PERSON"))
        assertTrue(labels.contains("MANAGE PEOPLE"))
        assertTrue(labels.contains("OUTDOOR GPS NAVIGATION"))
        assertTrue(labels.contains("EMERGENCY SOS"))

        // Verify execution of each canonical command
        actions.first { it.visibleLabel == "NAVIGATE" }.onExecute()
        assertTrue(startCameraCalled)

        actions.first { it.visibleLabel == "READ TEXT" }.onExecute()
        assertTrue(startReadCalled)

        actions.first { it.visibleLabel == "SCAN CURRENCY" }.onExecute()
        assertTrue(startCurrencyCalled)

        actions.first { it.visibleLabel == "RECOGNIZE PEOPLE" }.onExecute()
        assertTrue(startPeopleCalled)

        actions.first { it.visibleLabel == "SAVE PERSON" }.onExecute()
        assertTrue(savePersonCalled)

        actions.first { it.visibleLabel == "MANAGE PEOPLE" }.onExecute()
        assertTrue(managePeopleCalled)

        actions.first { it.visibleLabel == "OUTDOOR GPS NAVIGATION" }.onExecute()
        assertTrue(startNavigationCalled)

        // SAFETY TEST: Emergency SOS only opens screen, does not trigger dispatch
        actions.first { it.visibleLabel == "EMERGENCY SOS" }.onExecute()
        assertTrue(openSosCalled)
    }

    // =========================================================================
    // 2. CAMERA ASSISTANCE: NAVIGATE MODE
    // =========================================================================

    @Test
    fun cameraNavigate_mapsStopCameraAndBoxesToggle() = runBlocking {
        val fakeCamera = FakeCameraController()
        var stopCameraCalled = false
        var debugOverlay = true

        val actions = ScreenVoiceActions.createCameraAssistanceActions(
            currentMode = VisionMode.NAVIGATE,
            cameraController = fakeCamera,
            onStopCamera = { stopCameraCalled = true },
            getShowDebugOverlay = { debugOverlay },
            onToggleOverlay = { debugOverlay = !debugOverlay }
        )

        val stopAction = actions.firstOrNull { it.visibleLabel == "STOP CAMERA" }
        assertNotNull(stopAction)
        stopAction?.onExecute()
        assertTrue(fakeCamera.stopCameraCalled)
        assertTrue(stopCameraCalled)

        val boxesAction = actions.firstOrNull { it.visibleLabel == "BOXES: ON" }
        assertNotNull(boxesAction)
        boxesAction?.onExecute()
        assertFalse(debugOverlay)
    }

    // =========================================================================
    // 3. CAMERA ASSISTANCE: CURRENCY MODE
    // =========================================================================

    @Test
    fun cameraCurrency_contextualQueriesRequireValidDetection() = runBlocking {
        val currencyFlow = MutableStateFlow(CurrencyState(isReady = true))
        val fakeCamera = FakeCameraController(currencyState = currencyFlow)

        val actions = ScreenVoiceActions.createCameraAssistanceActions(
            currentMode = VisionMode.CURRENCY,
            cameraController = fakeCamera,
            onStopCamera = {},
            getShowDebugOverlay = { false },
            onToggleOverlay = {}
        )

        val whatNoteAction = actions.first { it.visibleLabel == "CURRENCY" }

        // When no detection yet
        val resultNoDet = whatNoteAction.onExecute() as VoiceActionResult.Success
        assertTrue(resultNoDet.message?.contains("No Indian banknote detected") == true)

        // When valid confirmed detection present
        currencyFlow.value = CurrencyState(
            isReady = true,
            isConfirmed = true,
            confirmedDenominations = listOf(CurrencyDenomination.FIVE_HUNDRED),
            detections = listOf(
                CurrencyDetection(
                    denomination = CurrencyDenomination.FIVE_HUNDRED,
                    confidence = 0.95f,
                    boundingBox = BoundingBox(0.2f, 0.2f, 0.8f, 0.8f)
                )
            )
        )
        val resultWithDet = whatNoteAction.onExecute() as VoiceActionResult.Success
        assertTrue(resultWithDet.message?.contains("500") == true)
    }

    // =========================================================================
    // 4. CAMERA ASSISTANCE: READ TEXT MODE
    // =========================================================================

    @Test
    fun cameraRead_preventsCaptureDuringOcrProcessing() = runBlocking {
        val readFlow = MutableStateFlow<ReadState>(ReadState.Searching)
        val fakeCamera = FakeCameraController(readState = readFlow)

        val actions = ScreenVoiceActions.createCameraAssistanceActions(
            currentMode = VisionMode.READ,
            cameraController = fakeCamera,
            onStopCamera = {},
            getShowDebugOverlay = { false },
            onToggleOverlay = {}
        )

        val captureAction = actions.first { it.visibleLabel == "CAPTURE PAGE NOW" }

        // While searching, capture succeeds and calls capturePageNow()
        val successResult = captureAction.onExecute() as VoiceActionResult.Success
        assertTrue(fakeCamera.capturePageNowCalled)

        // While OCR processing, capture returns error explaining state
        readFlow.value = ReadState.ProcessingOcr()
        val errorResult = captureAction.onExecute() as VoiceActionResult.Error
        assertTrue(errorResult.errorMessage.contains("processing"))
    }

    // =========================================================================
    // 5. GPS NAVIGATION SCREEN
    // =========================================================================

    @Test
    fun navigationGps_mapsAllExactControlsAndTelemetryQueries() = runBlocking {
        var stopNavCalled = false
        var stopGuidanceCalled = false
        var destInput = ""
        var routeSearchQuery: String? = null

        val mockLocationFix = LocationFix(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 5.0f
        )

        val fakeNavController = object : NavigationController {
            override val state: StateFlow<NavigationState> = MutableStateFlow(NavigationState.Inactive)
            override val diagnostics: StateFlow<NavigationGpsDiagnostics> = MutableStateFlow(
                NavigationGpsDiagnostics(
                    status = GpsStatus.ACTIVE,
                    locationFix = mockLocationFix,
                    headingDegrees = 45.0f,
                    cardinalDirection = "NE",
                    guidanceProgress = GuidanceProgress(
                        status = GuidanceStatus.CONTINUE,
                        currentInstruction = "In 20 meters, turn right on 5th cross.",
                        distanceToNextManeuver = 20.0,
                        remainingRouteDistance = 250.0,
                        currentStepIndex = 1,
                        totalSteps = 3
                    )
                )
            )
            override fun activate() {}
            override fun deactivate() {}
            override fun onPermissionResult(granted: Boolean) {}
            override fun startGuidance(destinationAddress: String) {}
            override fun stopGuidance() { stopGuidanceCalled = true }
            override fun searchDestinationAndRoute(query: String) {}
            override fun cancelRouteSearch() {}
            override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {}
        }

        val actions = ScreenVoiceActions.createNavigationGpsActions(
            navigationController = fakeNavController,
            onStopNavigation = { stopNavCalled = true },
            getDestinationInput = { destInput },
            setDestinationInput = { destInput = it },
            onStartRouteSearch = { routeSearchQuery = it },
            onCancelRouteSearch = {}
        )

        // 1. Back
        actions.first { it.visibleLabel == "BACK" }.onExecute()
        assertTrue(stopNavCalled)

        // 2. Enter destination triggers separate dictation
        val enterDest = actions.first { it.visibleLabel == "Enter destination" }
        assertTrue(enterDest.isDictation)
        val dictationResult = enterDest.onExecute()
        assertTrue(dictationResult is VoiceActionResult.StartDictation)
        assertEquals(DictationFieldType.NAVIGATION_DESTINATION, (dictationResult as VoiceActionResult.StartDictation).fieldType)

        // 3. Start route
        destInput = "Central Station"
        val startRouteAction = actions.first { it.visibleLabel == "START / ROUTE" }
        assertTrue(startRouteAction.isAvailable())
        startRouteAction.onExecute()
        assertEquals("Central Station", routeSearchQuery)

        // 4. Stop Guidance
        actions.first { it.visibleLabel == "STOP GUIDANCE" }.onExecute()
        assertTrue(stopGuidanceCalled)

        // 5. Telemetry queries
        val gpsStatus = actions.first { it.visibleLabel == "GPS STATUS" }.onExecute() as VoiceActionResult.Success
        assertTrue(gpsStatus.message?.contains("Active") == true)

        val coords = actions.first { it.visibleLabel == "COORDINATE TELEMETRY" }.onExecute() as VoiceActionResult.Success
        assertTrue(coords.message?.contains("12.97160") == true)

        val compass = actions.first { it.visibleLabel == "COMPASS" }.onExecute() as VoiceActionResult.Success
        assertTrue(compass.message?.contains("NE") == true)

        val instruction = actions.first { it.visibleLabel == "Current instruction" }.onExecute() as VoiceActionResult.Success
        assertTrue(instruction.message?.contains("turn right") == true)
    }

    // =========================================================================
    // 6. ENROLL PERSON SCREEN
    // =========================================================================

    @Test
    fun enrollPerson_requiresNameAndConsentBeforeCapture() = runBlocking {
        var personName = ""
        var consentGiven = false
        var captureStarted = false

        val actions = ScreenVoiceActions.createEnrollPersonActions(
            isCameraActive = false,
            onCancel = {},
            getPersonName = { personName },
            setPersonName = { personName = it },
            getConsentChecked = { consentGiven },
            setConsentChecked = { consentGiven = it },
            onStartCapture = { captureStarted = true },
            isComplete = { false },
            onDone = {}
        )

        val captureAction = actions.first { it.visibleLabel == "START FACE CAPTURE" }

        // 1. Initially disabled because name and consent are missing
        assertFalse(captureAction.isAvailable())

        // 2. Set name via suggestion chip
        val arunChip = actions.first { it.visibleLabel == "Arun" }
        arunChip.onExecute()
        assertEquals("Arun", personName)
        assertFalse(captureAction.isAvailable())

        // 3. Confirm consent
        val consentAction = actions.first { it.visibleLabel == "Consent obtained" }
        consentAction.onExecute()
        assertTrue(consentGiven)
        assertTrue(captureAction.isAvailable())

        // 4. Now capture can start
        val result = captureAction.onExecute() as VoiceActionResult.Success
        assertTrue(captureStarted)
        assertTrue(result.message?.contains("Arun") == true)
    }

    // =========================================================================
    // 7. MANAGE PEOPLE SCREEN
    // =========================================================================

    @Test
    fun managePeople_deleteRequiresExactProfileNameAndConfirmation() = runBlocking {
        val people = listOf(
            SavedPerson(id = "p1", name = "Arun", embeddings = emptyList()),
            SavedPerson(id = "p2", name = "Mother", embeddings = emptyList())
        )
        val fakeRepo = object : PeopleRepository {
            override fun getSavedPeople(): List<SavedPerson> = people
            override fun getPersonById(id: String): SavedPerson? = people.find { it.id == id }
            override fun savePerson(person: SavedPerson) {}
            override fun deletePerson(id: String): Boolean = true
            override fun clearAll() {}
        }
        var pendingDelete: SavedPerson? = null
        var deletedId: String? = null

        val actions = ScreenVoiceActions.createManagePeopleActions(
            repository = fakeRepo,
            onBack = {},
            onAddNewPerson = {},
            getPersonToDelete = { pendingDelete },
            setPersonToDelete = { pendingDelete = it },
            onConfirmDelete = { person -> deletedId = person.id }
        )

        // Select profile for deletion
        val deleteArun = actions.firstOrNull { it.canonicalCommand == "Delete profile for Arun" }
        assertNotNull(deleteArun)
        deleteArun?.onExecute()
        assertEquals("Arun", pendingDelete?.name)

        // Confirmation actions become available
        val confirmAction = actions.first { it.visibleLabel == "DELETE" }
        assertTrue(confirmAction.isAvailable())
        confirmAction.onExecute()
        assertEquals("p1", deletedId)
    }

    // =========================================================================
    // 8. EMERGENCY SOS SCREEN
    // =========================================================================

    @Test
    fun sosScreen_triggerPreservesCountdownAndCancelStopsIt() = runBlocking {
        var isTriggered = false
        var triggerCount = 0
        var cancelCount = 0

        val fakeSosController = object : EmergencyController {
            override val isTriggered: Boolean get() = isTriggered
            override val state: StateFlow<EmergencyState> = MutableStateFlow(EmergencyState.Idle)
            override val contactRepository: EmergencyContactRepository = object : EmergencyContactRepository {
                override fun getContact(): EmergencyContact? = EmergencyContact("Mom", "+919876543210")
                override fun saveContact(contact: EmergencyContact) {}
                override fun clearContact() {}
            }
            override fun activate() {}
            override fun deactivate() {}
            override fun triggerSos(reason: String?) {
                isTriggered = true
                triggerCount++
            }
            override fun cancelSos() {
                isTriggered = false
                cancelCount++
            }
            override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {}
        }

        val actions = ScreenVoiceActions.createSosActions(
            emergencyController = fakeSosController,
            onExitSos = {},
            getIsEditingContact = { false },
            setIsEditingContact = {},
            getContactNameInput = { "Mom" },
            setContactNameInput = {},
            getContactPhoneInput = { "+919876543210" },
            setContactPhoneInput = {},
            onSaveContact = {}
        )

        val triggerAction = actions.first { it.visibleLabel == "TRIGGER SOS" }
        val cancelAction = actions.first { it.visibleLabel == "CANCEL SOS" }

        assertTrue(triggerAction.isAvailable())
        assertFalse(cancelAction.isAvailable())

        // Trigger SOS
        triggerAction.onExecute()
        assertEquals(1, triggerCount)
        assertTrue(isTriggered)

        // Cancel SOS
        assertTrue(cancelAction.isAvailable())
        cancelAction.onExecute()
        assertEquals(1, cancelCount)
        assertFalse(isTriggered)
    }

    // =========================================================================
    // 9. PHONE NUMBER NORMALIZATION
    // =========================================================================

    @Test
    fun normalizeSpokenPhoneNumber_convertsWordsToDigits() {
        val input = "plus nine one nine eight seven six five four three two one zero"
        val normalized = ScreenVoiceActions.normalizeSpokenPhoneNumber(input)
        assertEquals("+919876543210", normalized)
    }
}
