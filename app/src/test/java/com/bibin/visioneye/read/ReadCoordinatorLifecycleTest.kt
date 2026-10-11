package com.bibin.visioneye.read

import com.bibin.visioneye.ai.BoundingBox
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority

class ReadCoordinatorLifecycleTest {

    private lateinit var fakeSpeech: FakeSpeechController
    private lateinit var coordinator: ReadCoordinator

    private class FakeSpeechController : SpeechController {
        val spokenUtterances = mutableListOf<Pair<String, SpeechPriority>>()
        var stopInvocationCount = 0
        val utteranceListeners = mutableListOf<com.bibin.visioneye.speech.SpeechUtteranceListener>()

        override fun addUtteranceListener(listener: com.bibin.visioneye.speech.SpeechUtteranceListener) {
            utteranceListeners.add(listener)
        }

        override fun removeUtteranceListener(listener: com.bibin.visioneye.speech.SpeechUtteranceListener) {
            utteranceListeners.remove(listener)
        }

        override fun speak(utterance: String, priority: SpeechPriority) {
            spokenUtterances.add(utterance to priority)
        }

        override fun speak(utterance: String, priority: SpeechPriority, utteranceId: String) {
            spokenUtterances.add(utterance to priority)
            for (listener in utteranceListeners.toList()) {
                listener.onUtteranceCompleted(utteranceId)
            }
        }

        override fun stop() {
            stopInvocationCount++
        }

        val lastSpokenUtterance: String? get() = spokenUtterances.lastOrNull()?.first
    }

    @Before
    fun setUp() {
        fakeSpeech = FakeSpeechController()
        coordinator = ReadCoordinator(
            speechController = fakeSpeech,
            backgroundDispatcher = Dispatchers.Unconfined
        )
    }

    private fun createPage(
        left: Float = 0.1f,
        top: Float = 0.1f,
        right: Float = 0.9f,
        bottom: Float = 0.9f
    ): DetectedPage {
        return DetectedPage(
            bounds = BoundingBox(left, top, right, bottom),
            corners = listOf(
                PagePoint(left, top),
                PagePoint(right, top),
                PagePoint(right, bottom),
                PagePoint(left, bottom)
            ),
            confidence = 0.95f,
            areaRatio = 0.64f
        )
    }

    @Test
    fun initialState_isSearching() {
        assertTrue(coordinator.readState.value is ReadState.Searching)
        assertFalse(coordinator.isCaptureInProgress)
        assertNull(coordinator.lastDetectedPage)
    }

    @Test
    fun activate_resetsTrackerAndStateToSearching() {
        coordinator.activate()

        assertTrue(coordinator.readState.value is ReadState.Searching)
        assertFalse(coordinator.isCaptureInProgress)
        assertNull(coordinator.lastDetectedPage)
        assertEquals(0, coordinator.stabilityTracker.stableCount)
    }

    @Test
    fun capturePageNow_whenNotActive_isIgnored() {
        // Not activated yet
        var captureTriggered = false
        coordinator.captureProvider = { _, _ -> captureTriggered = true }

        coordinator.capturePageNow()
        assertFalse("Capture must be ignored when coordinator is not active", captureTriggered)
        assertFalse(coordinator.isCaptureInProgress)
    }

    @Test
    fun capturePageNow_withoutDetectedPage_refusesFullFrameCaptureAndSpeaksGuidance() {
        coordinator.activate()
        assertNull(coordinator.lastDetectedPage)

        var captureInvoked = false
        coordinator.captureProvider = { _, _ -> captureInvoked = true }

        coordinator.capturePageNow()

        assertFalse("CaptureProvider must not be invoked when no page boundary is detected", captureInvoked)
        assertFalse("Capture must not be in progress", coordinator.isCaptureInProgress)
        val currentState = coordinator.readState.value
        assertTrue("State must transition to PageBoundaryNotDetected", currentState is ReadState.PageBoundaryNotDetected)
        assertEquals(
            "Page boundary not detected. Reposition the book and try again.",
            (currentState as ReadState.PageBoundaryNotDetected).message
        )
        assertEquals(
            "Page boundary not detected. Reposition the book and try again.",
            fakeSpeech.lastSpokenUtterance
        )
    }

