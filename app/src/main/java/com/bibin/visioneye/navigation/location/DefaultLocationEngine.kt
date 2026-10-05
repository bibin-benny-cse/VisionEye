package com.bibin.visioneye.navigation.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production implementation of [LocationEngine] using Google Play Services [FusedLocationProviderClient].
 *
 * Configured specifically for pedestrian navigation assistance:
 * - High-accuracy positioning (GPS + Wi-Fi / Cell triangulation)
 * - 3.0 second target update interval (balanced for walking pace ~1.4 m/s and battery longevity)
 * - 1.5 second fastest minimum update interval
 * - 1.0 meter minimum displacement threshold
 * - Complete lifecycle safety, leak prevention, and graceful permission degradation
 *
 * @param context Application context used for checking permissions and binding location services.
 * @param fusedLocationClient Fused location provider client instance (injected or default).
 * @param intervalMillis Target update interval in milliseconds (default: 3000ms).
 * @param minUpdateIntervalMillis Minimum update interval in milliseconds (default: 1500ms).
 * @param minUpdateDistanceMeters Minimum displacement in meters required for an update (default: 1.0m).
 * @param looper Looper for location callback execution (default: MainLooper).
 */
class DefaultLocationEngine(
    context: Context,
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context.applicationContext),
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val minUpdateIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val minUpdateDistanceMeters: Float = DEFAULT_MIN_DISTANCE_METERS,
    private val looper: Looper = Looper.getMainLooper()
) : LocationEngine {

    private val appContext: Context = context.applicationContext
    private val lock = Any()

    private val _locationFlow = MutableStateFlow<LocationFix?>(null)
    override val locationFlow: StateFlow<LocationFix?> = _locationFlow.asStateFlow()

    private var isRequestingUpdates = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val lastLocation = result.lastLocation ?: return
            val fix = fromAndroidLocation(lastLocation)
            if (fix != null) {
                _locationFlow.value = fix
            } else {
                Log.w(TAG, "Dropped invalid location update: lat=${lastLocation.latitude}, lon=${lastLocation.longitude}")
            }
        }
    }

    override fun hasPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    override fun startLocationUpdates() {
        synchronized(lock) {
            if (isRequestingUpdates) {
                Log.d(TAG, "startLocationUpdates ignored: updates already active.")
                return
            }

            if (!hasPermission()) {
                Log.w(TAG, "Cannot start location updates: location permissions are not granted.")
                return
            }

            try {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                    .setMinUpdateIntervalMillis(minUpdateIntervalMillis)
                    .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
                    .setWaitForAccurateLocation(false)
                    .build()

                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, looper)
                    .addOnSuccessListener {
                        Log.d(TAG, "Location updates successfully registered with FusedLocationProviderClient.")
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to register location updates with FusedLocationProviderClient", exception)
                    }

                // Attempt to seed initial position from last known location if available
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null && _locationFlow.value == null) {
                            fromAndroidLocation(location)?.let { initialFix ->
                                _locationFlow.value = initialFix
                                Log.d(TAG, "Initial position seeded from last known location: $initialFix")
                            }
                        }
                    }
                } catch (se: SecurityException) {
                    Log.w(TAG, "SecurityException while accessing last known location", se)
                }

                isRequestingUpdates = true
            } catch (se: SecurityException) {
                Log.w(TAG, "SecurityException while requesting location updates", se)
                isRequestingUpdates = false
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error starting location updates", t)
                isRequestingUpdates = false
            }
        }
    }

    override fun stopLocationUpdates() {
        synchronized(lock) {
            if (!isRequestingUpdates) {
                Log.d(TAG, "stopLocationUpdates ignored: updates not currently active.")
                return
            }

            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                    .addOnCompleteListener {
                        Log.d(TAG, "Location callback cleanly removed from FusedLocationProviderClient.")
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "Error removing location callback from FusedLocationProviderClient", t)
            } finally {
                isRequestingUpdates = false
            }
        }
    }

    companion object {
        private const val TAG = "DefaultLocationEngine"

        const val DEFAULT_INTERVAL_MILLIS = 3000L
        const val DEFAULT_MIN_INTERVAL_MILLIS = 1500L
        const val DEFAULT_MIN_DISTANCE_METERS = 1.0f

        const val MIN_SPEED_FOR_BEARING_METERS_PER_SECOND = 0.5f // ~1.8 km/h walking threshold

        /**
         * Converts an Android [Location] instance into a validated [LocationFix],
         * or returns null if the coordinates or metrics are invalid.
         */
        fun fromAndroidLocation(location: Location?): LocationFix? {
            if (location == null) return null
            val lat = location.latitude
            val lon = location.longitude

            // If location provider does not report accuracy, assume high uncertainty (Float.MAX_VALUE)
            // rather than 0.0f (which falsely represents infinite precision)
            val acc = if (location.hasAccuracy() && location.accuracy >= 0.0f && !location.accuracy.isNaN()) {
                location.accuracy
            } else {
                Float.MAX_VALUE
            }

            val speed = if (location.hasSpeed() && location.speed >= 0.0f && !location.speed.isNaN()) {
                location.speed
            } else {
                null
            }

            // GPS course/bearing is physically meaningful only when the device is in motion.
            // When stationary (speed < 0.5 m/s) or speed is unavailable, GPS bearing is pure noise.
            val bearing = if (location.hasBearing() &&
                speed != null &&
                speed >= MIN_SPEED_FOR_BEARING_METERS_PER_SECOND &&
                !location.bearing.isNaN()
            ) {
                com.bibin.visioneye.navigation.orientation.HeadingUtils.normalizeHeading(location.bearing)
            } else {
                null
            }

            val time = location.time

            return toLocationFix(
                latitude = lat,
                longitude = lon,
                accuracyMeters = acc,
                bearingDegrees = bearing,
                speedMetersPerSecond = speed,
                timestampMillis = if (time > 0L) time else System.currentTimeMillis()
            )
        }

        /**
         * Validates raw coordinate values and constructs a [LocationFix],
         * returning null if values are mathematically or physically invalid.
         */
        fun toLocationFix(
            latitude: Double,
            longitude: Double,
            accuracyMeters: Float,
            bearingDegrees: Float? = null,
            speedMetersPerSecond: Float? = null,
            timestampMillis: Long = System.currentTimeMillis()
        ): LocationFix? {
            if (!LocationFix.isValidLatitude(latitude) ||
                !LocationFix.isValidLongitude(longitude) ||
                !LocationFix.isValidAccuracy(accuracyMeters)
            ) {
                return null
            }
            return try {
                LocationFix(
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = accuracyMeters,
                    bearingDegrees = bearingDegrees,
                    speedMetersPerSecond = speedMetersPerSecond,
                    timestampMillis = timestampMillis
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
