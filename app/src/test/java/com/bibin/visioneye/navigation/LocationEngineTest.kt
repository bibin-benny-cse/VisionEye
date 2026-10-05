package com.bibin.visioneye.navigation

import com.bibin.visioneye.navigation.location.DefaultLocationEngine
import com.bibin.visioneye.navigation.location.LocationEngine
import com.bibin.visioneye.navigation.location.LocationFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Deterministic JVM unit tests for VisionEye GPS Foundation & Location Infrastructure (Phase 1).
 *
 * Verifies:
 * 1. Coordinate and accuracy validation rules in [LocationFix].
 * 2. Field preservation (bearing, speed, timestamp).
 * 3. Haversine distance calculations for known coordinate pairs.
 * 4. Conversion and safe rejection utilities in [DefaultLocationEngine].
 * 5. Safe lifecycle semantics and permission handling in [LocationEngine].
 * 6. NavigationState.AcquiringGps diagnostic representations.
 */
class LocationEngineTest {

    // --- 1. Coordinate Validation Tests ---

    @Test
    fun `valid latitude and longitude are accepted`() {
        val fix = LocationFix(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 4.5f
        )
        assertEquals(12.9716, fix.latitude, 0.00001)
        assertEquals(77.5946, fix.longitude, 0.00001)
        assertEquals(4.5f, fix.accuracyMeters, 0.01f)
    }

    @Test
    fun `boundary latitudes are accepted`() {
        val northPole = LocationFix(latitude = 90.0, longitude = 0.0, accuracyMeters = 1.0f)
        val southPole = LocationFix(latitude = -90.0, longitude = 0.0, accuracyMeters = 1.0f)
        assertEquals(90.0, northPole.latitude, 0.00001)
        assertEquals(-90.0, southPole.latitude, 0.00001)
    }

    @Test
    fun `boundary longitudes are accepted`() {
        val dateLineEast = LocationFix(latitude = 0.0, longitude = 180.0, accuracyMeters = 1.0f)
        val dateLineWest = LocationFix(latitude = 0.0, longitude = -180.0, accuracyMeters = 1.0f)
        assertEquals(180.0, dateLineEast.longitude, 0.00001)
        assertEquals(-180.0, dateLineWest.longitude, 0.00001)
    }

