package com.pharrlabs.voice.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointTrackerTest {
    private fun tracker() = EndpointTracker(thresholdRms = 200, chunkMs = 100)

    @Test fun neverStopsBeforeAnySpeech() {
        val t = tracker()
        repeat(100) { t.onChunk(10.0) }
        assertFalse(t.shouldStop(silenceMs = 2000, minSpeechMs = 400))
    }

    @Test fun stopsAfterSpeechThenSilence() {
        val t = tracker()
        repeat(6) { t.onChunk(900.0) }
        repeat(19) { t.onChunk(20.0) }
        assertFalse(t.shouldStop(2000, 400))
        t.onChunk(20.0)
        assertTrue(t.shouldStop(2000, 400))
    }

    @Test fun speechResetsSilenceRun() {
        val t = tracker()
        repeat(6) { t.onChunk(900.0) }
        repeat(15) { t.onChunk(20.0) }
        t.onChunk(900.0)
        repeat(15) { t.onChunk(20.0) }
        assertFalse(t.shouldStop(2000, 400))
    }

    @Test fun tooLittleSpeechDoesNotCount() {
        val t = tracker()
        repeat(2) { t.onChunk(900.0) }
        repeat(30) { t.onChunk(20.0) }
        assertFalse(t.shouldStop(2000, 400))
    }

    @Test fun zeroSilenceMeansDisabled() {
        val t = tracker()
        repeat(6) { t.onChunk(900.0) }
        repeat(100) { t.onChunk(20.0) }
        assertFalse(t.shouldStop(0, 400))
    }

    @Test fun resetClearsState() {
        val t = tracker()
        repeat(6) { t.onChunk(900.0) }
        repeat(30) { t.onChunk(20.0) }
        t.reset()
        assertFalse(t.shouldStop(2000, 400))
    }
}
