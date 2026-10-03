package com.bibin.visioneye.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.bibin.visioneye.ai.ObjectDetector
import com.bibin.visioneye.ai.YoloDebugState
import com.bibin.visioneye.ai.YoloFrameAnalyzer
import com.bibin.visioneye.ai.YoloV8Detector
import com.bibin.visioneye.core.mode.VisionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Concrete CameraX-backed controller for the VisionEye camera subsystem.
 *
 * Responsibilities:
 * - Camera initialization & lifecycle binding
 * - Camera runtime permission checking
 * - Rear-camera enforcement
 * - Dedicated background thread for [ImageAnalysis]
 * - Frame throttling (~5 FPS) via [FrameScheduler] to prevent accumulating delayed work
 * - Pluggable [ObjectDetector] integration (active only in [VisionMode.NAVIGATE])
 * - Clean unbinding and resource cleanup on mode change or user exit
 */
class CameraManager(
    private val context: Context,
    targetAnalysisFps: Double = 5.0,
    val objectDetector: ObjectDetector = YoloV8Detector(context),
    override val speechController: com.bibin.visioneye.speech.SpeechController? = null,
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "VisionEye-FrameAnalyzer").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }
) : CameraController {

    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow<CameraState>(CameraState.Idle)
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    val frameScheduler = FrameScheduler(targetFps = targetAnalysisFps)
    val frameDispatcher = FrameAnalysisDispatcher(scheduler = frameScheduler)

    override val diagnostics: StateFlow<FrameAnalysisDiagnostics> = frameDispatcher.diagnostics

    val yoloAnalyzer = YoloFrameAnalyzer(objectDetector, speechController = speechController)
    override val yoloState: StateFlow<YoloDebugState> = yoloAnalyzer.yoloState

    private var currentActiveMode: VisionMode = VisionMode.NAVIGATE

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentSurfaceProvider: Preview.SurfaceProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    override val hasPermission: Boolean
        get() = checkCameraPermission()

    override fun checkCameraPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && _state.value !is CameraState.PermissionRequired) {
            _state.value = CameraState.PermissionRequired()
        }
        return granted
    }

    override fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider?
    ) {
        currentLifecycleOwner = lifecycleOwner
        if (surfaceProvider != null) {
            currentSurfaceProvider = surfaceProvider
        }

        if (!checkCameraPermission()) {
            Log.w(TAG, "Cannot bind camera: CAMERA permission not granted")
            _state.value = CameraState.PermissionRequired()
            return
        }

        _state.value = CameraState.Initializing

        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Verify rear camera availability
                if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    val errorMsg = "Rear-facing camera not found on this device."
                    Log.e(TAG, errorMsg)
                    _state.value = CameraState.Error(errorMsg)
                    return@addListener
                }

                // Reset analyzer and scheduler state for a clean pipeline
                frameScheduler.reset()
                frameDispatcher.resetDiagnostics()

                // Unbind previous use cases before binding new ones
                imageAnalysis?.clearAnalyzer()
                provider.unbindAll()

                val useCases = mutableListOf<UseCase>()

                // 1. Live Preview Use Case
                val preview = Preview.Builder().build()
                currentSurfaceProvider?.let { providerTarget ->
                    preview.setSurfaceProvider(providerTarget)
                }
                useCases.add(preview)

                // 2. ImageAnalysis Use Case (Non-blocking latest-frame backpressure for YOLO inference)
                val analysisUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor, frameDispatcher)
                    }
                imageAnalysis = analysisUseCase
                useCases.add(analysisUseCase)

                // Attach YOLO frame analyzer only if in NAVIGATE mode
                if (currentActiveMode == VisionMode.NAVIGATE) {
                    setFrameAnalyzer(yoloAnalyzer)
                } else {
                    clearFrameAnalyzer()
                }

                // Bind to Lifecycle with rear-facing camera selector
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    *useCases.toTypedArray()
                )

                _state.value = CameraState.Streaming
                Log.d(TAG, "Camera bound successfully to lifecycle with rear camera & 5 FPS ImageAnalysis.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
                _state.value = CameraState.Error(
                    message = "Camera binding failed: ${e.localizedMessage}",
                    cause = e
                )
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    override fun unbindCamera() {
        try {
            clearFrameAnalyzer()
            yoloAnalyzer.reset()

            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            cameraProvider?.unbindAll()
            camera = null
            currentSurfaceProvider = null
            frameScheduler.reset()
            frameDispatcher.resetDiagnostics()

            if (_state.value is CameraState.Streaming || _state.value is CameraState.Initializing) {
                _state.value = CameraState.Idle
            }
            Log.d(TAG, "Camera unbind and analyzer release complete.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind camera", e)
            _state.value = CameraState.Error(
                message = "Failed to unbind camera: ${e.localizedMessage}",
                cause = e
            )
        }
    }

    override fun startCamera() {
        val owner = currentLifecycleOwner
        if (owner != null) {
            bindCamera(owner, currentSurfaceProvider)
        } else {
            Log.d(TAG, "startCamera requested with no active LifecycleOwner.")
        }
    }

    override fun pauseCamera() {
        try {
            clearFrameAnalyzer()
            yoloAnalyzer.reset()

            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            cameraProvider?.unbindAll()
            camera = null
            frameScheduler.reset()
            _state.value = CameraState.Paused
            Log.d(TAG, "Camera paused.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause camera", e)
        }
    }

    override fun stopCamera() {
        unbindCamera()
        currentLifecycleOwner = null
        _state.value = CameraState.Idle
    }

    override fun setFrameAnalyzer(analyzer: FrameAnalyzer) {
        frameDispatcher.clearAnalyzers()
        frameDispatcher.addAnalyzer(analyzer)
    }

    override fun clearFrameAnalyzer() {
        frameDispatcher.clearAnalyzers()
    }

    /**
     * Configures the target analysis frames per second rate (default 5.0).
     */
    fun setTargetAnalysisFps(fps: Double) {
        frameScheduler.targetFps = fps
    }

    override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {
        currentActiveMode = newMode
        if (newMode == VisionMode.NAVIGATE) {
            setFrameAnalyzer(yoloAnalyzer)
        } else {
            clearFrameAnalyzer()
            yoloAnalyzer.reset()
        }

        if (!isEnabledFor(newMode)) {
            Log.d(TAG, "Active mode switched to $newMode (no camera needed). Releasing camera.")
            stopCamera()
        }
    }

    /**
     * Cleanly shutdowns internal executors and detector. Call when application terminates.
     */
    fun release() {
        stopCamera()
        objectDetector.close()
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.shutdown()
        }
    }

    companion object {
        private const val TAG = "VisionEyeCameraManager"
    }
}
