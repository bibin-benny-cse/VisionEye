package com.bibin.visioneye.navigation.orientation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic JVM unit tests for [OrientationEngine] and [HeadingUtils].
 *
 * Verifies:
 * 1. Heading degree normalization across standard, negative, and wrapping boundaries.
 * 2. Smallest signed angle differences across 0°/360° North discontinuity.
 * 3. 8-sector relative direction classifications.
 * 4. Boundary precision around sector transitions.
 * 5. 8-point cardinal compass string formatting.
 * 6. Circular unit-vector smoothing behavior across the 0°/360° boundary.
 * 7. Unavailable sensor handling and start/stop idempotency.
 */
class OrientationEngineTest {

    // --- 1. Heading Normalization Tests ---

    @Test
    fun `heading normalization converts -10 to 350`() {
        assertEquals(350.0f, HeadingUtils.normalizeHeading(-10.0f), 0.001f)
    }

    @Test
    fun `heading normalization converts 0 to 0`() {
        assertEquals(0.0f, HeadingUtils.normalizeHeading(0.0f), 0.001f)
    }

    @Test
    fun `heading normalization converts 360 to 0`() {
        assertEquals(0.0f, HeadingUtils.normalizeHeading(360.0f), 0.001f)
    }

    @Test
    fun `heading normalization converts 725 to 5`() {
        assertEquals(5.0f, HeadingUtils.normalizeHeading(725.0f), 0.001f)
    }

    @Test
    fun `heading normalization edge cases`() {
        assertEquals(0.0f, HeadingUtils.normalizeHeading(-360.0f), 0.001f)
        assertEquals(0.0f, HeadingUtils.normalizeHeading(-720.0f), 0.001f)
        assertEquals(355.0f, HeadingUtils.normalizeHeading(-725.0f), 0.001f)
        assertEquals(359.9f, HeadingUtils.normalizeHeading(359.9f), 0.001f)
        assertEquals(0.0f, HeadingUtils.normalizeHeading(Float.NaN), 0.001f)
    }

    // --- 2. Smallest Angle Difference Tests ---

    @Test
    fun `smallest angle difference in standard quadrants`() {
        // Turning from North (0°) to East (90°) is +90° (clockwise / right)
        assertEquals(90.0f, HeadingUtils.smallestAngleDifference(from = 0.0f, to = 90.0f), 0.001f)

        // Turning from East (90°) to North (0°) is -90° (counter-clockwise / left)
        assertEquals(-90.0f, HeadingUtils.smallestAngleDifference(from = 90.0f, to = 0.0f), 0.001f)

        // Identical headings yield 0°
        assertEquals(0.0f, HeadingUtils.smallestAngleDifference(from = 145.0f, to = 145.0f), 0.001f)
    }

    @Test
    fun `smallest angle difference across North 0-360 boundary`() {
        // Facing 350° (10° west of North), turning to 10° (10° east of North) is +20°
        assertEquals(20.0f, HeadingUtils.smallestAngleDifference(from = 350.0f, to = 10.0f), 0.001f)

        // Facing 10° (10° east of North), turning to 350° (10° west of North) is -20°
        assertEquals(-20.0f, HeadingUtils.smallestAngleDifference(from = 10.0f, to = 350.0f), 0.001f)

        // Facing 359°, turning to 1° is +2° (not 358°)
        assertEquals(2.0f, HeadingUtils.smallestAngleDifference(from = 359.0f, to = 1.0f), 0.001f)

        // Facing 1°, turning to 359° is -2°
        assertEquals(-2.0f, HeadingUtils.smallestAngleDifference(from = 1.0f, to = 359.0f), 0.001f)
    }

    @Test
    fun `smallest angle difference opposite headings`() {
        // North to South (0° to 180°) is 180°
        assertEquals(180.0f, Math.abs(HeadingUtils.smallestAngleDifference(from = 0.0f, to = 180.0f)), 0.001f)
        assertEquals(180.0f, Math.abs(HeadingUtils.smallestAngleDifference(from = 180.0f, to = 0.0f)), 0.001f)
    }

    // --- 3. Relative Direction Classifications ---

