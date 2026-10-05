package com.bibin.visioneye.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import com.bibin.visioneye.people.PeopleCoordinator
import com.bibin.visioneye.people.PeopleState
import com.bibin.visioneye.read.ReadCoordinator
import com.bibin.visioneye.read.ReadState
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
 * - On-device English text reading pipeline via [ReadCoordinator] (active in [VisionMode.READ])
 * - High-resolution capture via [ImageCapture] for READ mode OCR
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

    val readCoordinator = ReadCoordinator(speechController = speechController)
    override val readState: StateFlow<ReadState> = readCoordinator.readState

    override val peopleRepository: com.bibin.visioneye.people.PeopleRepository =
        com.bibin.visioneye.people.LocalFilePeopleRepository(context.filesDir)

    val faceDetector: com.bibin.visioneye.people.FaceDetector =
        com.bibin.visioneye.people.MlKitFaceDetector()

    val embeddingModel: com.bibin.visioneye.people.FaceEmbeddingModel =
        com.bibin.visioneye.people.MobileFaceNetEmbeddingModel(context)

    val peopleCoordinator = com.bibin.visioneye.people.PeopleCoordinator(
        faceDetector = faceDetector,
        embeddingModel = embeddingModel,
        repository = peopleRepository,
        speechController = speechController
    )
    override val peopleState: StateFlow<PeopleState> = peopleCoordinator.peopleState

    val enrollmentCoordinator = com.bibin.visioneye.people.EnrollmentCoordinator(
        faceDetector = faceDetector,
        embeddingModel = embeddingModel,
        repository = peopleRepository,
        speechController = speechController
    )
    override val enrollmentState: StateFlow<com.bibin.visioneye.people.EnrollmentState> =
        enrollmentCoordinator.enrollmentState

    private var _isEnrollmentActive: Boolean = false
    override val isEnrollmentActive: Boolean
        get() = _isEnrollmentActive

    override fun startPersonEnrollment(name: String) {
        _isEnrollmentActive = true
        yoloAnalyzer.reset()
        readCoordinator.deactivate()
        peopleCoordinator.deactivate()
        enrollmentCoordinator.startEnrollment(name)
        setFrameAnalyzer(enrollmentCoordinator.frameAnalyzer)
        Log.d(TAG, "startPersonEnrollment: enrollment frame analyzer attached and active.")
    }

    override fun stopPersonEnrollment() {
        _isEnrollmentActive = false
        enrollmentCoordinator.stopEnrollment()
        when (currentActiveMode) {
            VisionMode.NAVIGATE -> setFrameAnalyzer(yoloAnalyzer)
            VisionMode.READ -> {
                readCoordinator.activate()
                setFrameAnalyzer(readCoordinator.frameAnalyzer)
            }
            VisionMode.PEOPLE -> {
                peopleCoordinator.activate()
                setFrameAnalyzer(peopleCoordinator.frameAnalyzer)
            }
            else -> clearFrameAnalyzer()
        }
        Log.d(TAG, "stopPersonEnrollment: restored analyzer for mode $currentActiveMode.")
    }

    private var currentActiveMode: VisionMode = VisionMode.NAVIGATE

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentSurfaceProvider: Preview.SurfaceProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null

    init {
        readCoordinator.captureProvider = { onSuccess, onError ->
            captureImage(onSuccess, onError)
        }
    }

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

                // 3. ImageCapture Use Case (High-resolution capture for READ mode)
                val captureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = captureUseCase
                useCases.add(captureUseCase)

                // Attach active frame analyzer based on enrollment state or mode
                if (_isEnrollmentActive) {
                    Log.d(TAG, "bindCamera: enrollment is active; preserving enrollment frame analyzer.")
                    setFrameAnalyzer(enrollmentCoordinator.frameAnalyzer)
                } else {
                    when (currentActiveMode) {
                        VisionMode.NAVIGATE -> setFrameAnalyzer(yoloAnalyzer)
                        VisionMode.READ -> {
                            readCoordinator.activate()
                            setFrameAnalyzer(readCoordinator.frameAnalyzer)
                        }
                        VisionMode.PEOPLE -> {
                            peopleCoordinator.activate()
                            setFrameAnalyzer(peopleCoordinator.frameAnalyzer)
                        }
                        else -> clearFrameAnalyzer()
                    }
                }

                // Bind to Lifecycle with rear-facing camera selector
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    *useCases.toTypedArray()
                )

                _state.value = CameraState.Streaming
                Log.d(TAG, "Camera bound successfully to lifecycle with rear camera, 5 FPS ImageAnalysis & ImageCapture.")
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
            readCoordinator.deactivate()
            peopleCoordinator.deactivate()

            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            imageCapture = null
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
            readCoordinator.deactivate()
            peopleCoordinator.deactivate()

            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            imageCapture = null
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

    override fun captureImage(
        onSuccess: (Bitmap) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("ImageCapture is not bound or initialized"))
            return
        }

        capture.takePicture(
            analysisExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val bitmap = imageProxy.toBitmap()
                        val uprightBitmap = if (rotationDegrees != 0) {
                            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        } else {
                            bitmap
                        }
                        onSuccess(uprightBitmap)
                    } catch (t: Throwable) {
                        onError(t)
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    override fun onModeChanged(newMode: VisionMode, previousMode: VisionMode) {
        currentActiveMode = newMode
        if (_isEnrollmentActive) {
            Log.d(TAG, "onModeChanged ignored for active frame analyzer while face enrollment is in progress.")
            return
        }
        when (newMode) {
            VisionMode.NAVIGATE -> {
                readCoordinator.deactivate()
                peopleCoordinator.deactivate()
                setFrameAnalyzer(yoloAnalyzer)
            }
            VisionMode.READ -> {
                yoloAnalyzer.reset()
                peopleCoordinator.deactivate()
                readCoordinator.activate()
                setFrameAnalyzer(readCoordinator.frameAnalyzer)
            }
            VisionMode.PEOPLE -> {
                yoloAnalyzer.reset()
                readCoordinator.deactivate()
                peopleCoordinator.activate()
                setFrameAnalyzer(peopleCoordinator.frameAnalyzer)
            }
            else -> {
                clearFrameAnalyzer()
                yoloAnalyzer.reset()
                readCoordinator.deactivate()
                peopleCoordinator.deactivate()
            }
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
        readCoordinator.release()
        peopleCoordinator.release()
        faceDetector.close()
        embeddingModel.close()
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.shutdown()
        }
    }

    companion object {
        private const val TAG = "VisionEyeCameraManager"
    }
}
