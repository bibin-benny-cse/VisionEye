package com.bibin.visioneye.people

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Quality validation assessment for a detected face.
 */
enum class FaceQualityStatus {
    GOOD,
    TOO_SMALL,
    BAD_ANGLE_YAW,
    BAD_ANGLE_PITCH,
    BAD_ANGLE_ROLL,
    OUT_OF_BOUNDS
}

/**
 * Normalized face detected by the face detector in a vision frame.
 *
 * @property boundingBox Normalized bounding box in [0.0, 1.0] range.
 * @property pixelRect Absolute pixel rectangle in image space.
 * @property eulerX Head pitch angle in degrees.
 * @property eulerY Head yaw angle in degrees.
 * @property eulerZ Head roll angle in degrees.
 * @property sizeRatio Ratio of face dimension to frame dimension.
 */
data class DetectedFace(
    val boundingBox: RectF,
    val pixelRect: RectF,
    val eulerX: Float = 0f,
    val eulerY: Float = 0f,
    val eulerZ: Float = 0f,
    val sizeRatio: Float = 0.2f
) {
    /**
     * Checks if this face meets the orientation and size requirements.
     */
    fun evaluateQuality(config: PeopleRecognitionConfig): FaceQualityStatus {
        if (sizeRatio < config.minFaceSizeRatio) return FaceQualityStatus.TOO_SMALL
        if (abs(eulerY) > config.maxEulerY) return FaceQualityStatus.BAD_ANGLE_YAW
        if (abs(eulerX) > config.maxEulerX) return FaceQualityStatus.BAD_ANGLE_PITCH
        if (abs(eulerZ) > config.maxEulerZ) return FaceQualityStatus.BAD_ANGLE_ROLL
        if (boundingBox.left < 0f || boundingBox.top < 0f || boundingBox.right > 1f || boundingBox.bottom > 1f) {
            // Minor boundary leeway allowed, but heavily clipped faces are rejected
            if (boundingBox.left < -0.05f || boundingBox.top < -0.05f || boundingBox.right > 1.05f || boundingBox.bottom > 1.05f) {
                return FaceQualityStatus.OUT_OF_BOUNDS
            }
        }
        return FaceQualityStatus.GOOD
    }
}

/**
 * Candidate recognition result for a single detected face.
 *
 * @property face The underlying detected face.
 * @property matchedPerson The enrolled person matched, or null if below threshold or no match.
 * @property similarity The highest cosine similarity achieved against the matched person (0.0 to 1.0).
 * @property isKnown True only if matched with similarity >= [PeopleRecognitionConfig.similarityThreshold].
 */
data class FaceRecognitionCandidate(
    val face: DetectedFace,
    val matchedPerson: SavedPerson?,
    val similarity: Float,
    val isKnown: Boolean
) {
    val displayName: String
        get() = if (isKnown && matchedPerson != null) matchedPerson.name else "Unknown person"
}

/**
 * Architectural contract for face embedding extraction and similarity comparison.
 */
interface FaceEmbeddingModel {
    /**
     * Extracts a normalized 192-dimensional embedding vector from a cropped face bitmap.
     *
     * @param faceBitmap Cropped bitmap containing the face.
     * @return 192-element normalized float array, or null if inference failed.
     */
    fun extractEmbedding(faceBitmap: Bitmap): FloatArray?

    /**
     * Calculates the cosine similarity between two normalized embedding vectors.
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float

    /**
     * Compares a query embedding against all embeddings of saved people and returns the best match.
     *
     * @param queryEmbedding Query face embedding.
     * @param savedPeople List of enrolled people.
     * @param threshold Similarity threshold.
     */
    fun findBestMatch(
        queryEmbedding: FloatArray,
        savedPeople: List<SavedPerson>,
        threshold: Float
    ): Pair<SavedPerson?, Float>

    fun close()
}

/**
 * Concrete TensorFlow Lite implementation of [FaceEmbeddingModel] using MobileFaceNet.
 *
 * Runs 112x112 input -> 192-d normalized embedding inference entirely on-device.
 */