    @Test
    fun `invalid latitude above 90 is rejected`() {
        try {
            LocationFix(latitude = 90.0001, longitude = 0.0, accuracyMeters = 5.0f)
            fail("Expected IllegalArgumentException for latitude > 90.0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Latitude must be between -90.0 and 90.0") == true)
        }
    }

    @Test
    fun `invalid latitude below minus 90 is rejected`() {
        try {
            LocationFix(latitude = -90.0001, longitude = 0.0, accuracyMeters = 5.0f)
            fail("Expected IllegalArgumentException for latitude < -90.0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Latitude must be between -90.0 and 90.0") == true)
        }
    }

    @Test
    fun `invalid longitude above 180 is rejected`() {
        try {
            LocationFix(latitude = 0.0, longitude = 180.0001, accuracyMeters = 5.0f)
            fail("Expected IllegalArgumentException for longitude > 180.0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Longitude must be between -180.0 and 180.0") == true)
        }
    }

    @Test
    fun `invalid longitude below minus 180 is rejected`() {
        try {
            LocationFix(latitude = 0.0, longitude = -180.0001, accuracyMeters = 5.0f)
            fail("Expected IllegalArgumentException for longitude < -180.0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Longitude must be between -180.0 and 180.0") == true)
        }
    }

    @Test
    fun `negative accuracy is rejected`() {
        try {
            LocationFix(latitude = 10.0, longitude = 20.0, accuracyMeters = -0.5f)
            fail("Expected IllegalArgumentException for negative accuracy")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Accuracy must not be negative") == true)
        }
    }

    @Test
    fun `zero accuracy is accepted`() {
        val fix = LocationFix(latitude = 10.0, longitude = 20.0, accuracyMeters = 0.0f)
        assertEquals(0.0f, fix.accuracyMeters, 0.0001f)
    }

    // --- 2. Metric Preservation Tests ---

    @Test
    fun `LocationFix preserves bearing when present`() {
        val fixWithBearing = LocationFix(
            latitude = 12.0,
            longitude = 77.0,
            accuracyMeters = 5.0f,
            bearingDegrees = 184.5f
        )
        assertNotNull(fixWithBearing.bearingDegrees)
        assertEquals(184.5f, fixWithBearing.bearingDegrees!!, 0.01f)

        val fixWithoutBearing = LocationFix(
            latitude = 12.0,
            longitude = 77.0,
            accuracyMeters = 5.0f,
            bearingDegrees = null
        )
        assertNull(fixWithoutBearing.bearingDegrees)
    }

    @Test
    fun `LocationFix preserves speed when present`() {
        val fixWithSpeed = LocationFix(
            latitude = 12.0,
            longitude = 77.0,
            accuracyMeters = 5.0f,
            speedMetersPerSecond = 1.35f
        )
        assertNotNull(fixWithSpeed.speedMetersPerSecond)
        assertEquals(1.35f, fixWithSpeed.speedMetersPerSecond!!, 0.01f)

        val fixWithoutSpeed = LocationFix(
            latitude = 12.0,
            longitude = 77.0,
            accuracyMeters = 5.0f,
            speedMetersPerSecond = null
        )
        assertNull(fixWithoutSpeed.speedMetersPerSecond)
    }

    @Test
    fun `LocationFix preserves timestamp`() {
        val timestamp = 1700000000000L
        val fix = LocationFix(
            latitude = 12.0,
            longitude = 77.0,
            accuracyMeters = 5.0f,
            timestampMillis = timestamp
        )
        assertEquals(timestamp, fix.timestampMillis)
    }

    // --- 3. Haversine Distance Calculation Tests ---

    @Test
    fun `Haversine distance between identical coordinates is zero`() {
        val p1 = LocationFix(latitude = 37.7749, longitude = -122.4194, accuracyMeters = 3.0f)
        val p2 = LocationFix(latitude = 37.7749, longitude = -122.4194, accuracyMeters = 3.0f)
        assertEquals(0.0, p1.distanceTo(p2), 0.001)
    }

    @Test
    fun `Haversine distance along equator for 1 degree longitude is approx 111 km`() {
        val p1 = LocationFix(latitude = 0.0, longitude = 0.0, accuracyMeters = 5.0f)
        val p2 = LocationFix(latitude = 0.0, longitude = 1.0, accuracyMeters = 5.0f)
        val distanceMeters = p1.distanceTo(p2)
        // 1 degree of longitude at the equator is approx 111,195 meters
        assertEquals(111195.0, distanceMeters, 200.0)
    }

    @Test
    fun `Haversine distance for known pedestrian walking pair`() {
        // Points ~100.3 meters apart along a meridian (dLat = 0.000902 deg)
        val start = LocationFix(latitude = 12.971598, longitude = 77.594562, accuracyMeters = 2.0f)
        val end = LocationFix(latitude = 12.972500, longitude = 77.594562, accuracyMeters = 2.0f)
        val distance = start.distanceTo(end)
        assertEquals(100.3, distance, 2.0)
    }

    // --- 4. Conversion & Rejection Utility Tests ---

    @Test
    fun `DefaultLocationEngine toLocationFix returns valid fix for good coordinates`() {
        val fix = DefaultLocationEngine.toLocationFix(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracyMeters = 6.2f,
            bearingDegrees = 90.0f,
            speedMetersPerSecond = 1.4f,
            timestampMillis = 1000L
        )
        assertNotNull(fix)
        assertEquals(28.6139, fix!!.latitude, 0.0001)
        assertEquals(77.2090, fix.longitude, 0.0001)
        assertEquals(6.2f, fix.accuracyMeters, 0.01f)
        assertEquals(90.0f, fix.bearingDegrees!!, 0.01f)
        assertEquals(1.4f, fix.speedMetersPerSecond!!, 0.01f)
    }

    @Test
    fun `DefaultLocationEngine toLocationFix returns null without throwing for bad coordinates`() {
        assertNull(DefaultLocationEngine.toLocationFix(latitude = 120.0, longitude = 0.0, accuracyMeters = 5.0f))
        assertNull(DefaultLocationEngine.toLocationFix(latitude = 0.0, longitude = 200.0, accuracyMeters = 5.0f))
        assertNull(DefaultLocationEngine.toLocationFix(latitude = 0.0, longitude = 0.0, accuracyMeters = -1.0f))
        assertNull(DefaultLocationEngine.toLocationFix(latitude = Double.NaN, longitude = 0.0, accuracyMeters = 5.0f))
    }

    @Test
    fun `fromAndroidLocation stationary speed under 0_5 ms produces null bearing`() {
        val stationaryLocation = object : android.location.Location("gps") {
            override fun getLatitude() = 12.0
            override fun getLongitude() = 77.0
            override fun hasAccuracy() = true
            override fun getAccuracy() = 3.0f
            override fun hasSpeed() = true
            override fun getSpeed() = 0.2f // Stationary drift < 0.5 m/s
            override fun hasBearing() = true
            override fun getBearing() = 180.0f
            override fun getTime() = 1000L
        }

        val fix = DefaultLocationEngine.fromAndroidLocation(stationaryLocation)
        assertNotNull(fix)
        assertEquals(0.2f, fix!!.speedMetersPerSecond!!, 0.01f)
        assertNull("Stationary GPS bearing must be gated to null", fix.bearingDegrees)
    }

    @Test
    fun `fromAndroidLocation moving speed over 0_5 ms preserves normalized bearing`() {
        val movingLocation = object : android.location.Location("gps") {
            override fun getLatitude() = 12.0
            override fun getLongitude() = 77.0
            override fun hasAccuracy() = true
            override fun getAccuracy() = 3.0f
            override fun hasSpeed() = true
            override fun getSpeed() = 1.4f // Walking pace ~1.4 m/s
            override fun hasBearing() = true
            override fun getBearing() = 360.0f // Boundary bearing
            override fun getTime() = 1000L
        }

        val fix = DefaultLocationEngine.fromAndroidLocation(movingLocation)
        assertNotNull(fix)
        assertEquals(1.4f, fix!!.speedMetersPerSecond!!, 0.01f)
        assertNotNull(fix.bearingDegrees)
        assertEquals(0.0f, fix.bearingDegrees!!, 0.01f) // Normalized to 0.0f
    }

    @Test
    fun `fromAndroidLocation missing accuracy defaults to Float MAX_VALUE`() {
        val noAccuracyLocation = object : android.location.Location("gps") {
            override fun getLatitude() = 12.0
            override fun getLongitude() = 77.0
            override fun hasAccuracy() = false
            override fun getTime() = 1000L
        }

        val fix = DefaultLocationEngine.fromAndroidLocation(noAccuracyLocation)
        assertNotNull(fix)
        assertEquals(Float.MAX_VALUE, fix!!.accuracyMeters, 0.01f)
    }

    // --- 5. Lifecycle Semantics & Permission Handling Tests ---

    @Test
    fun `LocationEngine safely handles missing permission`() {
        val engine = FakeLocationEngine(permissionGranted = false)
        assertFalse(engine.hasPermission())

        // Starting updates when permission is missing should not crash and should not activate tracking
        engine.startLocationUpdates()
        assertFalse(engine.isTrackingActive)
        assertNull(engine.locationFlow.value)
    }

    @Test
    fun `LocationEngine start and stop updates are idempotent`() {
        val engine = FakeLocationEngine(permissionGranted = true)
        assertTrue(engine.hasPermission())

        // Multiple starts do not duplicate state
        engine.startLocationUpdates()
        assertTrue(engine.isTrackingActive)
        assertEquals(1, engine.startInvocationCount)

        engine.startLocationUpdates()
        assertEquals(1, engine.startInvocationCount) // Idempotent: ignored
        assertTrue(engine.isTrackingActive)

        // Multiple stops do not duplicate state
        engine.stopLocationUpdates()
        assertFalse(engine.isTrackingActive)
        assertEquals(1, engine.stopInvocationCount)

        engine.stopLocationUpdates()
        assertEquals(1, engine.stopInvocationCount) // Idempotent: ignored
        assertFalse(engine.isTrackingActive)
    }

    @Test
    fun `LocationEngine emits location fixes to flow only when active`() {
        val engine = FakeLocationEngine(permissionGranted = true)
        val fix = LocationFix(12.0, 77.0, 3.0f)

        // Fix emitted before start is not accepted
        engine.simulateLocation(fix)
        assertNull(engine.locationFlow.value)

        // After start, updates are emitted
        engine.startLocationUpdates()
        engine.simulateLocation(fix)
        assertEquals(fix, engine.locationFlow.value)

        // After stop, new fixes are not emitted
        engine.stopLocationUpdates()
        val nextFix = LocationFix(13.0, 78.0, 2.0f)
        engine.simulateLocation(nextFix)
        assertEquals(fix, engine.locationFlow.value) // Still holds previous fix
    }

    // --- 6. NavigationState Diagnostic Tests ---

    @Test
    fun `NavigationState AcquiringGps defaults preserve compatibility`() {
        val state = NavigationState.AcquiringGps()
        assertTrue(state.hasPermission)
        assertNull(state.accuracyMeters)
    }

    @Test
    fun `NavigationState AcquiringGps represents permission missing and accuracy diagnostics`() {
        val noPerm = NavigationState.AcquiringGps(hasPermission = false)
        assertFalse(noPerm.hasPermission)
        assertNull(noPerm.accuracyMeters)

        val acquired = NavigationState.AcquiringGps(hasPermission = true, accuracyMeters = 3.5f)
        assertTrue(acquired.hasPermission)
        assertEquals(3.5f, acquired.accuracyMeters!!, 0.01f)
    }

    /**
     * Test fake implementation of [LocationEngine] for hardware-independent unit testing.
     */
    private class FakeLocationEngine(
        private var permissionGranted: Boolean = true
    ) : LocationEngine {

        private val _locationFlow = MutableStateFlow<LocationFix?>(null)
        override val locationFlow: StateFlow<LocationFix?> = _locationFlow.asStateFlow()

        var isTrackingActive: Boolean = false
            private set
        var startInvocationCount: Int = 0
            private set
        var stopInvocationCount: Int = 0
            private set

        override fun hasPermission(): Boolean = permissionGranted

        override fun startLocationUpdates() {
            if (isTrackingActive) return
            startInvocationCount++
            if (!hasPermission()) return
            isTrackingActive = true
        }

        override fun stopLocationUpdates() {
            if (!isTrackingActive) return
            stopInvocationCount++
            isTrackingActive = false
        }

        fun simulateLocation(fix: LocationFix) {
            if (isTrackingActive && permissionGranted) {
                _locationFlow.value = fix
            }
        }
    }
}
