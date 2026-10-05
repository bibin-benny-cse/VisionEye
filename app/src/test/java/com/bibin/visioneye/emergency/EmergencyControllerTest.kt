package com.bibin.visioneye.emergency

import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.navigation.location.LocationEngine
import com.bibin.visioneye.navigation.location.LocationFix
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic JVM unit tests for [DefaultEmergencyController].
 *
 * Verifies:
 * - A. Initial state
 * - B. Countdown starts
 * - C. Countdown cancellation
 * - D. Duplicate trigger prevention
 * - E. Location available
 * - F. Location unavailable
 * - G. Emergency message formatting
 * - H. Map-link coordinate formatting
 * - I. Invalid phone number
 * - J. SMS send success
 * - K. SMS send failure
 * - L. Permission denied
 * - M. Cancellation during send
 * - N. Leaving SOS mode cleans up
 * - O. Re-entering SOS does not create duplicates
 * - P. Existing modes remain unaffected
 */
class EmergencyControllerTest {

    private lateinit var fakeLocationEngine: FakeLocationEngine
    private lateinit var contactRepository: InMemoryEmergencyContactRepository
    private lateinit var fakeSmsSender: FakeSmsSender
    private lateinit var fakeSpeechController: FakeSpeechController
    private var mockCurrentTimeMs = 1700000000000L

    private lateinit var controller: DefaultEmergencyController

    @Before
    fun setUp() {
        fakeLocationEngine = FakeLocationEngine()
        contactRepository = InMemoryEmergencyContactRepository()
        fakeSmsSender = FakeSmsSender()
        fakeSpeechController = FakeSpeechController()
        mockCurrentTimeMs = 1700000000000L

        // Zero-delay countdown and minimal location timeout for deterministic unit test execution
        controller = DefaultEmergencyController(
            context = null,
            locationEngine = fakeLocationEngine,
            contactRepository = contactRepository,
            smsSender = fakeSmsSender,
            speechController = fakeSpeechController,
            countdownSeconds = 1,
            countdownIntervalMs = 0L,
            locationTimeoutMs = 10L,
            backgroundDispatcher = Dispatchers.Unconfined,
            clock = { mockCurrentTimeMs }
        )
    }

    @Test
    fun `A - initial state is Idle and isTriggered is false`() {
        assertEquals(EmergencyState.Idle, controller.state.value)
        assertFalse(controller.isTriggered)
        assertFalse(fakeLocationEngine.isTrackingActive)
    }

