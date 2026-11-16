package com.pharrlabs.voice.audio

/**
 * Tracks speech and trailing silence over fixed-size audio chunks so dictation can stop itself once the
 * speaker has finished talking. Silence before any real speech never triggers a stop.
 */
class EndpointTracker(private val thresholdRms: Int = 200, private val chunkMs: Int = 100) {
    private var voicedMs = 0
    private var silentRunMs = 0

    fun onChunk(rms: Double) {
        if (rms >= thresholdRms) { voicedMs += chunkMs; silentRunMs = 0 } else silentRunMs += chunkMs
    }

    /** @param silenceMs 0 disables auto-stop. */
    fun shouldStop(silenceMs: Int, minSpeechMs: Int): Boolean =
        silenceMs > 0 && voicedMs >= minSpeechMs && silentRunMs >= silenceMs

    fun reset() { voicedMs = 0; silentRunMs = 0 }
}
