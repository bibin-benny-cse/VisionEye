package com.bibin.visioneye.people

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PeopleDecisionEngineTest {

    private lateinit var config: PeopleRecognitionConfig
    private lateinit var engine: DefaultPeopleDecisionEngine

    private val father = SavedPerson(
        id = "p-1",
        name = "Father",
        embeddings = listOf(FloatArray(192) { 0.5f })
    )

    private val mother = SavedPerson(
        id = "p-2",
        name = "Mother",
        embeddings = listOf(FloatArray(192) { -0.5f })
    )

    private val arun = SavedPerson(
        id = "p-3",
        name = "Arun",
        embeddings = listOf(FloatArray(192) { 0.2f })
    )

    @Before
    fun setup() {
        config = PeopleRecognitionConfig(
            similarityThreshold = 0.70f,
            temporalConfirmationCount = 2,
            recognitionCooldownMs = 2500L,
            absenceTimeoutMs = 1000L,
            speakUnknownPerson = false
        )
        engine = DefaultPeopleDecisionEngine(config)
    }

    private fun createFace(
        sizeRatio: Float = 0.3f,
        eulerY: Float = 0f,
        eulerX: Float = 0f,
        eulerZ: Float = 0f
    ): DetectedFace {
        return DetectedFace(
            boundingBox = RectF(0.3f, 0.2f, 0.7f, 0.8f),
            pixelRect = RectF(150f, 100f, 350f, 400f),
            eulerX = eulerX,
            eulerY = eulerY,
            eulerZ = eulerZ,
            sizeRatio = sizeRatio
        )
    }

    private fun createCandidate(
        person: SavedPerson?,
        similarity: Float,
        isKnown: Boolean = (person != null && similarity >= config.similarityThreshold),
        face: DetectedFace = createFace()
    ): FaceRecognitionCandidate {
        return FaceRecognitionCandidate(
            face = face,
            matchedPerson = if (isKnown) person else null,
            similarity = similarity,
            isKnown = isKnown
        )
    }

    // 1. No saved people
    @Test
    fun noSavedPeople_emptyOrUnknownCandidates_noKnownAlert() {
        val result = engine.process(emptyList(), timestampMs = 1000L)
        assertNull(result.spokenAlert)
        assertFalse(result.isConfirmed)
        assertTrue(result.recognizedNames.isEmpty())
        assertEquals(PeopleScanStatus.LOOKING_FOR_PEOPLE, result.status)
    }

    // 2. No face detected
    @Test
    fun noFaceDetected_noAlert() {
        val result = engine.process(emptyList(), timestampMs = 1000L)
        assertNull(result.spokenAlert)
        assertFalse(result.isConfirmed)
        assertEquals(0, result.recognizedNames.size)
        assertFalse(result.hasUnknownPerson)
        assertEquals(PeopleScanStatus.LOOKING_FOR_PEOPLE, result.status)
    }

    // 3. Face detected but poor quality (e.g. rejected upstream as unknown candidate)
    @Test
    fun faceDetected_poorQuality_treatedAsUnknown_noKnownAlert() {
        val poorQualityCandidate = createCandidate(
            person = null,
            similarity = 0f,
            isKnown = false,
            face = createFace(sizeRatio = 0.05f) // too small
        )
        val result = engine.process(listOf(poorQualityCandidate), timestampMs = 1000L)
        assertNull(result.spokenAlert)
        assertTrue(result.recognizedNames.isEmpty())
        assertTrue(result.hasUnknownPerson)
    }

    // 4. Unknown face (below threshold)
    @Test
    fun unknownFace_conservativeDefault_doesNotSpeakUnknown() {
        val unknown = createCandidate(person = null, similarity = 0.45f, isKnown = false)
        // Frame 1
        val frame1 = engine.process(listOf(unknown), timestampMs = 1000L)
        assertNull(frame1.spokenAlert)

        // Frame 2
        val frame2 = engine.process(listOf(unknown), timestampMs = 1200L)
        assertNull("Conservative policy should not vocalize unknown person by default", frame2.spokenAlert)
        assertTrue(frame2.hasUnknownPerson)
        assertTrue(frame2.recognizedNames.isEmpty())
        assertEquals(PeopleScanStatus.UNKNOWN_PERSON, frame2.status)
    }

    @Test
    fun unknownFace_speakUnknownEnabled_vocalizesUnknownPerson() {
        val engineWithUnknownSpeech = DefaultPeopleDecisionEngine(
            config.copy(speakUnknownPerson = true)
        )
        val unknown = createCandidate(person = null, similarity = 0.45f, isKnown = false)

        engineWithUnknownSpeech.process(listOf(unknown), timestampMs = 1000L)
        val frame2 = engineWithUnknownSpeech.process(listOf(unknown), timestampMs = 1200L)
        assertEquals("Unknown person in front of you.", frame2.spokenAlert)
    }

    // 5. Known face matching Father
    @Test
    fun knownFaceMatchingFather_confirmsAndSpeaks() {
        val fatherCandidate = createCandidate(person = father, similarity = 0.85f, isKnown = true)

        // Frame 1
        val frame1 = engine.process(listOf(fatherCandidate), timestampMs = 1000L)
        assertNull("Single frame must not trigger speech", frame1.spokenAlert)
        assertFalse(frame1.isConfirmed)

        // Frame 2 (consecutive match)
        val frame2 = engine.process(listOf(fatherCandidate), timestampMs = 1200L)
        assertEquals("Father is in front of you.", frame2.spokenAlert)
        assertTrue(frame2.isConfirmed)
        assertEquals(listOf("Father"), frame2.recognizedNames)
        assertEquals(PeopleScanStatus.PERSON_RECOGNIZED, frame2.status)
    }

    // 6. Known face matching Mother
    @Test
    fun knownFaceMatchingMother_confirmsAndSpeaks() {
        val motherCandidate = createCandidate(person = mother, similarity = 0.90f, isKnown = true)

        engine.process(listOf(motherCandidate), timestampMs = 1000L)
        val frame2 = engine.process(listOf(motherCandidate), timestampMs = 1200L)
        assertEquals("Mother is in front of you.", frame2.spokenAlert)
        assertTrue(frame2.isConfirmed)
        assertEquals(listOf("Mother"), frame2.recognizedNames)
    }

    // 7. Similar-looking but different person must remain unknown when below threshold
    @Test
    fun similarLookingPerson_belowThreshold_remainsUnknown() {
        // High similarity 0.65f, but threshold is 0.70f -> must remain unknown
        val lookalike = createCandidate(person = father, similarity = 0.65f, isKnown = false)

        engine.process(listOf(lookalike), timestampMs = 1000L)
        val frame2 = engine.process(listOf(lookalike), timestampMs = 1200L)

        assertNull("Must not speak Father when below threshold", frame2.spokenAlert)
        assertTrue(frame2.recognizedNames.isEmpty())
        assertTrue(frame2.hasUnknownPerson)
        assertEquals(PeopleScanStatus.UNKNOWN_PERSON, frame2.status)
    }

    // 8. One-frame match must NOT trigger speech
    @Test
    fun oneFrameMatch_doesNotTriggerSpeech() {
        val fatherCandidate = createCandidate(person = father, similarity = 0.88f)
        val result = engine.process(listOf(fatherCandidate), timestampMs = 1000L)
        assertNull(result.spokenAlert)
        assertFalse(result.isConfirmed)
    }

    // 9. Stable multi-frame match triggers speech
    @Test
    fun stableMultiFrameMatch_triggersSpeech() {
        val arunCandidate = createCandidate(person = arun, similarity = 0.82f)
        engine.process(listOf(arunCandidate), timestampMs = 1000L)
        val frame2 = engine.process(listOf(arunCandidate), timestampMs = 1200L)
        assertEquals("Arun is in front of you.", frame2.spokenAlert)
    }

    // 10. Repeated Father frames do not repeatedly speak
    @Test
    fun repeatedFatherFrames_deduplicatedAndDoesNotRepeatSpeech() {
        val fatherCandidate = createCandidate(person = father, similarity = 0.85f)

        engine.process(listOf(fatherCandidate), timestampMs = 1000L)
        val frame2 = engine.process(listOf(fatherCandidate), timestampMs = 1200L)
        assertEquals("Father is in front of you.", frame2.spokenAlert)

        // Continuous frames
        val frame3 = engine.process(listOf(fatherCandidate), timestampMs = 1400L)
        assertNull("Continuous frame 3 must be deduplicated", frame3.spokenAlert)

        val frame4 = engine.process(listOf(fatherCandidate), timestampMs = 1600L)
        assertNull("Continuous frame 4 must be deduplicated", frame4.spokenAlert)

        // After cooldown (2500ms later), same person continuously visible must still NOT repeat
        val frame5 = engine.process(listOf(fatherCandidate), timestampMs = 5000L)
        assertNull("Continuous visibility must not re-announce same identity", frame5.spokenAlert)
    }

    // 11. Father disappears and later returns -> can announce again after cooldown
    @Test
    fun fatherDisappearsAndLaterReturns_canAnnounceAgainAfterCooldown() {
        val fatherCandidate = createCandidate(person = father, similarity = 0.85f)

        // Confirm Father
        engine.process(listOf(fatherCandidate), timestampMs = 1000L)
        val frame2 = engine.process(listOf(fatherCandidate), timestampMs = 1200L)
        assertEquals("Father is in front of you.", frame2.spokenAlert)

        // Absence past timeout (1000ms timeout)
        engine.process(emptyList(), timestampMs = 2500L)

        // Father returns at 4000ms (cooldown 2500ms elapsed since 1200ms)
        val returnFrame1 = engine.process(listOf(fatherCandidate), timestampMs = 4000L)
        assertNull("Return frame 1 must require temporal confirmation", returnFrame1.spokenAlert)

        val returnFrame2 = engine.process(listOf(fatherCandidate), timestampMs = 4200L)
        assertEquals("Father is in front of you.", returnFrame2.spokenAlert)
    }

    // 12. Two recognized people (Father + Arun)
    @Test
    fun twoRecognizedPeople_fatherAndArun_speaksBothNames() {
        val candidates = listOf(
            createCandidate(person = father, similarity = 0.85f),
            createCandidate(person = arun, similarity = 0.80f)
        )

        engine.process(candidates, timestampMs = 1000L)
        val frame2 = engine.process(candidates, timestampMs = 1200L)

        // Order is sorted: Arun and Father
        assertEquals("Arun and Father are in front of you.", frame2.spokenAlert)
        assertTrue(frame2.isConfirmed)
        assertEquals(2, frame2.recognizedNames.size)
        assertEquals(PeopleScanStatus.MULTIPLE_PEOPLE_RECOGNIZED, frame2.status)
    }

    // 13. One recognized + one unknown (Father + Unknown)
    @Test
    fun oneRecognizedPlusOneUnknown_speaksOnlyKnownNameWithoutGuessing() {
        val candidates = listOf(
            createCandidate(person = father, similarity = 0.85f, isKnown = true),
            createCandidate(person = null, similarity = 0.35f, isKnown = false)
        )

        engine.process(candidates, timestampMs = 1000L)
        val frame2 = engine.process(candidates, timestampMs = 1200L)

        assertEquals("Father is in front of you.", frame2.spokenAlert)
        assertTrue(frame2.hasUnknownPerson)
        assertEquals(listOf("Father"), frame2.recognizedNames)
    }

    // Rapid fluctuation test
    @Test
    fun rapidIdentityFluctuation_doesNotTriggerPrematureSpeech() {
        val fatherCand = createCandidate(person = father, similarity = 0.85f)
        val arunCand = createCandidate(person = arun, similarity = 0.82f)
        val unknownCand = createCandidate(person = null, similarity = 0.3f, isKnown = false)

        // Frame 1: Father
        val f1 = engine.process(listOf(fatherCand), timestampMs = 1000L)
        assertNull(f1.spokenAlert)

        // Frame 2: Unknown (interrupts)
        val f2 = engine.process(listOf(unknownCand), timestampMs = 1200L)
        assertNull(f2.spokenAlert)

        // Frame 3: Arun (interrupts)
        val f3 = engine.process(listOf(arunCand), timestampMs = 1400L)
        assertNull(f3.spokenAlert)

        // Frame 4: Father (1 frame, not consecutive yet)
        val f4 = engine.process(listOf(fatherCand), timestampMs = 1600L)
        assertNull(f4.spokenAlert)

        // Frame 5: Father (now consecutive = 2)
        val f5 = engine.process(listOf(fatherCand), timestampMs = 1800L)
        assertEquals("Father is in front of you.", f5.spokenAlert)
    }

    @Test
    fun reset_clearsAllConfirmationAndSpeechStateImmediately() {
        val fatherCand = createCandidate(person = father, similarity = 0.85f)
        engine.process(listOf(fatherCand), timestampMs = 1000L)
        engine.process(listOf(fatherCand), timestampMs = 1200L)

        engine.reset()

        val afterReset = engine.process(emptyList(), timestampMs = 1300L)
        assertFalse(afterReset.isConfirmed)
        assertTrue(afterReset.recognizedNames.isEmpty())
        assertNull(afterReset.spokenAlert)
    }
}
