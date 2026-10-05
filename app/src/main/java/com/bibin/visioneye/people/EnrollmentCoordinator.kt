package com.bibin.visioneye.people

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.bibin.visioneye.camera.FrameAnalyzer
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Real-time diagnostic metrics during face enrollment for live debugging.
 */
data class EnrollmentDiagnostics(
    val isCameraConnected: Boolean = false,
    val framesReceived: Long = 0L,
    val faceDetectionsCount: Long = 0L,
    val lastFaceState: String = "NO FRAMES YET",
    val lastRejectionReason: String = "WAITING FOR FRAMES",
    val acceptedSamples: Int = 0,
    val lastDetectorError: String? = null,
    val lastFaceMetrics: String? = null
)

/**
 * Observable UI state during the "SAVE PERSON" face enrollment flow.
 */
data class EnrollmentState(
    val personName: String = "",
    val samplesCaptured: Int = 0,
    val targetSamples: Int = 5,
    val guidanceMessage: String = "Enter person name and start camera to enroll.",
    val isComplete: Boolean = false,
    val isCameraScanning: Boolean = false,
    val lastError: String? = null,
    val diagnostics: EnrollmentDiagnostics = EnrollmentDiagnostics()
)

/**
 * Handles guided face capture, quality filtering, and embedding storage for person enrollment.
 *
 * Enforces strict sample rejection:
 * - Reject when no face detected.
 * - Reject when multiple faces detected.
 * - Reject when face is too small (move closer).
 * - Reject when head yaw/pitch/roll angle is too steep.
 * - Spoken and on-screen guidance directs the visually impaired user throughout.
 */
