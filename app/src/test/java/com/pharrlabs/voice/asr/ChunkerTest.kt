package com.pharrlabs.voice.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ChunkerTest {
    private val rate = 16000

    private fun sine(seconds: Double, amplitude: Int = 6000) =
        ShortArray((rate * seconds).toInt()) { i -> (amplitude * sin(2 * PI * 440.0 * i / rate)).toInt().toShort() }

    private fun silence(seconds: Double) = ShortArray((rate * seconds).toInt())

    @Test fun shortAudioIsOneChunk() {
        val pcm = sine(10.0)
        val chunks = Chunker.split(pcm)
        assertEquals(1, chunks.size)
        assertEquals(pcm.size, chunks[0].size)
    }

    @Test fun longAudioSplitsUnderMax() {
        val pcm = sine(70.0)
        val chunks = Chunker.split(pcm)
        assertTrue("expected >= 3 chunks, got ${chunks.size}", chunks.size >= 3)
        assertTrue("every chunk <= 28 s", chunks.all { it.size <= 28 * rate })
        assertEquals(pcm.size, chunks.sumOf { it.size })
    }

    @Test fun splitsAtQuietPoint() {
        // Speech to 26.5 s, a 1 s pause, then speech again: the cut must land inside the pause.
        val pcm = sine(26.5) + silence(1.0) + sine(20.0)
        val chunks = Chunker.split(pcm)
        assertTrue(chunks.size >= 2)
        val cut = chunks[0].size
        assertTrue("cut at ${cut / rate.toDouble()} s should be inside 26.5..27.5 s", cut in (26.5 * rate).toInt()..(27.5 * rate).toInt())
        assertEquals(pcm.size, chunks.sumOf { it.size })
    }

    @Test fun emptyInputGivesNoChunks() =
        assertEquals(0, Chunker.split(ShortArray(0)).size)
}
