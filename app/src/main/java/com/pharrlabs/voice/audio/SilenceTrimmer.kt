package com.pharrlabs.voice.audio

import kotlin.math.sqrt

object SilenceTrimmer {
    private const val FRAME_MS = 20

    /**
     * Trims leading/trailing silence using 20 ms RMS frames, keeping [padMs] around the first and
     * last voiced frame and leaving interior pauses intact. Returns empty when there is less than
     * [minSpeechMs] of voiced audio in total (blips, clicks, room noise).
     */
    fun trim(
        pcm: ShortArray,
        sampleRate: Int = 16000,
        thresholdRms: Int = 200,
        padMs: Int = 200,
        minSpeechMs: Int = 300,
    ): ShortArray {
        val frame = sampleRate * FRAME_MS / 1000
        val frames = pcm.size / frame
        var first = -1
        var last = -1
        var voiced = 0
        for (f in 0 until frames) {
            if (rms(pcm, f * frame, frame) >= thresholdRms) {
                if (first < 0) first = f
                last = f
                voiced++
            }
        }
        if (first < 0 || voiced * FRAME_MS < minSpeechMs) return ShortArray(0)
        val pad = sampleRate * padMs / 1000
        val from = (first * frame - pad).coerceAtLeast(0)
        val to = ((last + 1) * frame + pad).coerceAtMost(pcm.size)
        return pcm.copyOfRange(from, to)
    }

    private fun rms(pcm: ShortArray, start: Int, length: Int): Double {
        var sum = 0.0
        for (i in start until start + length) sum += pcm[i].toDouble() * pcm[i]
        return sqrt(sum / length)
    }
}