    @Test
    fun noPageDetectedPath_unverifiedFullFrameNeverReachesOcrOrTts() {
        var rectifyCalled = false
        var extractTextCalled = false

        val trackingProcessor = object : PageImageProcessor() {
            override fun rectifyPage(bitmap: android.graphics.Bitmap, page: DetectedPage): android.graphics.Bitmap {
                rectifyCalled = true
                return bitmap
            }
        }
        val trackingOcr = object : OcrTextExtractor() {
            override suspend fun extractText(bitmap: android.graphics.Bitmap): String {
                extractTextCalled = true
                return "unexpected"
            }
        }

        val testCoordinator = ReadCoordinator(
            speechController = fakeSpeech,
            pageImageProcessor = trackingProcessor,
            ocrTextExtractor = trackingOcr,
            backgroundDispatcher = Dispatchers.Unconfined
        )
        testCoordinator.activate()
        assertNull(testCoordinator.lastDetectedPage)

        var captureInvoked = false
        testCoordinator.captureProvider = { _, _ -> captureInvoked = true }

        // Trigger manual capture with no detected page
        testCoordinator.capturePageNow()

        assertFalse("Capture must not be invoked", captureInvoked)
        assertFalse("Perspective rectification must not be called", rectifyCalled)
        assertFalse("OCR extraction must not be called", extractTextCalled)
        assertTrue(testCoordinator.readState.value is ReadState.PageBoundaryNotDetected)
        assertEquals(
            "Page boundary not detected. Reposition the book and try again.",
            fakeSpeech.lastSpokenUtterance
        )
    }

    @Test
    fun missingBoundaryFailure_isRecoverable_whenPageDetected() {
        coordinator.activate()
        assertNull(coordinator.lastDetectedPage)

        // Attempt capture with no page
        coordinator.capturePageNow()
        assertTrue(coordinator.readState.value is ReadState.PageBoundaryNotDetected)

        // User repositions book; page candidate detected
        val candidate = createPage()
        coordinator.lastDetectedPage = candidate

        var capturedPage: DetectedPage? = null
        coordinator.captureProvider = { _, _ ->
            val currentState = coordinator.readState.value
            if (currentState is ReadState.Capturing) {
                capturedPage = currentState.detectedPage
            }
        }

        // Retry manual capture
        coordinator.capturePageNow()

        assertTrue(coordinator.isCaptureInProgress)
        val capturingState = coordinator.readState.value as ReadState.Capturing
        assertTrue("Candidate page must have isVerifiedPage = true", capturingState.isVerifiedPage)
        assertEquals(candidate, capturedPage)
        assertEquals("Capturing page. Hold still.", fakeSpeech.lastSpokenUtterance)
    }

    @Test
    fun missingBoundaryFailure_isRecoverable_viaScanAgain() {
        coordinator.activate()
        coordinator.capturePageNow()
        assertTrue(coordinator.readState.value is ReadState.PageBoundaryNotDetected)

        coordinator.scanAgain()

        assertFalse(coordinator.isCaptureInProgress)
        assertNull(coordinator.lastDetectedPage)
        assertTrue(coordinator.readState.value is ReadState.Searching)
        assertEquals("Scanning again. Align text in camera view.", fakeSpeech.lastSpokenUtterance)
    }

    @Test
    fun capturePageNow_unverifiedCandidate_refusesCaptureAndSpeaksGuidance() {
        coordinator.activate()
        val unverifiedPage = DetectedPage(
            bounds = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            corners = listOf(
                PagePoint(0.1f, 0.1f),
                PagePoint(0.9f, 0.1f),
                PagePoint(0.9f, 0.9f),
                PagePoint(0.1f, 0.9f)
            ),
            confidence = 0.95f,
            areaRatio = 0.64f,
            isVerifiedBoundary = false
        )
        coordinator.lastDetectedPage = unverifiedPage

        var captureTriggered = false
        coordinator.captureProvider = { _, _ -> captureTriggered = true }

        coordinator.capturePageNow()

        assertFalse("Capture must never be triggered for unverified boundary", captureTriggered)
        assertFalse("Capture in progress must be false", coordinator.isCaptureInProgress)
        assertTrue(coordinator.readState.value is ReadState.PageBoundaryNotDetected)
        assertEquals(
            "Page boundary not detected. Reposition the book and try again.",
            fakeSpeech.lastSpokenUtterance
        )
    }

    @Test
    fun duplicateCapturePrevention_blocksConcurrentCaptureRequests() {
        coordinator.activate()
        coordinator.lastDetectedPage = createPage()
        var captureInvocations = 0

        coordinator.captureProvider = { _, _ ->
            captureInvocations++
            // Simulates long-running capture: do not invoke callback immediately
        }

        coordinator.capturePageNow()
        assertEquals(1, captureInvocations)
        assertTrue(coordinator.isCaptureInProgress)

        // Attempt second capture while first is in progress
        coordinator.capturePageNow()
        assertEquals("Duplicate capture request must be blocked", 1, captureInvocations)
    }