class EnrollmentCoordinator(
    private val faceDetector: FaceDetector,
    private val embeddingModel: FaceEmbeddingModel,
    private val repository: PeopleRepository,
    private val speechController: SpeechController? = null,
    val config: PeopleRecognitionConfig = PeopleRecognitionConfig()
) {

    private val _enrollmentState = MutableStateFlow(EnrollmentState())
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState.asStateFlow()

    private val collectedEmbeddings = mutableListOf<FloatArray>()
    private var lastGuidanceSpokenTime = 0L
    private var lastSpokenGuidance = ""
    private val lock = Any()
    private var isScanning = false

    val framesReceivedCount = AtomicLong(0L)
    val faceDetectionsCount = AtomicLong(0L)
    private var lastSampleCapturedTime = 0L

    var testEmbeddingProvider: (() -> FloatArray?)? = null

    /**
     * Prepares for enrollment with the given [personName].
     */
    fun startEnrollment(personName: String) {
        synchronized(lock) {
            collectedEmbeddings.clear()
            framesReceivedCount.set(0L)
            faceDetectionsCount.set(0L)
            lastSampleCapturedTime = 0L
            isScanning = true
            lastGuidanceSpokenTime = 0L
            lastSpokenGuidance = ""
            _enrollmentState.value = EnrollmentState(
                personName = personName.trim(),
                samplesCaptured = 0,
                targetSamples = config.requiredEnrollmentSamples,
                guidanceMessage = "Position the camera in front of ${personName.trim()}'s face.",
                isComplete = false,
                isCameraScanning = true,
                diagnostics = EnrollmentDiagnostics(
                    isCameraConnected = false,
                    framesReceived = 0L,
                    faceDetectionsCount = 0L,
                    lastFaceState = "STARTING CAMERA",
                    lastRejectionReason = "WAITING FOR FRAMES",
                    acceptedSamples = 0
                )
            )
            speakGuidance("Starting enrollment for ${personName.trim()}. Point camera at the face.", force = true)
        }
    }

    /**
     * Evaluates detected faces during enrollment.
     */
    fun processDetectedFaces(
        faces: List<DetectedFace>,
        bitmap: Bitmap? = null,
        totalFrames: Long = framesReceivedCount.get(),
        detectorError: String? = faceDetector.lastError,
        timestampMs: Long? = null
    ) {
        synchronized(lock) {
        if (!isScanning || _enrollmentState.value.isComplete) return

        if (detectorError != null) {
            _enrollmentState.value = _enrollmentState.value.copy(
                guidanceMessage = "Face detector error: $detectorError",
                diagnostics = EnrollmentDiagnostics(
                    isCameraConnected = true,
                    framesReceived = totalFrames,
                    faceDetectionsCount = faceDetectionsCount.get(),
                    lastFaceState = "DETECTOR ERROR",
                    lastRejectionReason = "DETECTOR_ERROR: $detectorError",
                    acceptedSamples = collectedEmbeddings.size,
                    lastDetectorError = detectorError
                )
            )
            return
        }

        when {
            faces.isEmpty() -> {
                _enrollmentState.value = _enrollmentState.value.copy(
                    guidanceMessage = "No face detected. Position camera facing the person.",
                    diagnostics = EnrollmentDiagnostics(
                        isCameraConnected = true,
                        framesReceived = totalFrames,
                        faceDetectionsCount = faceDetectionsCount.get(),
                        lastFaceState = "NO FACE",
                        lastRejectionReason = "NO_FACE",
                        acceptedSamples = collectedEmbeddings.size
                    )
                )
                speakGuidance("No face detected. Position camera facing the person.", force = false)
                return
            }
            faces.size > 1 -> {
                faceDetectionsCount.incrementAndGet()
                _enrollmentState.value = _enrollmentState.value.copy(
                    guidanceMessage = "Multiple faces detected. Only one person should be in view.",
                    diagnostics = EnrollmentDiagnostics(
                        isCameraConnected = true,
                        framesReceived = totalFrames,
                        faceDetectionsCount = faceDetectionsCount.get(),
                        lastFaceState = "MULTIPLE FACES (${faces.size})",
                        lastRejectionReason = "MULTIPLE_FACES",
                        acceptedSamples = collectedEmbeddings.size
                    )
                )
                speakGuidance("Multiple faces detected. Only one person should be in view.", force = false)
                return
            }
        }

        faceDetectionsCount.incrementAndGet()
        val face = faces.first()
        val metricsStr = "size=${String.format("%.2f", face.sizeRatio)}, yaw=${face.eulerY.toInt()}°, pitch=${face.eulerX.toInt()}°, roll=${face.eulerZ.toInt()}°"
        val quality = face.evaluateQuality(config)

        when (quality) {
            FaceQualityStatus.TOO_SMALL -> {
                val reason = "FACE_TOO_SMALL (ratio=${String.format("%.2f", face.sizeRatio)} < ${config.minFaceSizeRatio})"
                updateRejection("Move closer to the face.", reason, totalFrames, metricsStr)
                return
            }
            FaceQualityStatus.BAD_ANGLE_YAW -> {
                val reason = "BAD_YAW (yaw=${face.eulerY.toInt()}° > ${config.maxEulerY.toInt()}°)"
                updateRejection("Turn face directly toward the camera.", reason, totalFrames, metricsStr)
                return
            }
            FaceQualityStatus.BAD_ANGLE_PITCH -> {
                val reason = "BAD_PITCH (pitch=${face.eulerX.toInt()}° > ${config.maxEulerX.toInt()}°)"
                updateRejection("Hold camera level with eyes.", reason, totalFrames, metricsStr)
                return
            }
            FaceQualityStatus.BAD_ANGLE_ROLL -> {
                val reason = "BAD_ROLL (roll=${face.eulerZ.toInt()}° > ${config.maxEulerZ.toInt()}°)"
                updateRejection("Hold camera straight.", reason, totalFrames, metricsStr)
                return
            }
            FaceQualityStatus.OUT_OF_BOUNDS -> {
                val reason = "FACE_OUT_OF_BOUNDS"
                updateRejection("Center face in front of the camera.", reason, totalFrames, metricsStr)
                return
            }
            FaceQualityStatus.GOOD -> {
                val now = timestampMs ?: System.currentTimeMillis()
                if (now - lastSampleCapturedTime < config.sampleSpacingMs) {
                    // Maintain spacing between distinct sample frames
                    return
                }

                val embedding = testEmbeddingProvider?.invoke() ?: run {
                    if (bitmap == null) return@run null
                    val cropped = PeopleCoordinator.cropFace(bitmap, face.pixelRect) ?: run {
                        updateRejection("Hold camera steady.", "CROP_FAILED", totalFrames, metricsStr)
                        return
                    }
                    val emb = embeddingModel.extractEmbedding(cropped)
                    cropped.recycle()
                    emb
                }

                val isValidEmbedding = embedding != null &&
                    embedding.size == MobileFaceNetEmbeddingModel.EMBEDDING_SIZE &&
                    embedding.all { it.isFinite() && !it.isNaN() } &&
                    embedding.any { abs(it) > 1e-6f }

                if (!isValidEmbedding) {
                    val reason = if (embedding == null) "EMBEDDING_FAILED (null)" else "EMBEDDING_FAILED (non-finite or zero)"
                    updateRejection("Failed to process face sample. Hold steady.", reason, totalFrames, metricsStr)
                    return
                }

                lastSampleCapturedTime = now
                collectedEmbeddings.add(embedding!!)
                val count = collectedEmbeddings.size
                val target = config.requiredEnrollmentSamples

                if (count < target) {
                    val msg = "Sample $count of $target captured. Hold steady..."
                    _enrollmentState.value = _enrollmentState.value.copy(
                        samplesCaptured = count,
                        guidanceMessage = msg,
                        diagnostics = EnrollmentDiagnostics(
                            isCameraConnected = true,
                            framesReceived = totalFrames,
                            faceDetectionsCount = faceDetectionsCount.get(),
                            lastFaceState = "ONE FACE",
                            lastRejectionReason = "NONE (ACCEPTED SAMPLE $count/$target)",
                            acceptedSamples = count,
                            lastFaceMetrics = metricsStr,
                            lastDetectorError = faceDetector.lastError
                        )
                    )
                    speakGuidance(msg, force = true)
                } else {
                    // All required samples captured: save person
                    isScanning = false
                    val newPerson = SavedPerson(
                        name = _enrollmentState.value.personName,
                        embeddings = collectedEmbeddings.toList()
                    )
                    repository.savePerson(newPerson)

                    val completeMsg = "${newPerson.name} saved successfully!"
                    _enrollmentState.value = _enrollmentState.value.copy(
                        samplesCaptured = count,
                        guidanceMessage = completeMsg,
                        isComplete = true,
                        isCameraScanning = false,
                        diagnostics = EnrollmentDiagnostics(
                            isCameraConnected = true,
                            framesReceived = totalFrames,
                            faceDetectionsCount = faceDetectionsCount.get(),
                            lastFaceState = "ONE FACE",
                            lastRejectionReason = "NONE (ENROLLMENT COMPLETE)",
                            acceptedSamples = count,
                            lastFaceMetrics = metricsStr,
                            lastDetectorError = null
                        )
                    )
                    speakGuidance("${newPerson.name} saved.", force = true)
                    Log.d(TAG, "Person '${newPerson.name}' successfully enrolled with $count samples.")
                }
            }
        }
        }
    }

    private fun updateRejection(
        guidance: String,
        rejectionReason: String,
        totalFrames: Long,
        metricsStr: String?
    ) {
        _enrollmentState.value = _enrollmentState.value.copy(
            guidanceMessage = guidance,
            samplesCaptured = collectedEmbeddings.size,
            diagnostics = EnrollmentDiagnostics(
                isCameraConnected = true,
                framesReceived = totalFrames,
                faceDetectionsCount = faceDetectionsCount.get(),
                lastFaceState = "ONE FACE",
                lastRejectionReason = rejectionReason,
                acceptedSamples = collectedEmbeddings.size,
                lastFaceMetrics = metricsStr,
                lastDetectorError = faceDetector.lastError
            )
        )
        speakGuidance(guidance, force = false)
    }

    /**
     * Evaluates a single frame during enrollment.
     */
    fun processFrame(
        bitmap: Bitmap,
        totalFrames: Long = framesReceivedCount.incrementAndGet(),
        timestampMs: Long? = null
    ) {
        val faces = faceDetector.detectFaces(bitmap)
        val detectorError = faceDetector.lastError
        processDetectedFaces(faces, bitmap, totalFrames, detectorError, timestampMs)
    }

    val frameAnalyzer = FrameAnalyzer { imageProxy ->
        synchronized(lock) {
            if (!isScanning) return@FrameAnalyzer
        }

        try {
            val currentFrames = framesReceivedCount.incrementAndGet()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rawBitmap = imageProxy.toBitmap()
            val bitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }

            processFrame(bitmap, currentFrames)
            bitmap.recycle()
        } catch (t: Throwable) {
            Log.e(TAG, "Error in enrollment frame analyzer", t)
        }
    }

    fun stopEnrollment() {
        synchronized(lock) {
            isScanning = false
            collectedEmbeddings.clear()
            _enrollmentState.value = _enrollmentState.value.copy(
                isCameraScanning = false
            )
        }
    }

    private fun speakGuidance(msg: String, force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || (msg != lastSpokenGuidance && now - lastGuidanceSpokenTime > 2500L)) {
            lastSpokenGuidance = msg
            lastGuidanceSpokenTime = now
            speechController?.speak(msg, SpeechPriority.HIGH)
        }
    }

    companion object {
        private const val TAG = "EnrollmentCoordinator"
    }
}