    @Test
    fun `relative direction classifications for standard bearings`() {
        // Current heading = North (0°)
        assertEquals(RelativeDirection.FORWARD, HeadingUtils.relativeDirection(targetBearing = 0.0f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.SLIGHT_RIGHT, HeadingUtils.relativeDirection(targetBearing = 45.0f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.RIGHT, HeadingUtils.relativeDirection(targetBearing = 90.0f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.SHARP_RIGHT, HeadingUtils.relativeDirection(targetBearing = 135.0f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.BACK, HeadingUtils.relativeDirection(targetBearing = 180.0f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.SHARP_LEFT, HeadingUtils.relativeDirection(targetBearing = 225.0f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.LEFT, HeadingUtils.relativeDirection(targetBearing = 270.0f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.SLIGHT_LEFT, HeadingUtils.relativeDirection(targetBearing = 315.0f, currentHeading = 0.0f))
    }

    // --- 4. Boundary Cases Around North / 360 ---

    @Test
    fun `boundary cases around North and 360`() {
        // User facing 355°, target bearing is 5° (deviation = +10°) -> FORWARD
        assertEquals(RelativeDirection.FORWARD, HeadingUtils.relativeDirection(targetBearing = 5.0f, currentHeading = 355.0f))

        // User facing 5°, target bearing is 355° (deviation = -10°) -> FORWARD
        assertEquals(RelativeDirection.FORWARD, HeadingUtils.relativeDirection(targetBearing = 355.0f, currentHeading = 5.0f))

        // User facing 350°, target bearing is 80° (deviation = +90°) -> RIGHT
        assertEquals(RelativeDirection.RIGHT, HeadingUtils.relativeDirection(targetBearing = 80.0f, currentHeading = 350.0f))

        // User facing 10°, target bearing is 280° (deviation = -90°) -> LEFT
        assertEquals(RelativeDirection.LEFT, HeadingUtils.relativeDirection(targetBearing = 280.0f, currentHeading = 10.0f))
    }

    // --- 5. Boundary Cases Around Sector Transitions ---

    @Test
    fun `boundary cases between sector transitions`() {
        // Forward boundary is [-22.5, +22.5]
        assertEquals(RelativeDirection.FORWARD, HeadingUtils.relativeDirection(targetBearing = 22.5f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.FORWARD, HeadingUtils.relativeDirection(targetBearing = 337.5f, currentHeading = 0.0f))

        // Just past 22.5° enters SLIGHT_RIGHT
        assertEquals(RelativeDirection.SLIGHT_RIGHT, HeadingUtils.relativeDirection(targetBearing = 22.6f, currentHeading = 0.0f))

        // Right boundary: (67.5, 112.5]
        assertEquals(RelativeDirection.SLIGHT_RIGHT, HeadingUtils.relativeDirection(targetBearing = 67.5f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.RIGHT, HeadingUtils.relativeDirection(targetBearing = 67.6f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.RIGHT, HeadingUtils.relativeDirection(targetBearing = 112.5f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.SHARP_RIGHT, HeadingUtils.relativeDirection(targetBearing = 112.6f, currentHeading = 0.0f))

        // Sharp right to back: (112.5, 157.5] -> SHARP_RIGHT, > 157.5 -> BACK
        assertEquals(RelativeDirection.SHARP_RIGHT, HeadingUtils.relativeDirection(targetBearing = 157.5f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.BACK, HeadingUtils.relativeDirection(targetBearing = 157.6f, currentHeading = 0.0f))

        // Left sectors:
        assertEquals(RelativeDirection.SLIGHT_LEFT, HeadingUtils.relativeDirection(targetBearing = 337.4f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.LEFT, HeadingUtils.relativeDirection(targetBearing = 270.0f, currentHeading = 0.0f))
        assertEquals(RelativeDirection.SHARP_LEFT, HeadingUtils.relativeDirection(targetBearing = 225.0f, currentHeading = 0.0f))
    }

    // --- 6. Cardinal Direction Tests ---

    @Test
    fun `cardinal direction formatting across all quadrants`() {
        assertEquals("N", HeadingUtils.cardinalDirection(0.0f))
        assertEquals("N", HeadingUtils.cardinalDirection(350.0f))
        assertEquals("N", HeadingUtils.cardinalDirection(10.0f))
        assertEquals("NE", HeadingUtils.cardinalDirection(45.0f))
        assertEquals("E", HeadingUtils.cardinalDirection(90.0f))
        assertEquals("SE", HeadingUtils.cardinalDirection(123.0f)) // Matches requirement specification: 123° -> SE
        assertEquals("S", HeadingUtils.cardinalDirection(180.0f))
        assertEquals("SW", HeadingUtils.cardinalDirection(225.0f))
        assertEquals("W", HeadingUtils.cardinalDirection(270.0f))
        assertEquals("NW", HeadingUtils.cardinalDirection(315.0f))
    }

    // --- 7. Circular Unit-Vector Smoothing Tests ---

    @Test
    fun `circular smoothing handles 0-360 discontinuity correctly`() {
        // Initial reading is 1° (just east of North)
        val initial = HeadingUtils.circularSmooth(newHeadingDegrees = 1.0f, prevHeadingDegrees = null)
        assertEquals(1.0f, initial, 0.001f)

        // New reading jumps across North to 359° (just west of North).
        // Standard arithmetic averaging would yield 180° (South) - which is completely wrong.
        // Circular unit-vector smoothing correctly interpolates around North:
        val smoothed = HeadingUtils.circularSmooth(newHeadingDegrees = 359.0f, prevHeadingDegrees = 1.0f, alpha = 0.5f)
        assertEquals(0.0f, smoothed, 0.5f) // Correctly centered at North (0°)
    }

    // --- 8. Hardware-Independent Engine Tests ---

    @Test
    fun `unavailable sensor state is emitted and start stop is idempotent`() {
        val engine = FakeOrientationEngine(isSensorAvailable = false)

        assertFalse(engine.headingFlow.value.isAvailable)

        engine.start()
        assertTrue(engine.headingFlow.value is HeadingState.Unavailable)
        assertEquals("Orientation sensors unavailable", (engine.headingFlow.value as HeadingState.Unavailable).reason)
        assertEquals(1, engine.startCount)

        // Idempotency check: repeated start does not duplicate state
        engine.start()
        assertEquals(1, engine.startCount)

        // Idempotency check: repeated stop does not duplicate state
        engine.stop()
        assertEquals(1, engine.stopCount)
        engine.stop()
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun `active sensor updates are emitted when hardware is available`() {
        val engine = FakeOrientationEngine(isSensorAvailable = true)

        engine.start()
        assertTrue(engine.headingFlow.value is HeadingState.Initializing)

        engine.emitHeading(123.0f)
        val state = engine.headingFlow.value
        assertTrue(state is HeadingState.Active)
        assertEquals(123.0f, (state as HeadingState.Active).headingDegrees, 0.01f)
        assertEquals("SE", state.cardinalDirection)
        assertTrue(state.isAvailable)
    }

    /**
     * Hardware-independent fake implementation of [OrientationEngine] for testing.
     */
    class FakeOrientationEngine(
        private var isSensorAvailable: Boolean = true
    ) : OrientationEngine {

        private val _headingFlow = MutableStateFlow<HeadingState>(HeadingState.Initializing)
        override val headingFlow: StateFlow<HeadingState> = _headingFlow.asStateFlow()

        var isRunning: Boolean = false
            private set
        var startCount: Int = 0
            private set
        var stopCount: Int = 0
        private var isStarted = false

        fun setAvailable(available: Boolean) {
            isSensorAvailable = available
        }

        fun emitHeading(degrees: Float) {
            if (isRunning && isSensorAvailable) {
                _headingFlow.value = HeadingState.Active(
                    headingDegrees = HeadingUtils.normalizeHeading(degrees),
                    cardinalDirection = HeadingUtils.cardinalDirection(degrees)
                )
            }
        }

        override fun start() {
            if (isStarted) return
            isStarted = true
            startCount++
            if (!isSensorAvailable) {
                _headingFlow.value = HeadingState.Unavailable("Orientation sensors unavailable")
                return
            }
            isRunning = true
            _headingFlow.value = HeadingState.Initializing
        }

        override fun stop() {
            if (!isStarted) return
            isStarted = false
            stopCount++
            isRunning = false
            _headingFlow.value = HeadingState.Initializing
        }
    }
}
