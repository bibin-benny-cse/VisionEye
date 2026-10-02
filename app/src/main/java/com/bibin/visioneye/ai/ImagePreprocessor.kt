package com.bibin.visioneye.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Coordinate mapping metadata preserving letterbox scale and padding transformations.
 *
 * Enables converting model bounding boxes in `[0, targetWidth]` and `[0, targetHeight]`
 * back to normalized `[0.0, 1.0]` coordinates relative to the upright camera image.
 *
 * @property scale Uniform scale factor applied to the original image.
 * @property padX Horizontal padding (in pixels) added to each side to center the image.
 * @property padY Vertical padding (in pixels) added to top and bottom to center the image.
 * @property originalWidth Width of the upright camera image before letterboxing.
 * @property originalHeight Height of the upright camera image before letterboxing.
 * @property targetWidth Target input width expected by the detector (e.g., 640).
 * @property targetHeight Target input height expected by the detector (e.g., 640).
 */
data class LetterboxTransform(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val originalWidth: Int,
    val originalHeight: Int,
    val targetWidth: Int,
    val targetHeight: Int
) {
    /**
     * Converts a box defined by center X/Y and width/height in model input space
     * back to a normalized [BoundingBox] on the original upright frame.
     */
    fun mapBoxToNormalized(cx: Float, cy: Float, width: Float, height: Float): BoundingBox {
        // Remove letterbox padding and rescale
        val left = (cx - width / 2f - padX) / scale
        val top = (cy - height / 2f - padY) / scale
        val right = (cx + width / 2f - padX) / scale
        val bottom = (cy + height / 2f - padY) / scale

        // Normalize to [0.0, 1.0] and clamp to frame boundaries
        return BoundingBox(
            left = (left / originalWidth).coerceIn(0f, 1f),
            top = (top / originalHeight).coerceIn(0f, 1f),
            right = (right / originalWidth).coerceIn(0f, 1f),
            bottom = (bottom / originalHeight).coerceIn(0f, 1f)
        )
    }
}

/**
 * Utilities for image preprocessing (sensor rotation, aspect-ratio letterboxing,
 * RGB channel extraction, normalization, and planar NCHW Float32 tensor conversion).
 */
object ImagePreprocessor {

