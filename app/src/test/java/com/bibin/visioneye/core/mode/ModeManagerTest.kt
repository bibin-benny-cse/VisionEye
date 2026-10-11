package com.bibin.visioneye.core.mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModeManagerTest {

    private lateinit var modeManager: ModeManager

    @Before
    fun setup() {
        modeManager = DefaultModeManager()
    }

    @Test
    fun defaultMode_isNavigate() {
        assertEquals(VisionMode.NAVIGATE, modeManager.getMode())
        assertEquals(VisionMode.NAVIGATE, modeManager.currentMode.value)
    }

    @Test
    fun allExpectedModesExist() {
        val expectedModes = setOf("NAVIGATE", "READ", "CURRENCY", "PEOPLE", "NAVIGATION", "SOS")
        val actualModes = VisionMode.entries.map { it.name }.toSet()
        assertEquals(expectedModes, actualModes)
    }

    @Test
    fun setMode_switchesModeSuccessfully() {
        val result = modeManager.setMode(VisionMode.READ)
        assertTrue(result)
        assertEquals(VisionMode.READ, modeManager.getMode())
        assertEquals(VisionMode.READ, modeManager.currentMode.value)
    }

    @Test
    fun setMode_toSameMode_returnsFalse() {
        val initialResult = modeManager.setMode(VisionMode.PEOPLE)
        assertTrue(initialResult)

        // Attempting to switch to the already active mode should return false
        val redundantResult = modeManager.setMode(VisionMode.PEOPLE)
        assertFalse(redundantResult)
        assertEquals(VisionMode.PEOPLE, modeManager.getMode())
    }

    @Test
    fun listener_notifiedOnModeChange() {
        var observedNewMode: VisionMode? = null
        var observedOldMode: VisionMode? = null

        val listener = ModeChangeListener { newMode, previousMode ->
            observedNewMode = newMode
            observedOldMode = previousMode
        }

        modeManager.addListener(listener)
        modeManager.setMode(VisionMode.PEOPLE)

        assertEquals(VisionMode.PEOPLE, observedNewMode)
        assertEquals(VisionMode.NAVIGATE, observedOldMode)

        // Test unregistering listener
        modeManager.removeListener(listener)
        modeManager.setMode(VisionMode.SOS)
        // Listener should not have updated to SOS
        assertEquals(VisionMode.PEOPLE, observedNewMode)
    }

    @Test
    fun architectureRule_modelRequirementFlags() {
        // NAVIGATE requires camera and real-time obstacle AI
        assertTrue(VisionMode.NAVIGATE.requiresCamera)
        assertTrue(VisionMode.NAVIGATE.requiresHighFrequencyAi)
        assertFalse(VisionMode.NAVIGATE.requiresLocation)

        // READ requires camera for OCR, but not location or high frequency AI
        assertTrue(VisionMode.READ.requiresCamera)
        assertFalse(VisionMode.READ.requiresHighFrequencyAi)

        // CURRENCY requires camera and high frequency AI, but not location
        assertTrue(VisionMode.CURRENCY.requiresCamera)
        assertTrue(VisionMode.CURRENCY.requiresHighFrequencyAi)
        assertFalse(VisionMode.CURRENCY.requiresLocation)

        // NAVIGATION requires GPS, not camera
        assertTrue(VisionMode.NAVIGATION.requiresLocation)
        assertFalse(VisionMode.NAVIGATION.requiresCamera)

        // SOS requires location, not camera
        assertTrue(VisionMode.SOS.requiresLocation)
        assertFalse(VisionMode.SOS.requiresCamera)
    }

    @Test
    fun modeEntryAnnouncements_areConsistentConciseAndUniqueAcrossAllModes() {
        val announcements = VisionMode.entries.map { it.announcement }

        // All 6 modes must have announcements
        assertEquals(6, announcements.size)

        // Announcements must be unique across modes
        val uniqueAnnouncements = announcements.toSet()
        assertEquals(6, uniqueAnnouncements.size)

        // Every mode announcement must state the active mode, purpose, and guidance
        VisionMode.entries.forEach { mode ->
            val text = mode.announcement
            assertTrue("Mode ${mode.name} announcement must not be blank", text.isNotBlank())
            assertTrue(
                "Mode ${mode.name} announcement must state mode name",
                text.contains(mode.displayName, ignoreCase = true) || text.contains(mode.name, ignoreCase = true)
            )
            assertTrue("Mode ${mode.name} announcement must provide guidance", text.contains("active", ignoreCase = true))
        }
    }
}
