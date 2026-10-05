package com.bibin.visioneye.people

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.bibin.visioneye.camera.FrameAnalyzer
import com.bibin.visioneye.speech.SpeechController
import com.bibin.visioneye.speech.SpeechPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Coordinator for VisionEye PEOPLE mode.
 *
 * Implements the on-device face recognition pipeline:
 * Camera frame -> Face Detection -> Quality Filtering -> Face Embedding (MobileFaceNet)
 * -> Local Database Matching -> Temporal Confirmation -> TTS Speech.
 *
 * @param faceDetector On-device face detector.
 * @param embeddingModel On-device face embedding model.
 * @param repository Local saved-people repository.
 * @param speechController Audio feedback synthesizer.
 * @param decisionEngine Temporal arbitrator and speech deduplication engine.
 * @param config Dedicated configuration parameters.
 */
class PeopleCoordinator(
    private val faceDetector: FaceDetector,
    private val embeddingModel: FaceEmbeddingModel,
    private val repository: PeopleRepository,
    private val speechController: SpeechController? = null,
    val decisionEngine: PeopleDecisionEngine = DefaultPeopleDecisionEngine(),
    val config: PeopleRecognitionConfig = PeopleRecognitionConfig()
) {

    private val _peopleState = MutableStateFlow(PeopleState())
    val peopleState: StateFlow<PeopleState> = _peopleState.asStateFlow()

    private var isCoordinatorActive = false
    private val lock = Any()

    /**
     * Frame analyzer registered with CameraX ImageAnalysis when in [com.bibin.visioneye.core.mode.VisionMode.PEOPLE].
     */
    val frameAnalyzer = FrameAnalyzer { imageProxy ->
        synchronized(lock) {
            if (!isCoordinatorActive) return@FrameAnalyzer
        }

        try {
            val startTime = SystemClock.uptimeMillis()
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

            // 1. Detect faces in frame
            val detectedFaces = faceDetector.detectFaces(bitmap)

            // 2. Evaluate each face and match against enrolled profiles
            val savedPeople = repository.getSavedPeople()
            val candidates = mutableListOf<FaceRecognitionCandidate>()

            for (face in detectedFaces) {
                val quality = face.evaluateQuality(config)
                if (quality == FaceQualityStatus.GOOD) {
                    val cropped = cropFace(bitmap, face.pixelRect)
                    if (cropped != null) {
                        val embedding = embeddingModel.extractEmbedding(cropped)
                        cropped.recycle()

                        if (embedding != null && savedPeople.isNotEmpty()) {
                            val (bestMatch, similarity) = embeddingModel.findBestMatch(
                                embedding,
                                savedPeople,
                                config.similarityThreshold
                            )
                            candidates.add(
                                FaceRecognitionCandidate(
                                    face = face,
                                    matchedPerson = bestMatch,
                                    similarity = similarity,
                                    isKnown = bestMatch != null
                                )
                            )
                        } else {
                            candidates.add(
                                FaceRecognitionCandidate(
                                    face = face,
                                    matchedPerson = null,
                                    similarity = 0f,
                                    isKnown = false
                                )
                            )
                        }
                    } else {
                        candidates.add(
                            FaceRecognitionCandidate(
                                face = face,
                                matchedPerson = null,
                                similarity = 0f,
                                isKnown = false
                            )
                        )
                    }
                } else {
                    // Face detected but rejected due to poor quality (too small, bad angle, etc.)
                    candidates.add(
                        FaceRecognitionCandidate(
                            face = face,
                            matchedPerson = null,
                            similarity = 0f,
                            isKnown = false
                        )
                    )
                }
            }

            bitmap.recycle()
            val elapsedMs = SystemClock.uptimeMillis() - startTime

            // 3. Temporal decision arbitration
            val now = System.currentTimeMillis()
            val decisionResult = decisionEngine.process(candidates, now)

            // 4. Vocalize alert if confirmed and not suppressed
            decisionResult.spokenAlert?.let { alert ->
                Log.d(TAG, "PEOPLE mode vocalizing: '$alert'")
                speechController?.speak(alert, SpeechPriority.NORMAL)
            }

            synchronized(lock) {
                if (!isCoordinatorActive) return@FrameAnalyzer
                _peopleState.value = PeopleState(
                    status = decisionResult.status,
                    recognizedNames = decisionResult.recognizedNames,
                    hasUnknownPerson = decisionResult.hasUnknownPerson,
                    isConfirmed = decisionResult.isConfirmed,
                    candidates = decisionResult.candidates,
                    lastSpokenAlert = decisionResult.spokenAlert ?: _peopleState.value.lastSpokenAlert,
                    inferenceTimeMs = elapsedMs,
                    isActive = true
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error processing frame in PEOPLE mode", t)
        }
    }

    /**
     * Activates PEOPLE mode face recognition.
     */
    fun activate() = synchronized(lock) {
        isCoordinatorActive = true
        decisionEngine.reset()
        _peopleState.value = PeopleState(
            status = PeopleScanStatus.LOOKING_FOR_PEOPLE,
            isActive = true
        )
        Log.d(TAG, "PeopleCoordinator activated.")
    }

    /**
     * Deactivates PEOPLE mode and clears state.
     */
    fun deactivate() = synchronized(lock) {
        isCoordinatorActive = false
        decisionEngine.reset()
        speechController?.stop()
        _peopleState.value = PeopleState(
            status = PeopleScanStatus.LOOKING_FOR_PEOPLE,
            isActive = false
        )
        Log.d(TAG, "PeopleCoordinator deactivated.")
    }

    /**
     * Releases models and repository bindings.
     */
    fun release() {
        deactivate()
        faceDetector.close()
        embeddingModel.close()
    }

    companion object {
        private const val TAG = "PeopleCoordinator"

        /**
         * Computes the bounding box coordinates for a face crop.
         * Expands rectangular face detections (e.g. portrait aspect ratios from ML Kit)
         * to a square centered on the face, clamped to the image dimensions.
         * This preserves 1:1 facial geometry before scaling to the 112x112 model input.
         */
        fun computeSquareCropBounds(
            imageWidth: Int,
            imageHeight: Int,
            faceLeft: Float,
            faceTop: Float,
            faceRight: Float,
            faceBottom: Float
        ): CropBounds? {
            val width = faceRight - faceLeft
            val height = faceBottom - faceTop
            if (width < 20f || height < 20f) return null

            val side = max(width, height)
            var left = faceLeft + (width - side) / 2f
            var top = faceTop + (height - side) / 2f
            var right = left + side
            var bottom = top + side

            // Shift square within image boundaries if clipped
            if (left < 0f) {
                val shift = -left
                left = 0f
                right = min(imageWidth.toFloat(), right + shift)
            }
            if (right > imageWidth.toFloat()) {
                val shift = right - imageWidth.toFloat()
                right = imageWidth.toFloat()
                left = max(0f, left - shift)
            }
            if (top < 0f) {
                val shift = -top
                top = 0f
                bottom = min(imageHeight.toFloat(), bottom + shift)
            }
            if (bottom > imageHeight.toFloat()) {
                val shift = bottom - imageHeight.toFloat()
                bottom = imageHeight.toFloat()
                top = max(0f, top - shift)
            }

            val iLeft = left.toInt()
            val iTop = top.toInt()
            val iRight = right.toInt()
            val iBottom = bottom.toInt()

            if (iRight - iLeft < 20 || iBottom - iTop < 20) return null
            return CropBounds(iLeft, iTop, iRight, iBottom)
        }

        /**
         * Safely crops a face rectangle from the frame bitmap.
         * Expands rectangular face detections to a square centered on the face
         * to avoid geometric distortion when resizing to the 112x112 model input.
         */
        fun cropFace(bitmap: Bitmap, pixelRect: RectF): Bitmap? {
            val bounds = computeSquareCropBounds(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                faceLeft = pixelRect.left,
                faceTop = pixelRect.top,
                faceRight = pixelRect.right,
                faceBottom = pixelRect.bottom
            ) ?: return null

            return try {
                Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width, bounds.height)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Pixel bounds for cropping a face from an image.
 */
data class CropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isSquare: Boolean get() = width == height
}