    @Test
    fun captureFailure_clearsCaptureInProgressFlagAndSetsErrorState() {
        coordinator.activate()
        coordinator.lastDetectedPage = createPage()
        coordinator.captureProvider = { _, onError ->
            onError(RuntimeException("Camera hardware error"))
        }

        coordinator.capturePageNow()

        assertFalse("Capture in progress must be cleared on failure", coordinator.isCaptureInProgress)
        val currentState = coordinator.readState.value
        assertTrue(currentState is ReadState.Error)
        assertEquals("Image capture failed.", (currentState as ReadState.Error).message)
    }

    @Test
    fun scanAgain_resetsCaptureInProgressAndRestoresSearchingState() {
        coordinator.activate()
        coordinator.lastDetectedPage = createPage()
        coordinator.captureProvider = { _, _ -> /* pending */ }
        coordinator.capturePageNow()
        assertTrue(coordinator.isCaptureInProgress)

        coordinator.scanAgain()

        assertFalse("scanAgain must clear isCaptureInProgress", coordinator.isCaptureInProgress)
        assertNull(coordinator.lastDetectedPage)
        assertTrue(coordinator.readState.value is ReadState.Searching)
    }

    @Test
    fun deactivate_cleansUpAllStates() {
        coordinator.activate()
        coordinator.lastDetectedPage = createPage()
        coordinator.captureProvider = { _, _ -> /* pending */ }
        coordinator.capturePageNow()

        coordinator.deactivate()

        assertFalse(coordinator.isCaptureInProgress)
        assertNull(coordinator.lastDetectedPage)
        assertTrue(coordinator.readState.value is ReadState.Searching)
    }

    @Test
    fun captureUnavailable_clearsCaptureInProgressAndSetsErrorState() {
        coordinator.activate()
        coordinator.lastDetectedPage = createPage()
        coordinator.captureProvider = null

        coordinator.capturePageNow()

        assertFalse(coordinator.isCaptureInProgress)
        val currentState = coordinator.readState.value
        assertTrue(currentState is ReadState.Error)
        assertEquals("Camera capture unavailable.", (currentState as ReadState.Error).message)
    }

    @Test
    fun successfulCapture_clearsStaleSpeech_andStoresDebugInspectionData() {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val mockBitmap = unsafe.allocateInstance(android.graphics.Bitmap::class.java) as android.graphics.Bitmap
        val testPage = createPage()

        val mockProcessor = object : PageImageProcessor() {
            override fun rectifyPage(bitmap: android.graphics.Bitmap, page: DetectedPage): android.graphics.Bitmap {
                return bitmap
            }
        }

        val mockOcr = object : OcrTextExtractor() {
            override suspend fun extractText(bitmap: android.graphics.Bitmap): String {
                return "The quick brown fox jumps over the lazy dog."
            }
        }

        val testCoordinator = ReadCoordinator(
            speechController = fakeSpeech,
            pageImageProcessor = mockProcessor,
            ocrTextExtractor = mockOcr,
            backgroundDispatcher = Dispatchers.Unconfined
        )

        testCoordinator.activate()
        testCoordinator.lastDetectedPage = testPage
        testCoordinator.captureProvider = { onSuccess, _ ->
            onSuccess(mockBitmap)
        }

        // Trigger capture
        testCoordinator.capturePageNow()
        kotlinx.coroutines.runBlocking {
            testCoordinator.activeJob?.join()
        }

        // 1. Verify speechController.stop() was called to flush stale preview guidance
        assertTrue("Speech stop must be invoked to flush stale announcements", fakeSpeech.stopInvocationCount >= 1)

        // 2. Verify reading announcement was made
        assertTrue(fakeSpeech.spokenUtterances.any { it.first == "Reading." })

        // 3. Verify chunks were spoken
        assertTrue(fakeSpeech.spokenUtterances.any { it.first.contains("quick brown fox") })

        // 4. Verify ReadDebugStore preserved pipeline state
        assertEquals(mockBitmap, ReadDebugStore.lastCapturedBitmap)
        assertEquals("The quick brown fox jumps over the lazy dog.", ReadDebugStore.lastOrganizedText)
        assertTrue(ReadDebugStore.lastSpeechChunks.isNotEmpty())
    }
}
