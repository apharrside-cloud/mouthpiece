package com.pharrlabs.voice.bubble

sealed interface BubbleState {
    data object Idle : BubbleState
    data object Listening : BubbleState
    data object Processing : BubbleState
    data class Error(val reason: String) : BubbleState
}

enum class BubbleAction { StartRecording, StopAndTranscribe, Nothing, ShowError }

/**
 * Tap-to-start / tap-to-stop dictation. Results that arrive after [reset] (focus lost, service
 * stopped) are ignored so a late transcript can never be injected into the wrong field.
 */
class BubbleStateMachine {
    var state: BubbleState = BubbleState.Idle
        private set

    fun onTap(): BubbleAction = when (state) {
        BubbleState.Idle -> { state = BubbleState.Listening; BubbleAction.StartRecording }
        BubbleState.Listening -> { state = BubbleState.Processing; BubbleAction.StopAndTranscribe }
        BubbleState.Processing -> BubbleAction.Nothing
        is BubbleState.Error -> { state = BubbleState.Idle; BubbleAction.Nothing }
    }

    /** Recorder finished. Empty means no real speech: go back to Idle and inject nothing. */
    fun onRecorded(empty: Boolean): BubbleAction {
        if (state == BubbleState.Processing && empty) state = BubbleState.Idle
        return BubbleAction.Nothing
    }

    fun onInjected(): BubbleAction {
        if (state == BubbleState.Processing) state = BubbleState.Idle
        return BubbleAction.Nothing
    }

    fun onFailure(reason: String): BubbleAction {
        if (state != BubbleState.Listening && state != BubbleState.Processing) return BubbleAction.Nothing
        state = BubbleState.Error(reason)
        return BubbleAction.ShowError
    }

    fun reset() { state = BubbleState.Idle }
}

object FieldFilter {
    /** Hint of the "Try it here" box on the Home tab: the one field inside our own app that gets a bubble. */
    const val TRY_HINT = "Dictate here\u2026"

    fun shouldShow(isEditable: Boolean, isPassword: Boolean, packageName: String, ownPackage: String, hint: String? = null): Boolean =
        isEditable && !isPassword && (packageName != ownPackage || hint == TRY_HINT)
}
