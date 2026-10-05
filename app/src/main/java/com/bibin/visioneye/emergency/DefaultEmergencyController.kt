package com.bibin.visioneye.emergency

import android.content.Context
import android.util.Log
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.navigation.location.LocationEngine
import com.bibin.visioneye.navigation.location.LocationFix
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Production implementation of [EmergencyController] for the VisionEye SOS subsystem.
 *
 * Responsibilities:
 * - Coordinates user-confirmed emergency SOS triggers
 * - Manages an accidental trigger protection countdown (default 3 seconds)
 * - Obtains current GPS position via [LocationEngine] with bounded timeout
 * - Dispatches emergency SMS to configured [EmergencyContact] via [SmsSender]
 * - Provides non-repetitive auditory feedback through [SpeechController]
 * - Ensures clean lifecycle management and resource deallocation on mode switch
 *
 * @param context Android application context, if available.
 * @param locationEngine Location provider for obtaining GPS coordinates.
 * @param contactRepository Local repository for emergency contacts.
 * @param smsSender Subsystem for dispatching SMS notifications.
 * @param speechController Speech synthesizer for vocal announcements.
 * @param countdownSeconds Number of seconds in the pre-dispatch confirmation countdown.
 * @param locationTimeoutMs Maximum time to wait for a GPS fix before sending without location.
 * @param backgroundDispatcher Dispatcher for asynchronous operations.
 * @param clock Epoch millisecond provider for deterministic testing.
 */
