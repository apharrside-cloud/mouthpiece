package com.pharrlabs.voice.asr

/**
 * Splits long audio into chunks of at most [maxChunkSec] seconds (whisper encodes 30 s windows).
 * Each cut lands on the quietest 20 ms frame in the last 5 s of the window, so words are not
 * sliced mid-syllable. When several frames tie for quietest (a digital pause), the middle one wins.
 */
object Chunker {
    private const val FRAME_MS = 20
    private const val SEARCH_SEC = 5

    fun split(pcm: ShortArray, maxChunkSec: Int = 28, sampleRate: Int = 16000): List<ShortArray> {
        if (pcm.isEmpty()) return emptyList()
        val max = maxChunkSec * sampleRate
        val frame = sampleRate * FRAME_MS / 1000
        val chunks = mutableListOf<ShortArray>()
        var pos = 0
        while (pcm.size - pos > max) {
            val windowEnd = pos + max
            val searchStart = (windowEnd - SEARCH_SEC * sampleRate).coerceAtLeast(pos + frame)
            val cut = quietestCut(pcm, searchStart, windowEnd, frame)
            chunks += pcm.copyOfRange(pos, cut)
            pos = cut
        }
        chunks += pcm.copyOfRange(pos, pcm.size)
        return chunks
    }

    private fun quietestCut(pcm: ShortArray, from: Int, to: Int, frame: Int): Int {
        var best = Long.MAX_VALUE
        val tied = ArrayList<Int>()
        var start = from
        while (start + frame <= to) {
            var energy = 0L
            for (i in start until start + frame) energy += pcm[i].toLong() * pcm[i]
            if (energy < best) {
                best = energy
                tied.clear()
                tied += start
            } else if (energy == best) {
                tied += start
            }
            start += frame
        }
        return tied[tied.size / 2]
    }
}
