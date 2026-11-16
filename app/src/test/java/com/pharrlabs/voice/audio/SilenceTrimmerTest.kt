package com.pharrlabs.voice.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SilenceTrimmerTest {
    private val rate = 16000

    private fun silence(seconds: Double) = ShortArray((rate * seconds).toInt())

    private fun sine(seconds: Double, amplitude: Int): ShortArray =
        ShortArray((rate * seconds).toInt()) { i ->
            (amplitude * sin(2 * PI * 440.0 * i / rate)).toInt().toShort()
        }

    @Test fun allSilenceReturnsEmpty() =
        assertEquals(0, SilenceTrimmer.trim(silence(1.0)).size)

    @Test fun trimsLeadingAndTrailingSilence() {
        val pcm = silence(0.5) + sine(1.0, 8000) + silence(0.5)
        val out = SilenceTrimmer.trim(pcm)
        assertTrue("kept at least the speech (${out.size})", out.size >= 16000)
        assertTrue("padding bounded (${out.size})", out.size <= 16000 + 2 * 3200)
        assertTrue("shorter than input (${out.size})", out.size < 32000)
    }

    @Test fun keepsInteriorPause() {
        val pcm = silence(0.5) + sine(1.0, 8000) + silence(1.0) + sine(1.0, 8000) + silence(0.5)
        val out = SilenceTrimmer.trim(pcm)
        assertTrue("interior pause preserved (${out.size})", out.size >= 16000 + 16000 + 16000)
    }

    @Test fun shortBlipReturnsEmpty() =
        assertEquals(0, SilenceTrimmer.trim(silence(0.5) + sine(0.1, 8000) + silence(0.5)).size)

    @Test fun quietSpeechSurvives() {
        // Amplitude 350 gives RMS ~247: the level measured on the Pixel in Spike A.
        val out = SilenceTrimmer.trim(silence(0.5) + sine(1.0, 350) + silence(0.5))
        assertTrue("quiet speech kept (${out.size})", out.size > 0)
    }
}