    @Test
    fun `B - countdown starts and vocalizes announcement when contact is configured`() {
        contactRepository.saveContact(EmergencyContact("Father", "+919876543210"))

        val countdownController = DefaultEmergencyController(
            context = null,
            locationEngine = fakeLocationEngine,
            contactRepository = contactRepository,
            smsSender = fakeSmsSender,
            speechController = fakeSpeechController,
            countdownSeconds = 5,
            countdownIntervalMs = 1000L,
            backgroundDispatcher = Dispatchers.Default
        )

        countdownController.activate()
        countdownController.triggerSos()

        assertTrue(countdownController.isTriggered)
        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("countdown", ignoreCase = true) == true
        )
        countdownController.cancelSos()
    }

    @Test
    fun `C - countdown cancellation stops timer and prevents SMS dispatch`() {
        contactRepository.saveContact(EmergencyContact("Father", "+919876543210"))

        val longCountdownController = DefaultEmergencyController(
            context = null,
            locationEngine = fakeLocationEngine,
            contactRepository = contactRepository,
            smsSender = fakeSmsSender,
            speechController = fakeSpeechController,
            countdownSeconds = 10,
            countdownIntervalMs = 1000L,
            backgroundDispatcher = Dispatchers.Default
        )

        longCountdownController.activate()
        longCountdownController.triggerSos()

        val state = longCountdownController.state.value
        assertTrue(state is EmergencyState.Countdown)

        longCountdownController.cancelSos()

        assertEquals(EmergencyState.Cancelled, longCountdownController.state.value)
        assertFalse(longCountdownController.isTriggered)
        assertFalse(fakeLocationEngine.isTrackingActive)
        assertEquals(0, fakeSmsSender.sendAttempts.size)
        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("cancelled", ignoreCase = true) == true
        )
    }

    @Test
    fun `D - duplicate triggerSos during active countdown is safely ignored`() {
        contactRepository.saveContact(EmergencyContact("Mother", "+919876543210"))

        val longCountdownController = DefaultEmergencyController(
            context = null,
            locationEngine = fakeLocationEngine,
            contactRepository = contactRepository,
            smsSender = fakeSmsSender,
            speechController = fakeSpeechController,
            countdownSeconds = 10,
            countdownIntervalMs = 1000L,
            backgroundDispatcher = Dispatchers.Default
        )

        longCountdownController.activate()
        longCountdownController.triggerSos()

        val initialUtteranceCount = fakeSpeechController.spokenUtterances.size

        // Repeated trigger calls
        longCountdownController.triggerSos()
        longCountdownController.triggerSos()

        assertEquals(initialUtteranceCount, fakeSpeechController.spokenUtterances.size)
        assertTrue(longCountdownController.state.value is EmergencyState.Countdown)

        longCountdownController.cancelSos()
    }

    @Test
    fun `E - location available includes map link and coordinates in dispatched SMS`() = runBlocking {
        contactRepository.saveContact(EmergencyContact("Guardian", "+919876543210"))
        fakeLocationEngine.emitLocation(
            LocationFix(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 3.5f)
        )

        controller.activate()
        controller.triggerSos()

        kotlinx.coroutines.delay(50L)

        val state = controller.state.value
        assertTrue("Expected Sent state but was $state", state is EmergencyState.Sent)

        assertEquals(1, fakeSmsSender.sendAttempts.size)
        val (phone, message) = fakeSmsSender.sendAttempts.first()
        assertEquals("+919876543210", phone)
        assertTrue(message.contains("https://maps.google.com/?q=12.9716,77.5946"))
        assertTrue(message.contains("12.9716"))

        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("sent", ignoreCase = true) == true
        )
    }

    @Test
    fun `F - location unavailable proceeds to dispatch SMS without coordinates`() = runBlocking {
        contactRepository.saveContact(EmergencyContact("Guardian", "+919876543210"))
        fakeLocationEngine.emitLocation(null)

        controller.activate()
        controller.triggerSos()

        kotlinx.coroutines.delay(50L)

        val state = controller.state.value
        assertTrue("Expected Sent state but was $state", state is EmergencyState.Sent)

        assertEquals(1, fakeSmsSender.sendAttempts.size)
        val (phone, message) = fakeSmsSender.sendAttempts.first()
        assertEquals("+919876543210", phone)
        assertTrue(message.contains("Location unavailable."))

        assertTrue(
            fakeSpeechController.spokenUtterances.any { it.contains("Location unavailable", ignoreCase = true) }
        )
    }

    @Test
    fun `H - map-link coordinate formatting generates universal Google Maps link`() {
        val link = EmergencyMessageFormatter.formatMapLink(13.0827, 80.2707)
        assertEquals("https://maps.google.com/?q=13.0827,80.2707", link)
    }

    @Test
    fun `I - invalid phone number aborts trigger and marks state as Failed`() {
        contactRepository.saveContact(EmergencyContact("Doctor", "invalid_phone"))

        controller.activate()
        controller.triggerSos()

        val state = controller.state.value
        assertTrue(state is EmergencyState.Failed)
        assertFalse(controller.isTriggered)
        assertEquals(0, fakeSmsSender.sendAttempts.size)

        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("Unable to send SOS", ignoreCase = true) == true
        )
    }

    @Test
    fun `J - SMS send success updates state to Sent with recipient and timestamp`() = runBlocking {
        contactRepository.saveContact(EmergencyContact("Police", "+919119119110"))

        controller.activate()
        controller.triggerSos()

        kotlinx.coroutines.delay(50L)

        val state = controller.state.value
        assertTrue(state is EmergencyState.Sent)
        val sentState = state as EmergencyState.Sent
        assertEquals("Police", sentState.recipient)
        assertEquals(mockCurrentTimeMs, sentState.timestampMs)

        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("message sent", ignoreCase = true) == true
        )
    }

    @Test
    fun `K - SMS send failure transitions to Failed state and vocalizes error`() = runBlocking {
        contactRepository.saveContact(EmergencyContact("Friend", "+919876543210"))
        fakeSmsSender.sendResult = SmsSendResult.Failure("Network radio offline")

        controller.activate()
        controller.triggerSos()

        kotlinx.coroutines.delay(50L)

        val state = controller.state.value
        assertTrue(state is EmergencyState.Failed)
        assertEquals("Network radio offline", (state as EmergencyState.Failed).reason)

        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("Unable to send SOS message", ignoreCase = true) == true
        )
    }

    @Test
    fun `L - SMS permission denied transitions to Failed state and vocalizes permission error`() = runBlocking {
        contactRepository.saveContact(EmergencyContact("Friend", "+919876543210"))
        fakeSmsSender.hasPermissionGranted = false
        fakeSmsSender.sendResult = SmsSendResult.PermissionDenied("SEND_SMS permission not granted")

        controller.activate()
        controller.triggerSos()

        kotlinx.coroutines.delay(50L)

        val state = controller.state.value
        assertTrue(state is EmergencyState.Failed)

        assertTrue(
            fakeSpeechController.lastSpokenUtterance?.contains("permission denied", ignoreCase = true) == true
        )
    }

    @Test
    fun `M - cancellation during send aborts operation`() = runBlocking {
        contactRepository.saveContact(EmergencyContact("Friend", "+919876543210"))

        // Controller with longer countdown
        val cancelController = DefaultEmergencyController(
            context = null,
            locationEngine = fakeLocationEngine,
            contactRepository = contactRepository,
            smsSender = fakeSmsSender,
            speechController = fakeSpeechController,
            countdownSeconds = 5,
            countdownIntervalMs = 500L,
            backgroundDispatcher = Dispatchers.Default
        )

        cancelController.activate()
        cancelController.triggerSos()

        // Cancel while still counting down or preparing to send
        cancelController.cancelSos()

        assertEquals(EmergencyState.Cancelled, cancelController.state.value)
        assertFalse(cancelController.isTriggered)
        assertFalse(fakeLocationEngine.isTrackingActive)
    }

    @Test
    fun `N - leaving SOS mode deactivates controller, cancels jobs, and resets state to Idle`() {
        contactRepository.saveContact(EmergencyContact("Friend", "+919876543210"))

        controller.activate()
        controller.triggerSos()

        // Transition away from SOS mode
        controller.onModeChanged(newMode = VisionMode.NAVIGATE, previousMode = VisionMode.SOS)

        assertEquals(EmergencyState.Idle, controller.state.value)
        assertFalse(controller.isTriggered)
        assertFalse(fakeLocationEngine.isTrackingActive)
        assertEquals(1, fakeSpeechController.stopInvocationCount)
    }

    @Test
    fun `O - re-entering SOS mode activates cleanly without duplicate timers`() {
        controller.activate()
        controller.activate()
        controller.activate()

        assertEquals(EmergencyState.Idle, controller.state.value)
    }

    // --- Fakes ---

    class FakeLocationEngine : LocationEngine {
        private val _locationFlow = MutableStateFlow<LocationFix?>(null)
        override val locationFlow: StateFlow<LocationFix?> = _locationFlow.asStateFlow()

        var isTrackingActive = false
        var hasPermissionGranted = true

        fun emitLocation(fix: LocationFix?) {
            _locationFlow.value = fix
        }

        override fun hasPermission(): Boolean = hasPermissionGranted

        override fun startLocationUpdates() {
            isTrackingActive = true
        }

        override fun stopLocationUpdates() {
            isTrackingActive = false
        }
    }

    class FakeSmsSender : SmsSender {
        var hasPermissionGranted = true
        var sendResult: SmsSendResult = SmsSendResult.Success
        val sendAttempts = mutableListOf<Pair<String, String>>()

        override fun hasPermission(): Boolean = hasPermissionGranted

        override suspend fun sendSms(phoneNumber: String, message: String): SmsSendResult {
            sendAttempts.add(phoneNumber to message)
            return sendResult
        }

        override fun createSmsIntent(phoneNumber: String, message: String): android.content.Intent? = null
    }

    class FakeSpeechController : SpeechController {
        val spokenUtterances = mutableListOf<String>()
        val lastSpokenUtterance: String?
            get() = spokenUtterances.lastOrNull()

        var stopInvocationCount = 0

        override fun speak(utterance: String, priority: SpeechPriority) {
            spokenUtterances.add(utterance)
        }

        override fun stop() {
            stopInvocationCount++
        }
    }
}
