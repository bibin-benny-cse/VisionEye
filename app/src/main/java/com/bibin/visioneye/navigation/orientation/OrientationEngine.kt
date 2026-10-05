package com.bibin.visioneye.navigation.orientation

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Accuracy level reported by hardware orientation sensors.
 */
enum class HeadingAccuracy {
    HIGH,
    MEDIUM,
    LOW,
    UNRELIABLE;

    companion object {
        fun fromSensorAccuracy(accuracy: Int): HeadingAccuracy {
            return when (accuracy) {
                3 -> HIGH // SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                2 -> MEDIUM // SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                1 -> LOW // SensorManager.SENSOR_STATUS_ACCURACY_LOW
                else -> UNRELIABLE
            }
        }
    }
}

/**
 * Hardware sensor type driving device orientation.
 */
enum class OrientationSensorType {
    ROTATION_VECTOR,
    ACCELEROMETER_MAGNETOMETER,
    SIMULATED
}

/**
 * Operational diagnostic status of the compass engine.
 */
enum class CompassStatus {
    INITIALIZING,
    ACTIVE,
    UNAVAILABLE
}

/**
 * Relative direction classifications indicating where a target bearing lies
 * relative to the user's current device heading.
 */
enum class RelativeDirection {
    FORWARD,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    BACK,
    SHARP_LEFT,
    LEFT,
    SLIGHT_LEFT
}

/**
 * State representing physical device compass heading and sensor availability.
 */
sealed interface HeadingState {
    val headingDegrees: Float?
        get() = null
    val isAvailable: Boolean
        get() = false

    /**
     * Sensors are not present on the hardware or permission was denied.
     */
    data class Unavailable(val reason: String = "Orientation sensors unavailable") : HeadingState

    /**
     * Sensors are actively registered and awaiting initial orientation measurements.
     */
    data object Initializing : HeadingState

    /**
     * Active heading measurement successfully acquired and smoothed.
     *
     * @property headingDegrees Azimuth in degrees [0.0f, 360.0f), where 0°=North, 90°=East, 180°=South, 270°=West.
     * @property cardinalDirection 8-point compass quadrant string ("N", "NE", "E", "SE", "S", "SW", "W", "NW").
     * @property accuracy Hardware sensor accuracy status.
     * @property sensorType Hardware sensor pipeline currently producing readings.
     * @property timestampMillis Epoch timestamp when this heading measurement was calculated.
     */
    data class Active(
        override val headingDegrees: Float,
        val cardinalDirection: String = HeadingUtils.cardinalDirection(headingDegrees),
        val accuracy: HeadingAccuracy = HeadingAccuracy.HIGH,
        val sensorType: OrientationSensorType = OrientationSensorType.ROTATION_VECTOR,
        val timestampMillis: Long = System.currentTimeMillis()
    ) : HeadingState {
        override val isAvailable: Boolean get() = true
    }
}

/**
 * Architectural contract for physical phone orientation and compass heading.
 *
 * Decoupled from GPS bearing: Represents the user's phone pointing direction in the horizontal plane.
 */
interface OrientationEngine {
    /**
     * Observable stream emitting real-time device [HeadingState].
     */
    val headingFlow: StateFlow<HeadingState>

    /**
     * Starts listening to orientation sensors.
     * Idempotent: safe to invoke repeatedly.
     */
    fun start()

    /**
     * Halts sensor observation and releases hardware listeners.
     * Idempotent: safe to invoke repeatedly.
     */
    fun stop()
}

/**
 * Pure Kotlin mathematical utilities for compass heading calculations and relative directions.
 */
object HeadingUtils {

    /**
     * Normalizes an angular heading in degrees into the half-open interval `[0.0f, 360.0f)`.
     *
     * Examples:
     * - `-10f` -> `350.0f`
     * - `0f` -> `0.0f`
     * - `360f` -> `0.0f`
     * - `725f` -> `5.0f`
     */
    fun normalizeHeading(degrees: Float): Float {
        if (degrees.isNaN() || degrees.isInfinite()) return 0.0f
        var normalized = degrees % 360.0f
        if (normalized < 0.0f) {
            normalized += 360.0f
        }
        if (normalized >= 360.0f || normalized == -0.0f) {
            normalized = 0.0f
        }
        return normalized
    }

    /**
     * Computes the smallest signed angular difference from [from] heading to [to] heading in degrees.
     *
     * Result is in `[-180.0f, 180.0f]`:
     * - Positive value: Clockwise turn (turn right).
     * - Negative value: Counter-clockwise turn (turn left).
     *
     * Handles wrap-around seamlessly across the North (0°/360°) boundary.
     * Examples:
     * - `from=350f, to=10f` -> `+20.0f` (right 20°)
     * - `from=10f, to=350f` -> `-20.0f` (left 20°)
     */
    fun smallestAngleDifference(from: Float, to: Float): Float {
        val nFrom = normalizeHeading(from)
        val nTo = normalizeHeading(to)
        val diff = (nTo - nFrom) % 360.0f
        val normalizedDiff = (diff + 360.0f) % 360.0f
        return if (normalizedDiff > 180.0f) {
            normalizedDiff - 360.0f
        } else {
            normalizedDiff
        }
    }

