package com.pharrlabs.voice.asr

import kotlin.math.abs
import kotlin.math.min

/** PCM helpers for feeding whisper. */
object Pcm {
    private const val TARGET_PEAK = 0.9f
    private const val MAX_GAIN = 30f

    /**
     * Converts 16-bit PCM to floats, peak-normalizing quiet audio (the Pixel's VOICE_RECOGNITION
     * source peaks around 0.06-0.1 for normal speech) with a capped gain so noise is not blown up,
     * and zero-pads to [minSamples] because whisper ignores clips under 1 s.
     */
    fun toNormalizedFloats(pcm: ShortArray, minSamples: Int = 19200): FloatArray {
        var peak = 0
        for (s in pcm) peak = maxOf(peak, abs(s.toInt()))
        val gain = if (peak == 0) 1f else maxOf(1f, min(TARGET_PEAK * 32768f / peak, MAX_GAIN))
        val out = FloatArray(maxOf(pcm.size, minSamples))
        for (i in pcm.indices) out[i] = pcm[i] / 32768f * gain
        return out
    }
}
