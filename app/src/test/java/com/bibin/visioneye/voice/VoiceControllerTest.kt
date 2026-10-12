package com.bibin.visioneye.voice

import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake SpeechController recording spoken utterances and priorities for unit testing.
 */
class FakeVoiceSpeechController : SpeechController {
    val spokenMessages = mutableListOf<Pair<String, SpeechPriority>>()
    var stopCount = 0

    override fun speak(utterance: String, priority: SpeechPriority) {
        spokenMessages.add(Pair(utterance, priority))
    }

    override fun stop() {
        stopCount++
    }
}

/**
 * Unit tests for [DefaultVoiceController] verifying single-utterance state machine,
 * command execution, dictation transitions, error recovery, and contextual help.
 */
class VoiceControllerTest {

    private lateinit var mockEngine: MockSpeechRecognitionEngine
    private lateinit var fakeSpeech: FakeVoiceSpeechController
    private lateinit var registry: VoiceCommandRegistry
    private lateinit var controller: DefaultVoiceController

    @Before
    fun setUp() {
        mockEngine = MockSpeechRecognitionEngine()
        fakeSpeech = FakeVoiceSpeechController()
        registry = VoiceCommandRegistry()

        controller = DefaultVoiceController(
            speechController = fakeSpeech,
            engine = mockEngine,
            registry = registry,
            backgroundDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun startListening_transitionsStateAndAnnouncesListening() = runBlocking {
        controller.startListening()

        assertTrue(controller.state.value is VoiceInputState.Listening)
        assertTrue(fakeSpeech.spokenMessages.any { it.first.contains("Listening") })
    }

    @Test
    fun recognizedCommand_executesAndSpeaksResult() = runBlocking {
        var executed = false
        registry.register(
            VoiceAction(
                id = "test_cmd",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "NAVIGATE",
                canonicalCommand = "Navigate",
                onExecute = {
                    executed = true
                    VoiceActionResult.Success("Navigate mode activated.")
                }
            )
        )

        controller.startListening()
        mockEngine.simulateResults(listOf("Navigate"))

        assertTrue(executed)
        assertTrue(fakeSpeech.spokenMessages.any { it.first.contains("Navigate mode activated") })
        assertTrue(controller.state.value is VoiceInputState.SpeakingResult)
        controller.cancel()
        assertTrue(controller.state.value is VoiceInputState.Idle)
    }

    @Test
    fun dictationFlow_transitionsToDictatingAndFeedsConsumer() = runBlocking {
        var savedDestination = ""
        registry.register(
            VoiceAction(
                id = "nav_dest",
                screenContext = VoiceScreenContext.NAVIGATION_GPS,
                visibleLabel = "Enter destination",
                canonicalCommand = "Enter destination",
                isDictation = true,
                onExecute = {
                    VoiceActionResult.StartDictation(
                        prompt = "Destination field ready. Say the place name.",
                        fieldType = DictationFieldType.NAVIGATION_DESTINATION
                    )
                }
            )
        )
        controller.setScreenContext(VoiceScreenContext.NAVIGATION_GPS)
        controller.setDictationConsumer { fieldType, text ->
            if (fieldType == DictationFieldType.NAVIGATION_DESTINATION) {
                savedDestination = text
                VoiceActionResult.Success("Destination set to $text.")
            } else {
                VoiceActionResult.Error("Unknown field")
            }
        }

        // 1. Initial command: "Enter destination"
        controller.startListening()
        mockEngine.simulateResults(listOf("Enter destination"))

        // Should transition into Dictating state
        assertTrue(controller.state.value is VoiceInputState.Dictating)
        assertTrue(fakeSpeech.spokenMessages.any { it.first.contains("Destination field ready") })

        // 2. User dictates place name
        mockEngine.simulateResults(listOf("Central Park"))

        assertEquals("Central Park", savedDestination)
        assertTrue(fakeSpeech.spokenMessages.any { it.first.contains("Destination set to Central Park") })
        assertTrue(controller.state.value is VoiceInputState.SpeakingResult)
        controller.cancel()
        assertTrue(controller.state.value is VoiceInputState.Idle)
    }

    @Test
    fun helpQuery_speaksDynamicAvailableCommands() = runBlocking {
        registry.register(
            VoiceAction(
                id = "cmd1",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "NAVIGATE",
                canonicalCommand = "Navigate",
                onExecute = { VoiceActionResult.Success("OK") }
            )
        )
        registry.register(
            VoiceAction(
                id = "cmd2",
                screenContext = VoiceScreenContext.MAIN_DASHBOARD,
                visibleLabel = "READ TEXT",
                canonicalCommand = "Read text",
                onExecute = { VoiceActionResult.Success("OK") }
            )
        )

        controller.startListening()
        mockEngine.simulateResults(listOf("What can I do here?"))

        assertTrue(fakeSpeech.spokenMessages.any { it.first.contains("On Main Dashboard, you can say") && it.first.contains("Navigate") && it.first.contains("Read text") })
        assertTrue(controller.state.value is VoiceInputState.SpeakingResult)
        controller.cancel()
        assertTrue(controller.state.value is VoiceInputState.Idle)
    }

    @Test
    fun recognitionError_recoversToIdleAndSpeaksConstructiveMessage() = runBlocking {
        controller.startListening()
        mockEngine.simulateError(7, "No speech input")

        assertTrue(controller.state.value is VoiceInputState.Error)
        assertTrue(fakeSpeech.spokenMessages.any { it.first.contains("No speech detected") })
        controller.cancel()
        assertTrue(controller.state.value is VoiceInputState.Idle)
    }

    @Test
    fun cancel_resetsListeningEngine() = runBlocking {
        controller.startListening()
        assertTrue(controller.isListening())
        controller.cancel()

        assertFalse(controller.isListening())
        assertTrue(controller.state.value is VoiceInputState.Idle)
    }
}
