package com.pharrlabs.voice.asr

import kotlin.math.sqrt

/**
 * Finds natural places to cut audio while the user is still talking, so finished stretches of speech can be
 * transcribed in the background and only the last bit is left to do when they stop.
 */
object SegmentPlanner {
    private const val FRAME_MS = 20

    /**
     * Returns the sample index to cut at (end of the segment that starts at [from]) or null if the speaker is
     * still mid-phrase. A cut needs at least [minSegmentMs] of audio containing [minSpeechMs] of speech,
     * followed by a pause of at least [pauseMs]. The cut sits a little into the pause so word endings are kept.
     */
    fun nextCut(
        pcm: ShortArray,
        from: Int = 0,
        sampleRate: Int = 16000,
        thresholdRms: Int = 200,
        pauseMs: Int = 700,
        minSegmentMs: Int = 2500,
        minSpeechMs: Int = 400,
    ): Int? {
        val frame = sampleRate * FRAME_MS / 1000
        var pos = from
        var voicedFrames = 0
        var silenceStart = -1
        var firstVoiced = -1
        while (pos + frame <= pcm.size) {
            if (rms(pcm, pos, frame) >= thresholdRms) {
                voicedFrames++
                if (firstVoiced < 0) firstVoiced = pos
                silenceStart = -1
            } else if (firstVoiced >= 0 && silenceStart < 0) {
                silenceStart = pos
            }
            if (silenceStart >= 0 && voicedFrames * FRAME_MS >= minSpeechMs) {
                val pauseSoFar = (pos + frame - silenceStart) * 1000 / sampleRate
                val segmentMs = (silenceStart - firstVoiced) * 1000 / sampleRate
                if (pauseSoFar >= pauseMs && segmentMs >= minSegmentMs) {
                    val pad = sampleRate * 250 / 1000
                    return (silenceStart + pad).coerceAtMost(pos + frame)
                }
            }
            pos += frame
        }
        return null
    }

    private fun rms(pcm: ShortArray, start: Int, length: Int): Double {
        var sum = 0.0
        for (i in start until start + length) sum += pcm[i].toDouble() * pcm[i]
        return sqrt(sum / length)
    }
}
