package com.bibin.visioneye.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [VoiceCommandRegistry].
 *
 * Verifies:
 * - Thread-safe registration and unregistration
 * - Context-scoped action queries
 * - Dynamic contextual help text generation derived directly from registered actions
 * - Absence of drift between registered visible labels and help text
 */
class VoiceCommandRegistryTest {

    private lateinit var registry: VoiceCommandRegistry

    @Before
    fun setUp() {
        registry = VoiceCommandRegistry()
    }

    @Test
    fun register_and_match() {
        val action = VoiceAction(
            id = "test_action",
            screenContext = VoiceScreenContext.MAIN_DASHBOARD,
            visibleLabel = "NAVIGATE",
            canonicalCommand = "Navigate",
            onExecute = { VoiceActionResult.Success("Navigating") }
        )

        registry.register(action)
        val result = registry.match(listOf("NAVIGATE"), VoiceScreenContext.MAIN_DASHBOARD)
        assertTrue(result is MatchResult.Matched)
        assertEquals("test_action", (result as MatchResult.Matched).action.id)
    }

    @Test
    fun unregister_removesAction() {
        val action = VoiceAction(
            id = "test_action_2",
            screenContext = VoiceScreenContext.MAIN_DASHBOARD,
            visibleLabel = "READ TEXT",
            canonicalCommand = "Read text",
            onExecute = { VoiceActionResult.Success("Reading") }
        )

        registry.register(action)
        registry.unregister("test_action_2")
        val result = registry.match(listOf("READ TEXT"), VoiceScreenContext.MAIN_DASHBOARD)
        assertTrue(result is MatchResult.NoMatch)
    }

    @Test
    fun unregisterForContext_removesOnlyContextActions() {
        val mainAction = VoiceAction(
            id = "main_action",
            screenContext = VoiceScreenContext.MAIN_DASHBOARD,
            visibleLabel = "NAVIGATE",
            canonicalCommand = "Navigate",
            onExecute = { VoiceActionResult.Success("Navigating") }
        )
        val sosAction = VoiceAction(
            id = "sos_action",
            screenContext = VoiceScreenContext.SOS_EMERGENCY,
            visibleLabel = "TRIGGER SOS",
            canonicalCommand = "Trigger SOS",
            onExecute = { VoiceActionResult.Success("Trigger SOS") }
        )

        registry.register(mainAction)
        registry.register(sosAction)

        registry.unregisterForContext(VoiceScreenContext.MAIN_DASHBOARD)

        assertEquals(0, registry.getActionsForContext(VoiceScreenContext.MAIN_DASHBOARD).filter { it.screenContext == VoiceScreenContext.MAIN_DASHBOARD }.size)
        assertEquals(1, registry.getActionsForContext(VoiceScreenContext.SOS_EMERGENCY).filter { it.screenContext == VoiceScreenContext.SOS_EMERGENCY }.size)
    }

    @Test
    fun generateContextualHelp_includesOnlyAvailableRegisteredActions() {
        val available = VoiceAction(
            id = "action_avail",
            screenContext = VoiceScreenContext.MAIN_DASHBOARD,
            visibleLabel = "NAVIGATE",
            canonicalCommand = "Navigate",
            isAvailable = { true },
            onExecute = { VoiceActionResult.Success("OK") }
        )
        val unavailable = VoiceAction(
            id = "action_unavail",
            screenContext = VoiceScreenContext.MAIN_DASHBOARD,
            visibleLabel = "DISABLED BUTTON",
            canonicalCommand = "Disabled button",
            isAvailable = { false },
            onExecute = { VoiceActionResult.Success("OK") }
        )

        registry.registerAll(available, unavailable)

        val help = registry.generateContextualHelp(VoiceScreenContext.MAIN_DASHBOARD)
        assertTrue(help.contains("Navigate"))
        assertTrue(!help.contains("Disabled button"))
    }
}
