package com.pharrlabs.voice.asr

import com.pharrlabs.voice.audio.SilenceTrimmer

/**
 * Transcribes finished stretches of speech while the user is still talking. [poll] is called with the whole
 * recording so far; every pause-delimited segment it finds is transcribed once and kept. [finish] transcribes
 * whatever is left and returns the full text, so the wait after "stop" covers only the last phrase.
 *
 * Each segment is silence-trimmed independently (the audio passed in is untrimmed), and segments that contain
 * no real speech produce no text.
 */
class StreamingSession(
    private val transcriber: Transcriber,
    private val onText: (String) -> Unit = {},
) {
    private val parts = mutableListOf<String>()
    private var consumed = 0
    private var previewedEnd = -1

    val committedText: String get() = parts.joinToString(" ")

    /** Transcribes any newly completed segments in [audio]; returns how many samples are now committed. */
    suspend fun poll(audio: ShortArray): Int {
        while (true) {
            val cut = SegmentPlanner.nextCut(audio, from = consumed) ?: break
            transcribeSegment(audio.copyOfRange(consumed, cut))
            consumed = cut
        }
        return consumed
    }

    /**
     * Live caption only: transcribes the not-yet-committed tail (at most the last [maxMs]) and reports
     * committed text plus this draft through onText. Nothing is committed; [finish] still transcribes the tail
     * properly. Skipped (null) when the tail is too short, silent, or has barely grown since the last preview.
     */
    suspend fun preview(audio: ShortArray, maxMs: Int = 4000, minMs: Int = 1000): String? {
        val rate = 16
        if (audio.size - consumed < minMs * rate) return null
        if (previewedEnd >= 0 && audio.size - previewedEnd < 500 * rate) return null
        previewedEnd = audio.size
        val from = maxOf(consumed, audio.size - maxMs * rate)
        val tail = SilenceTrimmer.trim(audio.copyOfRange(from, audio.size))
        if (tail.isEmpty()) return null
        val text = transcriber.transcribe(tail).trim()
        if (text.isEmpty()) return null
        onText((parts + text).joinToString(" "))
        return text
    }

    /** Transcribes the remaining tail of [audio] and returns the complete text. */
    suspend fun finish(audio: ShortArray): String {
        if (consumed < audio.size) transcribeSegment(audio.copyOfRange(consumed, audio.size))
        consumed = audio.size
        return committedText
    }

    private suspend fun transcribeSegment(segment: ShortArray) {
        val trimmed = SilenceTrimmer.trim(segment)
        if (trimmed.isEmpty()) return
        val text = transcriber.transcribe(trimmed).trim()
        if (text.isEmpty()) return
        parts += text
        onText(committedText)
    }
}
