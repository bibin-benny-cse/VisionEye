package com.bibin.visioneye

import android.app.Application
import com.bibin.visioneye.camera.CameraController
import com.bibin.visioneye.camera.CameraManager
import com.bibin.visioneye.core.mode.DefaultModeManager
import com.bibin.visioneye.core.mode.ModeManager
import com.bibin.visioneye.speech.DefaultSpeechController
import com.bibin.visioneye.speech.SpeechController

/**
 * Base Application class for VisionEye.
 *
 * Initializes application-wide singletons including the central [ModeManager],
 * the audio [SpeechController], and the mode-aware [CameraController].
 */
class VisionEyeApplication : Application() {

    lateinit var modeManager: ModeManager
        private set

    lateinit var speechController: SpeechController
        private set

    lateinit var cameraController: CameraController
        private set

    lateinit var navigationController: com.bibin.visioneye.navigation.NavigationController
        private set

    lateinit var emergencyController: com.bibin.visioneye.emergency.EmergencyController
        private set

    val emergencyContactRepository: com.bibin.visioneye.emergency.EmergencyContactRepository by lazy {
        com.bibin.visioneye.emergency.SharedPreferencesEmergencyContactRepository(this)
    }

    val smsSender: com.bibin.visioneye.emergency.SmsSender by lazy {
        com.bibin.visioneye.emergency.DefaultSmsSender(this)
    }

    /**
     * Lazy singleton for GPS location acquisition, prepared for NAVIGATION mode guidance.
     * Does not initiate location updates until explicitly commanded.
     */
    val locationEngine: com.bibin.visioneye.navigation.location.LocationEngine by lazy {
        com.bibin.visioneye.navigation.location.DefaultLocationEngine(this)
    }

    /**
     * Lazy singleton for hardware compass and orientation tracking.
     * Does not activate sensor listeners until explicitly commanded.
     */
    val orientationEngine: com.bibin.visioneye.navigation.orientation.OrientationEngine by lazy {
        com.bibin.visioneye.navigation.orientation.DefaultOrientationEngine(this)
    }

    /**
     * Lazy singleton for geocoding destination queries.
     */
    val geocodingService: com.bibin.visioneye.navigation.routing.GeocodingService by lazy {
        com.bibin.visioneye.navigation.routing.NominatimGeocodingService()
    }

    /**
     * Lazy singleton for pedestrian walking route calculations.
     */
    val pedestrianRoutingService: com.bibin.visioneye.navigation.routing.PedestrianRoutingService by lazy {
        com.bibin.visioneye.navigation.routing.OsrmPedestrianRoutingService()
    }

    /**
     * Lazy singleton for pedestrian turn-by-turn guidance and progress tracking.
     */
    val guidanceEngine: com.bibin.visioneye.navigation.guidance.NavigationGuidanceEngine by lazy {
        com.bibin.visioneye.navigation.guidance.DefaultNavigationGuidanceEngine()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val mm = DefaultModeManager()
        modeManager = mm

        val sc = DefaultSpeechController(this)
        speechController = sc

        val cm = CameraManager(this, speechController = sc)
        cameraController = cm
        // Register camera controller to mode transitions for automatic pause/stop
        mm.addListener(cm)

        val nc = com.bibin.visioneye.navigation.DefaultNavigationController(
            context = this,
            locationEngine = locationEngine,
            orientationEngine = orientationEngine,
            geocodingService = geocodingService,
            pedestrianRoutingService = pedestrianRoutingService,
            guidanceEngine = guidanceEngine,
            speechController = sc,
            modeManager = mm
        )
        navigationController = nc

        // Register navigation controller to mode transitions for automatic GPS activation/deactivation
        mm.addListener(nc)

        val ec = com.bibin.visioneye.emergency.DefaultEmergencyController(
            context = this,
            locationEngine = locationEngine,
            contactRepository = emergencyContactRepository,
            smsSender = smsSender,
            speechController = sc
        )
        emergencyController = ec

        // Register emergency controller to mode transitions for automatic SOS activation/deactivation
        mm.addListener(ec)
    }

    companion object {
        lateinit var instance: VisionEyeApplication
            private set
    }
}