class DefaultEmergencyController(
    val context: Context? = null,
    val locationEngine: LocationEngine,
    override val contactRepository: EmergencyContactRepository,
    val smsSender: SmsSender,
    val speechController: SpeechController? = null,
    val countdownSeconds: Int = 3,
    val countdownIntervalMs: Long = 1000L,
    val locationTimeoutMs: Long = 4000L,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : EmergencyController {

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)

    private val _state = MutableStateFlow<EmergencyState>(EmergencyState.Idle)
    override val state: StateFlow<EmergencyState> = _state.asStateFlow()

    private var isActive = false
    private var sosJob: Job? = null

    private var lastSpokenText: String? = null
    private var lastSpokenTimeMs: Long = 0L

    override val isTriggered: Boolean
        get() = synchronized(lock) {
            when (_state.value) {
                is EmergencyState.Countdown,
                is EmergencyState.AcquiringLocation,
                is EmergencyState.Sending,
                is EmergencyState.Sent -> true
                is EmergencyState.Idle,
                is EmergencyState.Failed,
                is EmergencyState.Cancelled -> false
            }
        }

    override fun activate() {
        synchronized(lock) {
            if (isActive) return
            isActive = true
            _state.value = EmergencyState.Idle
            lastSpokenText = null
            lastSpokenTimeMs = 0L
            speakOnce("SOS mode active.", SpeechPriority.IMMEDIATE_SAFETY)
            Log.d(TAG, "EmergencyController activated.")
        }
    }

    override fun deactivate() {
        synchronized(lock) {
            if (!isActive) return
            isActive = false
            sosJob?.cancel()
            sosJob = null
            locationEngine.stopLocationUpdates()
            _state.value = EmergencyState.Idle
            lastSpokenText = null
            lastSpokenTimeMs = 0L
            speechController?.stop()
            Log.d(TAG, "EmergencyController deactivated.")
        }
    }

    override fun triggerSos(reason: String?) {
        synchronized(lock) {
            if (!isActive) {
                activate()
            }

            // Duplicate trigger protection: ignore if countdown, acquiring, or sending is underway
            if (isTriggered) {
                Log.w(TAG, "triggerSos ignored: SOS workflow is already active.")
                return
            }

            // 1. Validate emergency contact
            val contact = contactRepository.getContact()
            if (contact == null || !contact.isValid()) {
                _state.value = EmergencyState.Failed("No valid emergency contact configured")
                speakOnce("Unable to send SOS. No emergency contact configured.")
                Log.w(TAG, "SOS trigger aborted: No valid emergency contact found.")
                return
            }

            // 2. Start countdown
            sosJob?.cancel()
            _state.value = EmergencyState.Countdown(countdownSeconds)
            speakOnce("SOS countdown started.")

            sosJob = scope.launch {
                executeCountdownAndDispatch(contact)
            }
        }
    }

    private suspend fun executeCountdownAndDispatch(contact: EmergencyContact) {
        // A. Run confirmation countdown
        for (sec in countdownSeconds downTo 1) {
            synchronized(lock) {
                if (!isActive) return
                _state.value = EmergencyState.Countdown(sec)
            }
            if (countdownIntervalMs > 0L) {
                delay(countdownIntervalMs)
            }
        }

        // B. Acquire location
        synchronized(lock) {
            if (!isActive) return
            _state.value = EmergencyState.AcquiringLocation
        }

        var locationFix: LocationFix? = locationEngine.locationFlow.value
        if (locationFix == null && locationEngine.hasPermission()) {
            locationEngine.startLocationUpdates()
            try {
                withTimeoutOrNull(locationTimeoutMs) {
                    locationEngine.locationFlow.first { it != null }
                }?.let { fix ->
                    locationFix = fix
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Location acquisition timed out or interrupted for SOS", t)
            } finally {
                locationEngine.stopLocationUpdates()
            }
        }

        if (locationFix == null) {
            speakOnce("Location unavailable. Sending SOS without location.")
        }

        // C. Format emergency message
        val message = EmergencyMessageFormatter.formatMessage(locationFix)

        synchronized(lock) {
            if (!isActive) return
            _state.value = EmergencyState.Sending(message, locationFix)
        }

        // D. Dispatch SMS
        when (val sendResult = smsSender.sendSms(contact.phoneNumber, message)) {
            is SmsSendResult.Success -> {
                synchronized(lock) {
                    _state.value = EmergencyState.Sent(
                        message = message,
                        recipient = contact.name,
                        timestampMs = clock()
                    )
                }
                speakOnce("SOS message sent.")
                Log.d(TAG, "Emergency SOS successfully dispatched to ${contact.name}.")
            }
            is SmsSendResult.Failure -> {
                synchronized(lock) {
                    _state.value = EmergencyState.Failed(sendResult.reason)
                }
                speakOnce("Unable to send SOS message.")
                Log.e(TAG, "Emergency SOS failed: ${sendResult.reason}")
            }
            is SmsSendResult.PermissionDenied -> {
                synchronized(lock) {
                    _state.value = EmergencyState.Failed(sendResult.message)
                }
                speakOnce("Unable to send SOS message. SMS permission denied.")
                Log.e(TAG, "Emergency SOS blocked: SMS permission denied.")
            }
        }
    }

    override fun cancelSos() {
        synchronized(lock) {
            sosJob?.cancel()
            sosJob = null
            locationEngine.stopLocationUpdates()
            _state.value = EmergencyState.Cancelled
            speakOnce("SOS cancelled.")
            Log.d(TAG, "Emergency SOS cancelled by user.")
        }
    }

    override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {
        if (newMode == VisionMode.SOS) {
            Log.d(TAG, "Mode changed to SOS. Activating controller.")
            activate()
        } else if (previousMode == VisionMode.SOS) {
            Log.d(TAG, "Mode changed away from SOS to $newMode. Deactivating controller.")
            deactivate()
        }
    }

    private fun speakOnce(message: String, priority: SpeechPriority = SpeechPriority.IMMEDIATE_SAFETY) {
        val now = clock()
        if (message == lastSpokenText && (now - lastSpokenTimeMs) < 1500L) {
            return
        }
        lastSpokenText = message
        lastSpokenTimeMs = now
        speechController?.speak(message, priority)
    }

    companion object {
        private const val TAG = "DefaultEmergencyController"
    }
}
