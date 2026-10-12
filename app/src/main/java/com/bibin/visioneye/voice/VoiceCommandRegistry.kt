package com.bibin.visioneye.voice

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Centralized, typed registry connecting visible UI controls to voice commands.
 *
 * Implements a single source of truth for:
 * - Visible control labels
 * - Canonical spoken phrases
 * - Real execution handlers
 * - Availability predicates
 * - Dynamic contextual help ("What can I do here?")
 */
class VoiceCommandRegistry {

    private val actions = CopyOnWriteArrayList<VoiceAction>()

    /**
     * Registers a single voice action.
     */
    fun register(action: VoiceAction) {
        // Prevent duplicate action registration by ID
        actions.removeIf { it.id == action.id }
        actions.add(action)
    }

    /**
     * Registers a batch of voice actions.
     */
    fun registerAll(vararg newActions: VoiceAction) {
        for (action in newActions) {
            register(action)
        }
    }

    /**
     * Registers a collection of voice actions.
     */
    fun registerAll(newActions: Collection<VoiceAction>) {
        for (action in newActions) {
            register(action)
        }
    }

    /**
     * Removes actions registered for a specific screen context.
     */
    fun unregisterForContext(context: VoiceScreenContext) {
        actions.removeIf { it.screenContext == context }
    }

    /**
     * Removes an action by its unique ID.
     */
    fun unregister(actionId: String) {
        actions.removeIf { it.id == actionId }
    }

    /**
     * Clears all registered actions.
     */
    fun clear() {
        actions.clear()
    }

    /**
     * Returns all registered actions relevant to the specified screen context
     * (including screen-specific actions and global actions).
     */
    fun getActionsForContext(context: VoiceScreenContext): List<VoiceAction> {
        return actions.filter { it.screenContext == context || it.screenContext == null }
    }

    /**
     * Returns all currently available (visible and enabled) actions for the screen context.
     */
    fun getAvailableActions(context: VoiceScreenContext): List<VoiceAction> {
        return getActionsForContext(context).filter { it.isAvailable() }
    }

    /**
     * Dynamically generates the contextual help announcement for the current screen context.
     * Always derived from active registered actions to prevent documentation drift.
     */
    fun generateContextualHelp(context: VoiceScreenContext): String {
        val available = getAvailableActions(context)
            .filter { it.screenContext == context || it.screenContext == null }
            .filter { it.id != "global_help" } // Exclude self-reference

        if (available.isEmpty()) {
            return "No voice controls are currently available on ${context.displayName}."
        }

        val commandNames = available.map { it.canonicalCommand }.distinct()
        val formattedList = if (commandNames.size <= 2) {
            commandNames.joinToString(" or ")
        } else {
            val allExceptLast = commandNames.dropLast(1).joinToString(", ")
            "$allExceptLast, or ${commandNames.last()}"
        }

        return "On ${context.displayName}, you can say: $formattedList."
    }

    /**
     * Matches one or more speech hypotheses against all currently available actions.
     */
    fun match(hypotheses: List<String>, context: VoiceScreenContext): MatchResult {
        return VoiceMatcher.match(hypotheses, actions, context)
    }

    /**
     * Total number of registered actions.
     */
    val size: Int
        get() = actions.size
}
