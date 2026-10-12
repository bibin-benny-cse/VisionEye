package com.bibin.visioneye.voice

/**
 * Representation of a registered voice-controlled action in VisionEye.
 *
 * Core rule: The canonical spoken phrase matches the exact visible label in the UI.
 *
 * @property id Unique identifier for the action.
 * @property screenContext Screen context where this action is active (null if global across all screens).
 * @property visibleLabel Exact visible text displayed on the UI control.
 * @property canonicalCommand Spoken phrase derived directly from the visible label.
 * @property aliases Natural spoken variations of the visible label (e.g., punctuation or case variants).
 * @property description Accessible description used for TalkBack and contextual help ("What can I do here?").
 * @property isDictation True if activating this control begins a separate dictation session.
 * @property isAvailable Evaluates whether the interactive control is currently visible and enabled in the UI.
 * @property onExecute Handler executing the exact existing UI callback or controller handler.
 */
data class VoiceAction(
    val id: String,
    val screenContext: VoiceScreenContext?,
    val visibleLabel: String,
    val canonicalCommand: String,
    val aliases: List<String> = emptyList(),
    val description: String = visibleLabel,
    val isDictation: Boolean = false,
    val isAvailable: () -> Boolean = { true },
    val onExecute: suspend () -> VoiceActionResult
)
