package com.bibin.visioneye.people

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.max
import kotlin.math.min

/**
 * Architectural contract for on-device face detection.
 */
interface FaceDetector {
    /**
     * Last recorded detection error, or null if healthy.
     */
    val lastError: String? get() = null

    /**
     * Detects faces in the given [bitmap].
     *
     * @param bitmap Vision frame bitmap.
     * @return List of detected faces with bounding boxes, head poses, and quality metrics.
     */
    fun detectFaces(bitmap: Bitmap): List<DetectedFace>

    fun close()
}

/**
 * Concrete on-device face detector backed by Google ML Kit Face Detection.
 */
class MlKitFaceDetector(
    minFaceSize: Float = 0.12f
) : FaceDetector {

    private var _lastError: String? = null
    override val lastError: String? get() = _lastError

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(minFaceSize)
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(options)

    override fun detectFaces(bitmap: Bitmap): List<DetectedFace> {
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()
        if (imageWidth <= 0f || imageHeight <= 0f) return emptyList()

        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val task = detector.process(inputImage)
            val mlFaces = Tasks.await(task)
            _lastError = null

            val minDim = min(imageWidth, imageHeight)
            mlFaces.map { face ->
                val box = face.boundingBox
                val left = max(0f, box.left.toFloat())
                val top = max(0f, box.top.toFloat())
                val right = min(imageWidth, box.right.toFloat())
                val bottom = min(imageHeight, box.bottom.toFloat())

                val pixelRect = RectF(left, top, right, bottom)
                val normalizedRect = RectF(
                    left / imageWidth,
                    top / imageHeight,
                    right / imageWidth,
                    bottom / imageHeight
                )

                val faceDim = max(pixelRect.width(), pixelRect.height())
                val sizeRatio = faceDim / minDim

                DetectedFace(
                    boundingBox = normalizedRect,
                    pixelRect = pixelRect,
                    eulerX = face.headEulerAngleX,
                    eulerY = face.headEulerAngleY,
                    eulerZ = face.headEulerAngleZ,
                    sizeRatio = sizeRatio
                )
            }
        } catch (t: Throwable) {
            val msg = t.localizedMessage ?: t.javaClass.simpleName
            _lastError = msg
            Log.e(TAG, "Error running ML Kit face detection: $msg", t)
            emptyList()
        }
    }

    override fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ML Kit Face detector", e)
        }
    }

    companion object {
        private const val TAG = "MlKitFaceDetector"
    }
}
