package com.pharrlabs.voice.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnWatcherTest {
    private val known = mutableMapOf<String, String>()
    private fun watcher(now: () -> Long) = LearnWatcher(windowMs = 60_000, isKnown = { c -> known[c.from] == c.to }, clock = now)

    @Test fun offersCorrectionAfterEdit() {
        var t = 0L
        val w = watcher { t }
        w.start(baseline = "Tell Tracy hello.", inserted = "Tell Tracy hello.")
        t = 5_000
        assertEquals(Correction("tracy", "Tracey"), w.onTextChanged("Tell Tracey hello."))
    }

    @Test fun nothingBeforeStart() = assertNull(watcher { 0L }.onTextChanged("anything"))

    @Test fun expiresAfterWindow() {
        var t = 0L
        val w = watcher { t }
        w.start("Tell Tracy hello.", "Tell Tracy hello.")
        t = 61_000
        assertNull(w.onTextChanged("Tell Tracey hello."))
    }

    @Test fun knownCorrectionNotOfferedAgain() {
        known["tracy"] = "Tracey"
        val w = watcher { 0L }
        w.start("Tell Tracy hello.", "Tell Tracy hello.")
        assertNull(w.onTextChanged("Tell Tracey hello."))
    }

    @Test fun sameCorrectionOfferedOnlyOnce() {
        val w = watcher { 0L }
        w.start("Tell Tracy hello.", "Tell Tracy hello.")
        assertEquals(Correction("tracy", "Tracey"), w.onTextChanged("Tell Tracey hello."))
        assertNull(w.onTextChanged("Tell Tracey hello."))
    }

    @Test fun stopClearsState() {
        val w = watcher { 0L }
        w.start("Tell Tracy hello.", "Tell Tracy hello.")
        w.stop()
        assertNull(w.onTextChanged("Tell Tracey hello."))
    }

    @Test fun restartReplacesBaseline() {
        val w = watcher { 0L }
        w.start("Old text.", "Old text.")
        w.start("Tell Tracy hello.", "Tell Tracy hello.")
        assertEquals(Correction("tracy", "Tracey"), w.onTextChanged("Tell Tracey hello."))
    }
}
