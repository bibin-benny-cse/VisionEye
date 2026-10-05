package com.bibin.visioneye.navigation.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production implementation of [OrientationEngine] using the Android Sensor framework.
 *
 * Sensor Strategy:
 * 1. Primary: [Sensor.TYPE_ROTATION_VECTOR] (hardware-fused gyroscope + accelerometer + geomagnetic).
 * 2. Fallback: [Sensor.TYPE_ACCELEROMETER] + [Sensor.TYPE_MAGNETIC_FIELD] when rotation vector is unavailable.
 * 3. Graceful degradation: Emits [HeadingState.Unavailable] if required sensors are missing, without throwing.
 *
 * Jitter & Smoothing:
 * - Employs Cartesian unit-vector exponential moving average ([HeadingUtils.circularSmooth])
 * - Prevents high-frequency sensor noise while avoiding 0°/360° wrap-around discontinuity.
 *
 * @param context Application context used for accessing the sensor service.
 * @param sensorManager Injectable [SensorManager] instance (defaults to system service).
 * @param smoothingAlpha Exponential smoothing factor in `[0.0, 1.0]` (default: 0.25f for smooth responsiveness).
 * @param sensorDelay Sensor event rate (default: [SensorManager.SENSOR_DELAY_UI]).
 */
class DefaultOrientationEngine(
    context: Context,
    private val sensorManager: SensorManager? = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager,
    private val smoothingAlpha: Float = DEFAULT_SMOOTHING_ALPHA,
    private val sensorDelay: Int = SensorManager.SENSOR_DELAY_UI
) : OrientationEngine, SensorEventListener {

    private val lock = Any()
    private val _headingFlow = MutableStateFlow<HeadingState>(HeadingState.Initializing)
    override val headingFlow: StateFlow<HeadingState> = _headingFlow.asStateFlow()

    private var isRunning = false
    private var activeSensorType: OrientationSensorType? = null

    // Smoothing tracking
    private var currentSmoothedHeading: Float? = null

    // Rotation vector buffers
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Accelerometer + Magnetometer fallback buffers
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccelerometerReading = false
    private var hasMagnetometerReading = false

    override fun start() {
        synchronized(lock) {
            if (isRunning) {
                Log.d(TAG, "start ignored: OrientationEngine is already active.")
                return
            }

            val sm = sensorManager
            if (sm == null) {
                Log.w(TAG, "SensorManager service unavailable on this device.")
                _headingFlow.value = HeadingState.Unavailable("SensorManager service unavailable")
                return
            }

            // 1. Attempt primary: Rotation Vector
            val rotationVectorSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotationVectorSensor != null) {
                val registered = sm.registerListener(this, rotationVectorSensor, sensorDelay)
                if (registered) {
                    activeSensorType = OrientationSensorType.ROTATION_VECTOR
                    isRunning = true
                    currentSmoothedHeading = null
                    _headingFlow.value = HeadingState.Initializing
                    Log.d(TAG, "OrientationEngine started with primary sensor: TYPE_ROTATION_VECTOR")
                    return
                }
            }

            // 2. Attempt fallback: Accelerometer + Magnetometer
            val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accelSensor != null && magSensor != null) {
                val accelRegistered = sm.registerListener(this, accelSensor, sensorDelay)
                val magRegistered = sm.registerListener(this, magSensor, sensorDelay)
                if (accelRegistered && magRegistered) {
                    activeSensorType = OrientationSensorType.ACCELEROMETER_MAGNETOMETER
                    isRunning = true
                    hasAccelerometerReading = false
                    hasMagnetometerReading = false
                    currentSmoothedHeading = null
                    _headingFlow.value = HeadingState.Initializing
                    Log.d(TAG, "OrientationEngine started with fallback sensors: ACCELEROMETER + MAGNETOMETER")
                    return
                } else {
                    sm.unregisterListener(this)
                }
            }

            // 3. Fallback exhausted: Sensors not available
            Log.w(TAG, "Required orientation sensors (rotation vector or accelerometer+magnetometer) not available.")
            activeSensorType = null
            _headingFlow.value = HeadingState.Unavailable("Device lacks required orientation hardware sensors")
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (!isRunning) {
                Log.d(TAG, "stop ignored: OrientationEngine is not active.")
                return
            }

            sensorManager?.unregisterListener(this)
            isRunning = false
            activeSensorType = null
            currentSmoothedHeading = null
            hasAccelerometerReading = false
            hasMagnetometerReading = false

            Log.d(TAG, "OrientationEngine cleanly stopped and sensor listeners unregistered.")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        synchronized(lock) {
            if (!isRunning) return

            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    processRotationVector(event)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                    hasAccelerometerReading = true
                    if (hasMagnetometerReading) {
                        processFallbackOrientation(event.accuracy)
                    }
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                    hasMagnetometerReading = true
                    if (hasAccelerometerReading) {
                        processFallbackOrientation(event.accuracy)
                    }
                }
            }
        }
    }

    private fun processRotationVector(event: SensorEvent) {
        try {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // orientationAngles[0] is azimuth (rotation around -z axis) in radians [-PI, PI]
            val rawAzimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val normalized = HeadingUtils.normalizeHeading(rawAzimuthDegrees)

            // Apply circular smoothing to eliminate physical micro-jitter
            val smoothed = HeadingUtils.circularSmooth(
                newHeadingDegrees = normalized,
                prevHeadingDegrees = currentSmoothedHeading,
                alpha = smoothingAlpha
            )
            currentSmoothedHeading = smoothed

            val accuracy = HeadingAccuracy.fromSensorAccuracy(event.accuracy)
            _headingFlow.value = HeadingState.Active(
                headingDegrees = smoothed,
                accuracy = accuracy,
                sensorType = OrientationSensorType.ROTATION_VECTOR,
                timestampMillis = System.currentTimeMillis()
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error calculating orientation from rotation vector", t)
        }
    }

    private fun processFallbackOrientation(rawSensorAccuracy: Int) {
        try {
            val success = SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
            )

            if (success) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawAzimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val normalized = HeadingUtils.normalizeHeading(rawAzimuthDegrees)

                val smoothed = HeadingUtils.circularSmooth(
                    newHeadingDegrees = normalized,
                    prevHeadingDegrees = currentSmoothedHeading,
                    alpha = smoothingAlpha
                )
                currentSmoothedHeading = smoothed

                val accuracy = HeadingAccuracy.fromSensorAccuracy(rawSensorAccuracy)
                _headingFlow.value = HeadingState.Active(
                    headingDegrees = smoothed,
                    accuracy = accuracy,
                    sensorType = OrientationSensorType.ACCELEROMETER_MAGNETOMETER,
                    timestampMillis = System.currentTimeMillis()
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error calculating orientation from accelerometer/magnetometer fallback", t)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Handled directly during onSensorChanged
    }

    companion object {
        private const val TAG = "DefaultOrientationEngine"
        const val DEFAULT_SMOOTHING_ALPHA = 0.25f
    }
}