    /**
     * Determines the relative direction of [targetBearing] with respect to [currentHeading].
     *
     * Divides 360° into 8 continuous sectors of 45°:
     * - `FORWARD`: `[-22.5°, +22.5°]`
     * - `SLIGHT_RIGHT`: `(+22.5°, +67.5°]`
     * - `RIGHT`: `(+67.5°, +112.5°]`
     * - `SHARP_RIGHT`: `(+112.5°, +157.5°]`
     * - `BACK`: `> +157.5°` or `< -157.5°`
     * - `SHARP_LEFT`: `[-157.5°, -112.5°)`
     * - `LEFT`: `[-112.5°, -67.5°)`
     * - `SLIGHT_LEFT`: `[-67.5°, -22.5°)`
     *
     * @param targetBearing Target direction of travel in degrees `[0, 360)`.
     * @param currentHeading Current physical heading in degrees `[0, 360)`.
     */
    fun relativeDirection(targetBearing: Float, currentHeading: Float): RelativeDirection {
        val diff = smallestAngleDifference(from = currentHeading, to = targetBearing)
        return when {
            diff in -22.5f..22.5f -> RelativeDirection.FORWARD
            diff > 22.5f && diff <= 67.5f -> RelativeDirection.SLIGHT_RIGHT
            diff > 67.5f && diff <= 112.5f -> RelativeDirection.RIGHT
            diff > 112.5f && diff <= 157.5f -> RelativeDirection.SHARP_RIGHT
            diff < -22.5f && diff >= -67.5f -> RelativeDirection.SLIGHT_LEFT
            diff < -67.5f && diff >= -112.5f -> RelativeDirection.LEFT
            diff < -112.5f && diff >= -157.5f -> RelativeDirection.SHARP_LEFT
            else -> RelativeDirection.BACK
        }
    }

    /**
     * Maps an azimuth in degrees into an 8-point compass quadrant string.
     *
     * Quadrant bounds:
     * - `N`:  [337.5°, 360.0) or [0.0°, 22.5°)
     * - `NE`: [22.5°, 67.5°)
     * - `E`:  [67.5°, 112.5°)
     * - `SE`: [112.5°, 157.5°)
     * - `S`:  [157.5°, 202.5°)
     * - `SW`: [202.5°, 247.5°)
     * - `W`:  [247.5°, 292.5°)
     * - `NW`: [292.5°, 337.5°)
     */
    fun cardinalDirection(headingDegrees: Float): String {
        val norm = normalizeHeading(headingDegrees)
        return when {
            norm >= 337.5f || norm < 22.5f -> "N"
            norm < 67.5f -> "NE"
            norm < 112.5f -> "E"
            norm < 157.5f -> "SE"
            norm < 202.5f -> "S"
            norm < 247.5f -> "SW"
            norm < 292.5f -> "W"
            else -> "NW"
        }
    }

    /**
     * Circular low-pass smoothing for angular values to prevent jitter and 0°/360° discontinuity wrap-around.
     *
     * Uses Cartesian unit vector components (sin and cos) with exponential moving average:
     * `smoothed = atan2(alpha * sin(new) + (1 - alpha) * sin(prev), alpha * cos(new) + (1 - alpha) * cos(prev))`
     *
     * @param newHeadingDegrees Raw input heading in degrees.
     * @param prevHeadingDegrees Previous smoothed heading in degrees (or null if first reading).
     * @param alpha Smoothing factor `[0.0, 1.0]`, where 1.0 = raw (no smoothing) and 0.2 = strong smoothing.
     * @return Smoothed normalized heading in degrees.
     */
    fun circularSmooth(
        newHeadingDegrees: Float,
        prevHeadingDegrees: Float?,
        alpha: Float = 0.25f
    ): Float {
        if (prevHeadingDegrees == null) {
            return normalizeHeading(newHeadingDegrees)
        }

        val newRad = Math.toRadians(newHeadingDegrees.toDouble())
        val prevRad = Math.toRadians(prevHeadingDegrees.toDouble())

        val newSin = sin(newRad)
        val newCos = cos(newRad)
        val prevSin = sin(prevRad)
        val prevCos = cos(prevRad)

        val smoothedSin = alpha * newSin + (1.0f - alpha) * prevSin
        val smoothedCos = alpha * newCos + (1.0f - alpha) * prevCos

        val smoothedRad = atan2(smoothedSin, smoothedCos)
        return normalizeHeading(Math.toDegrees(smoothedRad).toFloat())
    }
}