    /**
     * Rotates [source] by the camera sensor's [orientationDegrees].
     *
     * Rotation Handling:
     * CameraX frames arrive in the camera sensor's native orientation (typically 90°
     * clockwise for rear cameras in portrait mode). We apply an affine rotation matrix
     * before letterboxing so that YOLO inference and bounding boxes align upright with
     * the physical phone screen and user viewpoint.
     */
    fun rotateBitmap(source: Bitmap, orientationDegrees: Int): Bitmap {
        if (orientationDegrees == 0) return source
        val matrix = Matrix().apply { postRotate(orientationDegrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Scales and letterboxes [uprightBitmap] to [targetWidth] x [targetHeight]
     * preserving aspect ratio and padding empty borders with neutral gray (RGB 114, 114, 114).
     *
     * @return Pair containing the letterboxed ARGB_8888 bitmap and the [LetterboxTransform]
     *         needed to unmap output coordinates.
     */
    fun letterbox(
        uprightBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Pair<Bitmap, LetterboxTransform> {
        val origW = uprightBitmap.width.toFloat()
        val origH = uprightBitmap.height.toFloat()

        val scale = min(targetWidth / origW, targetHeight / origH)
        val scaledW = origW * scale
        val scaledH = origH * scale

        val padX = (targetWidth - scaledW) / 2f
        val padY = (targetHeight - scaledH) / 2f

        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        // Neutral gray fill standard for YOLO letterbox preprocessing
        canvas.drawColor(Color.rgb(114, 114, 114))

        val destRect = RectF(padX, padY, padX + scaledW, padY + scaledH)
        canvas.drawBitmap(uprightBitmap, null, destRect, null)

        val transform = LetterboxTransform(
            scale = scale,
            padX = padX,
            padY = padY,
            originalWidth = uprightBitmap.width,
            originalHeight = uprightBitmap.height,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )

        return Pair(outputBitmap, transform)
    }

    /**
     * Packs an array of ARGB_8888 [pixels] into a native-ordered Float32 [ByteBuffer]
     * matching the specified [layout] with values normalized to [0.0f, 1.0f].
     *
     * In [TensorLayout.NCHW] (planar):
     * The buffer is packed channel-by-channel:
     * - First `width * height` floats: All Red channel values
     * - Next `width * height` floats: All Green channel values
     * - Final `width * height` floats: All Blue channel values
     *
     * RGB Channel Extraction:
     * - Red: `(pixel shr 16) and 0xFF`
     * - Green: `(pixel shr 8) and 0xFF`
     * - Blue: `pixel and 0xFF`
     *
     * @param pixels Array of packed 32-bit ARGB integer pixels.
     * @param width Width of the image in pixels.
     * @param height Height of the image in pixels.
     * @param layout Memory ordering ([TensorLayout.NCHW] or [TensorLayout.NHWC]).
     * @param targetBuffer Optional pre-allocated direct buffer for reuse.
     * @return Rewound Float32 [ByteBuffer] ready for TFLite inference.
     */
    fun packPixelsToFloatBuffer(
        pixels: IntArray,
        width: Int,
        height: Int,
        layout: TensorLayout = TensorLayout.NCHW,
        targetBuffer: ByteBuffer? = null
    ): ByteBuffer {
        val totalPixels = width * height
        val requiredBytes = 1 * 3 * totalPixels * 4 // [1, 3, H, W] * 4 bytes per float

        val buffer = if (targetBuffer != null && targetBuffer.capacity() >= requiredBytes) {
            targetBuffer.apply { rewind() }
        } else {
            ByteBuffer.allocateDirect(requiredBytes).apply { order(ByteOrder.nativeOrder()) }
        }

        when (layout) {
            TensorLayout.NCHW -> {
                // Planar memory layout: All R values, then all G values, then all B values
                // Channel 0: Red
                for (p in 0 until totalPixels) {
                    val pixel = pixels[p]
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    buffer.putFloat(r)
                }

                // Channel 1: Green
                for (p in 0 until totalPixels) {
                    val pixel = pixels[p]
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    buffer.putFloat(g)
                }

                // Channel 2: Blue
                for (p in 0 until totalPixels) {
                    val pixel = pixels[p]
                    val b = (pixel and 0xFF) / 255.0f
                    buffer.putFloat(b)
                }
            }
            TensorLayout.NHWC -> {
                // Interleaved memory layout: R, G, B, R, G, B...
                for (p in 0 until totalPixels) {
                    val pixel = pixels[p]
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f

                    buffer.putFloat(r)
                    buffer.putFloat(g)
                    buffer.putFloat(b)
                }
            }
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Converts an ARGB_8888 letterboxed [bitmap] into a native-ordered Float32 [ByteBuffer]
     * formatted as [layout] (default: planar NCHW) with pixel values normalized to [0.0f, 1.0f].
     *
     * Reuses [targetBuffer] and [reusablePixelArray] when supplied to prevent per-frame GC allocations.
     */
    fun bitmapToFloatBuffer(
        bitmap: Bitmap,
        layout: TensorLayout = TensorLayout.NCHW,
        targetBuffer: ByteBuffer? = null,
        reusablePixelArray: IntArray? = null
    ): ByteBuffer {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        val pixels = if (reusablePixelArray != null && reusablePixelArray.size >= totalPixels) {
            reusablePixelArray
        } else {
            IntArray(totalPixels)
        }

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return packPixelsToFloatBuffer(
            pixels = pixels,
            width = width,
            height = height,
            layout = layout,
            targetBuffer = targetBuffer
        )
    }
}
