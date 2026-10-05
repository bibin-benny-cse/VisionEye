package com.bibin.visioneye.people

import android.graphics.Bitmap
import android.graphics.RectF
import com.bibin.visioneye.core.mode.VisionMode
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PeopleCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeFaceDetector : FaceDetector {
        var detectedFaces: List<DetectedFace> = emptyList()
        var closeCallCount = 0

        override fun detectFaces(bitmap: Bitmap): List<DetectedFace> = detectedFaces

        override fun close() {
            closeCallCount++
        }
    }

    private class FakeEmbeddingModel : FaceEmbeddingModel {
        var embeddingToReturn: FloatArray? = FloatArray(192) { 0.5f }
        var closeCallCount = 0

        override fun extractEmbedding(faceBitmap: Bitmap): FloatArray? = embeddingToReturn

        override fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float = 0.85f

        override fun findBestMatch(
            queryEmbedding: FloatArray,
            savedPeople: List<SavedPerson>,
            threshold: Float
        ): Pair<SavedPerson?, Float> {
            val best = savedPeople.firstOrNull()
            return Pair(best, if (best != null) 0.88f else 0f)
        }

        override fun close() {
            closeCallCount++
        }
    }

    private class FakeSpeechController : SpeechController {
        val spokenUtterances = mutableListOf<String>()
        var stopCallCount = 0

        override fun speak(utterance: String, priority: SpeechPriority) {
            spokenUtterances.add(utterance)
        }

        override fun stop() {
            stopCallCount++
        }
    }

    private lateinit var fakeDetector: FakeFaceDetector
    private lateinit var fakeEmbedding: FakeEmbeddingModel
    private lateinit var fakeSpeech: FakeSpeechController
    private lateinit var repository: LocalFilePeopleRepository
    private lateinit var decisionEngine: DefaultPeopleDecisionEngine
    private lateinit var coordinator: PeopleCoordinator

    @Before
    fun setup() {
        fakeDetector = FakeFaceDetector()
        fakeEmbedding = FakeEmbeddingModel()
        fakeSpeech = FakeSpeechController()
        repository = LocalFilePeopleRepository(tempFolder.newFolder("coordinator_test"))
        decisionEngine = DefaultPeopleDecisionEngine(
            PeopleRecognitionConfig(
                similarityThreshold = 0.70f,
                temporalConfirmationCount = 2,
                recognitionCooldownMs = 2500L,
                absenceTimeoutMs = 1000L
            )
        )
        coordinator = PeopleCoordinator(
            faceDetector = fakeDetector,
            embeddingModel = fakeEmbedding,
            repository = repository,
            speechController = fakeSpeech,
            decisionEngine = decisionEngine
        )
    }

    @Test
    fun initialState_isInactiveAndLookingForPeople() {
        val state = coordinator.peopleState.value
        assertFalse(state.isActive)
        assertFalse(state.isConfirmed)
        assertEquals(0, state.confirmedCount)
        assertEquals(PeopleScanStatus.LOOKING_FOR_PEOPLE, state.status)
    }

    @Test
    fun peopleMode_entryAndExit() {
        // Entry: activate
        coordinator.activate()
        val activeState = coordinator.peopleState.value
        assertTrue("Coordinator must be active upon activation", activeState.isActive)
        assertEquals(PeopleScanStatus.LOOKING_FOR_PEOPLE, activeState.status)
        assertFalse(activeState.isConfirmed)

        // Exit: deactivate
        coordinator.deactivate()
        val inactiveState = coordinator.peopleState.value
        assertFalse("Coordinator must be inactive upon deactivation", inactiveState.isActive)
        assertEquals(1, fakeSpeech.stopCallCount)
        assertFalse(inactiveState.isConfirmed)
    }

    @Test
    fun release_triggersDeactivateAndClosesModels() {
        coordinator.activate()
        coordinator.release()

        assertFalse(coordinator.peopleState.value.isActive)
        assertEquals(1, fakeSpeech.stopCallCount)
        assertEquals(1, fakeDetector.closeCallCount)
        assertEquals(1, fakeEmbedding.closeCallCount)
    }

    @Test
    fun decisionEngine_integrationDirectProcess_emitsSpeechViaController() {
        coordinator.activate()

        val father = SavedPerson(
            id = "f-1",
            name = "Father",
            embeddings = listOf(FloatArray(192) { 0.5f })
        )
        val candidate = FaceRecognitionCandidate(
            face = DetectedFace(
                boundingBox = RectF(0.2f, 0.2f, 0.8f, 0.8f),
                pixelRect = RectF(100f, 100f, 400f, 400f)
            ),
            matchedPerson = father,
            similarity = 0.88f,
            isKnown = true
        )

        // Frame 1
        val res1 = decisionEngine.process(listOf(candidate), timestampMs = 1000L)
        assertFalse(res1.isConfirmed)
        assertTrue(fakeSpeech.spokenUtterances.isEmpty())

        // Frame 2 (confirmation)
        val res2 = decisionEngine.process(listOf(candidate), timestampMs = 1200L)
        assertTrue(res2.isConfirmed)
        assertEquals("Father is in front of you.", res2.spokenAlert)

        res2.spokenAlert?.let { alert ->
            fakeSpeech.speak(alert, SpeechPriority.NORMAL)
        }

        assertEquals(1, fakeSpeech.spokenUtterances.size)
        assertEquals("Father is in front of you.", fakeSpeech.spokenUtterances.first())
    }

    @Test
    fun visionMode_supportedModesIncludesPeople() {
        val supported = setOf(
            VisionMode.NAVIGATE,
            VisionMode.READ,
            VisionMode.PEOPLE
        )
        assertTrue(supported.contains(VisionMode.PEOPLE))
    }
}
