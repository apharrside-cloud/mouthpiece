package com.pharrlabs.voice.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleStateMachineTest {
    @Test fun startsIdle() =
        assertEquals(BubbleState.Idle, BubbleStateMachine().state)

    @Test fun tapFromIdleStartsRecording() {
        val m = BubbleStateMachine()
        assertEquals(BubbleAction.StartRecording, m.onTap())
        assertEquals(BubbleState.Listening, m.state)
    }

    @Test fun tapFromListeningStopsAndTranscribes() {
        val m = BubbleStateMachine().apply { onTap() }
        assertEquals(BubbleAction.StopAndTranscribe, m.onTap())
        assertEquals(BubbleState.Processing, m.state)
    }

    @Test fun tapWhileProcessingDoesNothing() {
        val m = BubbleStateMachine().apply { onTap(); onTap() }
        assertEquals(BubbleAction.Nothing, m.onTap())
        assertEquals(BubbleState.Processing, m.state)
    }

    @Test fun emptyRecordingReturnsToIdleWithoutInjecting() {
        val m = BubbleStateMachine().apply { onTap(); onTap() }
        assertEquals(BubbleAction.Nothing, m.onRecorded(empty = true))
        assertEquals(BubbleState.Idle, m.state)
    }

    @Test fun nonEmptyRecordingKeepsProcessing() {
        val m = BubbleStateMachine().apply { onTap(); onTap() }
        assertEquals(BubbleAction.Nothing, m.onRecorded(empty = false))
        assertEquals(BubbleState.Processing, m.state)
    }

    @Test fun injectedReturnsToIdle() {
        val m = BubbleStateMachine().apply { onTap(); onTap(); onRecorded(false) }
        assertEquals(BubbleAction.Nothing, m.onInjected())
        assertEquals(BubbleState.Idle, m.state)
    }

    @Test fun failureShowsErrorThenTapClearsIt() {
        val m = BubbleStateMachine().apply { onTap() }
        assertEquals(BubbleAction.ShowError, m.onFailure("mic busy"))
        assertEquals(BubbleState.Error("mic busy"), m.state)
        assertEquals(BubbleAction.Nothing, m.onTap())
        assertEquals(BubbleState.Idle, m.state)
    }

    @Test fun lateResultsAreIgnoredAfterReset() {
        val m = BubbleStateMachine().apply { onTap(); onTap() }
        m.reset()
        assertEquals(BubbleState.Idle, m.state)
        assertEquals(BubbleAction.Nothing, m.onInjected())
        assertEquals(BubbleAction.Nothing, m.onRecorded(empty = false))
        assertEquals(BubbleState.Idle, m.state)
    }

    @Test fun lateFailureAfterResetIsIgnored() {
        val m = BubbleStateMachine().apply { onTap(); onTap() }
        m.reset()
        assertEquals(BubbleAction.Nothing, m.onFailure("late"))
        assertTrue(m.state is BubbleState.Idle)
    }
}