class MobileFaceNetEmbeddingModel(
    private val context: Context,
    private val modelAssetPath: String = "models/mobilefacenet.tflite"
) : FaceEmbeddingModel {

    private var interpreter: Interpreter? = null
    private val lock = Any()

    init {
        loadModel()
    }

    private fun loadModel() = synchronized(lock) {
        try {
            val afd = context.assets.openFd(modelAssetPath)
            val inputStream = FileInputStream(afd.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = afd.startOffset
            val declaredLength = afd.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "MobileFaceNet TFLite interpreter initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MobileFaceNet TFLite model from $modelAssetPath", e)
        }
    }

    override fun extractEmbedding(faceBitmap: Bitmap): FloatArray? = synchronized(lock) {
        val currInterpreter = interpreter ?: return null

        try {
            // Resize to 112x112 for MobileFaceNet
            val scaledBitmap = if (faceBitmap.width == INPUT_SIZE && faceBitmap.height == INPUT_SIZE) {
                faceBitmap
            } else {
                Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
            }

            // Convert to ByteBuffer with (pixel - 127.5) / 128.0 normalization
            val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
            scaledBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            var pixelIdx = 0
            for (i in 0 until INPUT_SIZE) {
                for (j in 0 until INPUT_SIZE) {
                    val pixel = intValues[pixelIdx++]
                    val r = ((pixel shr 16) and 0xFF)
                    val g = ((pixel shr 8) and 0xFF)
                    val b = (pixel and 0xFF)

                    // Normalize to [-1, 1] range standard for MobileFaceNet (RGB channel order)
                    inputBuffer.putFloat(normalizePixel(r))
                    inputBuffer.putFloat(normalizePixel(g))
                    inputBuffer.putFloat(normalizePixel(b))
                }
            }

            if (scaledBitmap != faceBitmap) {
                scaledBitmap.recycle()
            }

            inputBuffer.rewind()
            val outputBuffer = Array(1) { FloatArray(EMBEDDING_SIZE) }
            currInterpreter.run(inputBuffer, outputBuffer)

            // Normalize vector to unit length (L2 norm)
            val rawEmbedding = outputBuffer[0]
            return l2Normalize(rawEmbedding)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating face embedding", e)
            return null
        }
    }

    override fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return cosineSimilarity(embedding1, embedding2)
    }

    override fun findBestMatch(
        queryEmbedding: FloatArray,
        savedPeople: List<SavedPerson>,
        threshold: Float
    ): Pair<SavedPerson?, Float> {
        var bestMatch: SavedPerson? = null
        var highestSimilarity = -1.0f

        for (person in savedPeople) {
            for (enrolledVec in person.embeddings) {
                val similarity = cosineSimilarity(queryEmbedding, enrolledVec)
                if (similarity > highestSimilarity) {
                    highestSimilarity = similarity
                    if (similarity >= threshold) {
                        bestMatch = person
                    }
                }
            }
        }

        return Pair(bestMatch, highestSimilarity.coerceAtLeast(0f))
    }

    override fun close() = synchronized(lock) {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG = "MobileFaceNet"
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 192

        // Normalization constants for MobileFaceNet
        // Standard formula: normalized = (pixel - 127.5f) / 128.0f
        const val IMAGE_MEAN = 127.5f
        const val IMAGE_STD = 128.0f

        /**
         * Normalizes an 8-bit unsigned RGB channel intensity [0..255]
         * to the MobileFaceNet standard [-1.0, 1.0] range.
         */
        fun normalizePixel(channelValue: Int): Float {
            return (channelValue - IMAGE_MEAN) / IMAGE_STD
        }

        /**
         * L2 normalization of an embedding vector.
         */
        fun l2Normalize(vector: FloatArray): FloatArray {
            var sum = 0f
            for (v in vector) {
                sum += v * v
            }
            val norm = sqrt(sum)
            if (norm == 0f) return vector
            return FloatArray(vector.size) { i -> vector[i] / norm }
        }

        /**
         * Cosine similarity between two unit-normalized vectors.
         */
        fun cosineSimilarity(u: FloatArray, v: FloatArray): Float {
            if (u.size != v.size) return 0f
            var dot = 0f
            for (i in u.indices) {
                dot += u[i] * v[i]
            }
            return dot
        }
    }
}
