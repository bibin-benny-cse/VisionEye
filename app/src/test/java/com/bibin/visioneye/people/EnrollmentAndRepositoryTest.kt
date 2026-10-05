package com.bibin.visioneye.people

import android.graphics.Bitmap
import android.graphics.RectF
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnrollmentAndRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: LocalFilePeopleRepository
    private lateinit var fakeSpeech: FakeSpeechController
    private lateinit var fakeFaceDetector: FakeFaceDetector
    private lateinit var fakeEmbeddingModel: FakeEmbeddingModel
    private lateinit var coordinator: EnrollmentCoordinator
    private val config = PeopleRecognitionConfig(requiredEnrollmentSamples = 3, sampleSpacingMs = 0L)

    private class FakeFaceDetector : FaceDetector {
        var detectedFaces: List<DetectedFace> = emptyList()
        private var _lastError: String? = null
        override val lastError: String? get() = _lastError

        fun setFakeError(err: String?) {
            _lastError = err
        }

        override fun detectFaces(bitmap: Bitmap): List<DetectedFace> = detectedFaces

        override fun close() {}
    }

    private class FakeEmbeddingModel : FaceEmbeddingModel {
        var embeddingToReturn: FloatArray? = FloatArray(192) { 0.5f }

        override fun extractEmbedding(faceBitmap: Bitmap): FloatArray? = embeddingToReturn

        override fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float = 0.85f

        override fun findBestMatch(
            queryEmbedding: FloatArray,
            savedPeople: List<SavedPerson>,
            threshold: Float
        ): Pair<SavedPerson?, Float> {
            val best = savedPeople.firstOrNull()
            return Pair(best, if (best != null) 0.85f else 0f)
        }

        override fun close() {}
    }

    private class FakeSpeechController : SpeechController {
        val spokenUtterances = mutableListOf<String>()

        override fun speak(utterance: String, priority: SpeechPriority) {
            spokenUtterances.add(utterance)
        }

        override fun stop() {}
    }

    @Before
    fun setup() {
        repository = LocalFilePeopleRepository(tempFolder.newFolder("people_test"))
        fakeSpeech = FakeSpeechController()
        fakeFaceDetector = FakeFaceDetector()
        fakeEmbeddingModel = FakeEmbeddingModel()
        coordinator = EnrollmentCoordinator(
            faceDetector = fakeFaceDetector,
            embeddingModel = fakeEmbeddingModel,
            repository = repository,
            speechController = fakeSpeech,
            config = config
        )
        coordinator.testEmbeddingProvider = { FloatArray(192) { 0.5f } }
    }

    private fun createGoodFace(): DetectedFace {
        return DetectedFace(
            boundingBox = RectF(0.2f, 0.2f, 0.8f, 0.8f),
            pixelRect = RectF(100f, 100f, 400f, 400f),
            eulerX = 0f,
            eulerY = 0f,
            eulerZ = 0f,
            sizeRatio = 0.35f
        )
    }

    // 1. Initial State & Diagnostics
    @Test
    fun initialState_isIdleWithCleanDiagnostics() {
        val state = coordinator.enrollmentState.value
        assertEquals(0, state.samplesCaptured)
        assertFalse(state.isComplete)
        assertFalse(state.isCameraScanning)
        assertEquals(0L, state.diagnostics.framesReceived)
        assertEquals(0, state.diagnostics.acceptedSamples)
    }

    // 2. Start Enrollment initializes diagnostics
    @Test
    fun startEnrollment_initializesStateAndDiagnostics() {
        coordinator.startEnrollment("Father")
        val state = coordinator.enrollmentState.value

        assertEquals("Father", state.personName)
        assertEquals(0, state.samplesCaptured)
        assertEquals(3, state.targetSamples)
        assertTrue(state.isCameraScanning)
        assertFalse(state.isComplete)
        assertEquals(0L, state.diagnostics.framesReceived)
        assertEquals(0, state.diagnostics.acceptedSamples)
        assertEquals("WAITING FOR FRAMES", state.diagnostics.lastRejectionReason)
    }

    // 3. Enrollment with no face -> rejected with diagnostics
    @Test
    fun enrollmentWithNoFace_rejectedWithGuidanceAndDiagnostics() {
        coordinator.startEnrollment("Father")
        coordinator.processDetectedFaces(emptyList(), totalFrames = 1L)

        val state = coordinator.enrollmentState.value
        assertEquals(0, state.samplesCaptured)
        assertFalse(state.isComplete)
        assertTrue(state.guidanceMessage.contains("No face detected", ignoreCase = true))
        assertEquals("NO FACE", state.diagnostics.lastFaceState)
        assertEquals("NO_FACE", state.diagnostics.lastRejectionReason)
        assertEquals(1L, state.diagnostics.framesReceived)
        assertTrue(repository.getSavedPeople().isEmpty())
    }

    // 4. Enrollment with multiple faces -> rejected with diagnostics
    @Test
    fun enrollmentWithMultipleFaces_rejectedWithGuidanceAndDiagnostics() {
        coordinator.startEnrollment("Father")
        coordinator.processDetectedFaces(listOf(createGoodFace(), createGoodFace()), totalFrames = 2L)

        val state = coordinator.enrollmentState.value
        assertEquals(0, state.samplesCaptured)
        assertFalse(state.isComplete)
        assertTrue(state.guidanceMessage.contains("Multiple faces detected", ignoreCase = true))
        assertEquals("MULTIPLE FACES (2)", state.diagnostics.lastFaceState)
        assertEquals("MULTIPLE_FACES", state.diagnostics.lastRejectionReason)
        assertTrue(repository.getSavedPeople().isEmpty())
    }

    // 5. Enrollment with poor-quality face: too small -> rejected with diagnostics
    @Test
    fun enrollmentWithPoorQualityFace_tooSmall_rejectedWithDiagnostics() {
        coordinator.startEnrollment("Father")
        val smallFace = DetectedFace(
            boundingBox = RectF(0.4f, 0.4f, 0.5f, 0.5f),
            pixelRect = RectF(200f, 200f, 250f, 250f),
            sizeRatio = 0.05f
        )
        coordinator.processDetectedFaces(listOf(smallFace), totalFrames = 3L)

        val state = coordinator.enrollmentState.value
        assertEquals(0, state.samplesCaptured)
        assertFalse(state.isComplete)
        assertTrue(state.guidanceMessage.contains("Move closer", ignoreCase = true))
        assertTrue(state.diagnostics.lastRejectionReason.contains("FACE_TOO_SMALL"))
    }

    // 6. Enrollment with poor-quality face: bad yaw -> rejected with diagnostics
    @Test
    fun enrollmentWithPoorQualityFace_badYawAngle_rejectedWithDiagnostics() {
        coordinator.startEnrollment("Father")
        // Yaw angle 45 deg > max 35 deg
        val angledFace = createGoodFace().copy(eulerY = 45f)
        coordinator.processDetectedFaces(listOf(angledFace), totalFrames = 4L)

        val state = coordinator.enrollmentState.value
        assertEquals(0, state.samplesCaptured)
        assertFalse(state.isComplete)
        assertTrue(state.guidanceMessage.contains("directly toward", ignoreCase = true))
        assertTrue(state.diagnostics.lastRejectionReason.contains("BAD_YAW"))
    }

    // 7. Enrollment with poor-quality face: bad pitch -> rejected with diagnostics
    @Test
    fun enrollmentWithPoorQualityFace_badPitchAngle_rejectedWithDiagnostics() {
        coordinator.startEnrollment("Father")
        // Pitch angle 45 deg > max 35 deg
        val pitchFace = createGoodFace().copy(eulerX = 45f)
        coordinator.processDetectedFaces(listOf(pitchFace), totalFrames = 5L)

        val state = coordinator.enrollmentState.value
        assertEquals(0, state.samplesCaptured)
        assertFalse(state.isComplete)
        assertTrue(state.guidanceMessage.contains("level with eyes", ignoreCase = true))
        assertTrue(state.diagnostics.lastRejectionReason.contains("BAD_PITCH"))
    }

    // 8. Face detector error reported directly to diagnostics
    @Test
    fun detectorError_reportsInDiagnosticsAndHaltsEnrollment() {
        coordinator.startEnrollment("Father")
        fakeFaceDetector.setFakeError("Waiting for the face detection module to be downloaded.")

        coordinator.processDetectedFaces(
            faces = emptyList(),
            totalFrames = 6L,
            detectorError = fakeFaceDetector.lastError
        )

        val state = coordinator.enrollmentState.value
        assertEquals(0, state.samplesCaptured)
        assertEquals("DETECTOR ERROR", state.diagnostics.lastFaceState)
        assertTrue(state.diagnostics.lastRejectionReason.contains("Waiting for the face detection module"))
        assertEquals("Waiting for the face detection module to be downloaded.", state.diagnostics.lastDetectorError)
    }

    // 9. Single valid face advances from 0/5 to 1/5
    @Test
    fun firstSample_acceptedEasily_increments0To1() {
        coordinator.startEnrollment("Father")
        val goodFaceList = listOf(createGoodFace())

        coordinator.processDetectedFaces(goodFaceList, totalFrames = 1L)

        val state = coordinator.enrollmentState.value
        assertEquals(1, state.samplesCaptured)
        assertEquals(1, state.diagnostics.acceptedSamples)
        assertFalse(state.isComplete)
        assertTrue(state.guidanceMessage.contains("Sample 1 of 3", ignoreCase = true))
        assertTrue(state.diagnostics.lastRejectionReason.contains("ACCEPTED SAMPLE 1/3"))
    }

    // 10. Multi-sample enrollment completes and stores person
    @Test
    fun successfulEnrollment_capturesSamples_andStoresPerson() {
        coordinator.startEnrollment("Father")
        val goodFaceList = listOf(createGoodFace())

        // Frame 1
        coordinator.processDetectedFaces(goodFaceList, totalFrames = 1L)
        assertEquals(1, coordinator.enrollmentState.value.samplesCaptured)
        assertFalse(coordinator.enrollmentState.value.isComplete)

        // Frame 2
        coordinator.processDetectedFaces(goodFaceList, totalFrames = 2L)
        assertEquals(2, coordinator.enrollmentState.value.samplesCaptured)
        assertFalse(coordinator.enrollmentState.value.isComplete)

        // Frame 3 (reaches required target = 3)
        coordinator.processDetectedFaces(goodFaceList, totalFrames = 3L)
        val state = coordinator.enrollmentState.value
        assertEquals(3, state.samplesCaptured)
        assertTrue(state.isComplete)
        assertFalse(state.isCameraScanning)

        // Verify stored in repository
        val saved = repository.getSavedPeople()
        assertEquals(1, saved.size)
        assertEquals("Father", saved.first().name)
        assertEquals(3, saved.first().embeddings.size)

        // Verify spoken confirmation "Father saved."
        assertTrue(fakeSpeech.spokenUtterances.any { it.contains("Father saved", ignoreCase = true) })
    }

    // 11. Sample spacing debounces rapid bursts of the exact same instant
    @Test
    fun sampleSpacing_debouncesRapidSameFrameBurst() {
        val spacedCoordinator = EnrollmentCoordinator(
            faceDetector = fakeFaceDetector,
            embeddingModel = fakeEmbeddingModel,
            repository = repository,
            speechController = fakeSpeech,
            config = PeopleRecognitionConfig(requiredEnrollmentSamples = 3, sampleSpacingMs = 400L)
        )
        spacedCoordinator.testEmbeddingProvider = { FloatArray(192) { 0.5f } }
        spacedCoordinator.startEnrollment("Father")
        val goodFace = listOf(createGoodFace())

        // Frame 1 at t = 1000ms -> accepted (1/3)
        spacedCoordinator.processDetectedFaces(goodFace, timestampMs = 1000L)
        assertEquals(1, spacedCoordinator.enrollmentState.value.samplesCaptured)

        // Frame 2 at t = 1100ms (too soon, within 400ms) -> debounced (still 1/3)
        spacedCoordinator.processDetectedFaces(goodFace, timestampMs = 1100L)
        assertEquals(1, spacedCoordinator.enrollmentState.value.samplesCaptured)

        // Frame 3 at t = 1450ms (>= 400ms elapsed) -> accepted (2/3)
        spacedCoordinator.processDetectedFaces(goodFace, timestampMs = 1450L)
        assertEquals(2, spacedCoordinator.enrollmentState.value.samplesCaptured)
    }

    // 12. Invalid embedding (null or non-finite) is rejected without counting
    @Test
    fun invalidEmbedding_isRejectedWithoutIncrementingSampleCount() {
        coordinator.startEnrollment("Father")
        coordinator.testEmbeddingProvider = { null } // Simulate failed embedding

        coordinator.processDetectedFaces(listOf(createGoodFace()), totalFrames = 1L)
        assertEquals(0, coordinator.enrollmentState.value.samplesCaptured)
        assertTrue(coordinator.enrollmentState.value.diagnostics.lastRejectionReason.contains("EMBEDDING_FAILED"))

        // Simulate NaN embedding
        coordinator.testEmbeddingProvider = { FloatArray(192) { Float.NaN } }
        coordinator.processDetectedFaces(listOf(createGoodFace()), totalFrames = 2L)
        assertEquals(0, coordinator.enrollmentState.value.samplesCaptured)
        assertTrue(coordinator.enrollmentState.value.diagnostics.lastRejectionReason.contains("EMBEDDING_FAILED"))
    }

    // 13. Delete saved person removes from storage
    @Test
    fun deleteSavedPerson_removesFromStorage() {
        val person = SavedPerson(
            id = "test-uuid-1",
            name = "Father",
            embeddings = listOf(FloatArray(192) { 0.1f })
        )
        repository.savePerson(person)
        assertEquals(1, repository.getSavedPeople().size)

        val deleted = repository.deletePerson(person.id)
        assertTrue(deleted)
        assertTrue(repository.getSavedPeople().isEmpty())
        assertNull(repository.getPersonById(person.id))
    }

    // 14. Repository persistence across instances
    @Test
    fun repositoryPersistence_retainsAcrossInstances() {
        val dir = tempFolder.newFolder("persistence_test")
        val repo1 = LocalFilePeopleRepository(dir)
        val person = SavedPerson(
            id = "id-123",
            name = "Mother",
            embeddings = listOf(FloatArray(192) { 0.7f }, FloatArray(192) { 0.8f })
        )
        repo1.savePerson(person)

        // Recreate repository pointing to same directory
        val repo2 = LocalFilePeopleRepository(dir)
        val loaded = repo2.getSavedPeople()
        assertEquals(1, loaded.size)
        assertEquals("Mother", loaded.first().name)
        assertEquals(2, loaded.first().embeddings.size)
    }
}
