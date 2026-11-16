package com.pharrlabs.voice.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmTest {
    @Test fun quietSpeechIsAmplifiedToTargetPeak() {
        val out = Pcm.toNormalizedFloats(shortArrayOf(0, 2000, -3000, 1000))
        assertEquals(0.9f, out.maxOf { kotlin.math.abs(it) }, 0.001f)
    }

    @Test fun gainIsCapped() {
        // A near-silent click must not be blown up to full scale.
        val out = Pcm.toNormalizedFloats(shortArrayOf(0, 10, -10))
        assertTrue(out.maxOf { kotlin.math.abs(it) } < 0.05f)
    }

    @Test fun loudAudioIsNotAmplified() {
        val out = Pcm.toNormalizedFloats(shortArrayOf(30000, -30000))
        assertEquals(30000f / 32768f, out[0], 0.0001f)
    }

    @Test fun allZeroStaysZero() =
        assertTrue(Pcm.toNormalizedFloats(ShortArray(100)).all { it == 0f })

    @Test fun shortClipsArePaddedToMinimumLength() {
        val out = Pcm.toNormalizedFloats(ShortArray(8000) { 1000 }, minSamples = 19200)
        assertEquals(19200, out.size)
        assertEquals(0f, out.last(), 0f)
    }
}
