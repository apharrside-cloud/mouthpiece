package com.pharrlabs.voice.history

import java.text.NumberFormat
import java.util.Locale

data class ProcessingStats(val dictations: Int, val averageMs: Long)

/** Metrics describe saved, completed dictations. Deleting an entry also removes its contribution. */
data class HistoryMetrics(
    val words: Int,
    val dictations: Int,
    val timedDictations: Int,
    val recordingMs: Long,
    private val timedWords: Int,
) {
    val averageWords: Double get() = if (dictations == 0) 0.0 else words.toDouble() / dictations
    val averageRecordingMs: Long? get() = if (timedDictations == 0) null else recordingMs / timedDictations
    val wordsPerMinute: Double? get() = if (recordingMs == 0L) null else timedWords * 60_000.0 / recordingMs

    companion object {
        private val word = Regex("[\\p{L}\\p{N}]+(?:[’'\\-][\\p{L}\\p{N}]+)*")
        fun wordCount(text: String): Int = word.findAll(text).count()
        fun from(entries: List<HistoryEntry>): HistoryMetrics {
            val timed = entries.filter { (it.recordingMs ?: 0) > 0 }
            return HistoryMetrics(entries.sumOf { wordCount(it.text) }, entries.size, timed.size,
                timed.sumOf { it.recordingMs!! }, timed.sumOf { wordCount(it.text) })
        }

        /** Call with the store's retained entries so these use the same 30-day window as History. */
        fun processingByModel(entries: List<HistoryEntry>): Map<String, ProcessingStats> = entries
            .filter { !it.modelName.isNullOrBlank() && (it.processingMs ?: -1) >= 0 }
            .groupBy { it.modelName!! }
            .mapValues { (_, samples) -> ProcessingStats(samples.size, samples.sumOf { it.processingMs!! } / samples.size) }
    }
}

object HistoryTiming {
    /** Recorder and Whisper both use 16 kHz mono PCM. Includes pauses, independent of silence trimming. */
    fun recordingMs(samples: Int): Long = samples.coerceAtLeast(0) * 1000L / 16_000

    /** A fraction of a second helps compare the short waits that whole seconds would round together. */
    fun formatProcessing(ms: Long, locale: Locale = Locale.getDefault()): String = when {
        ms < 1000 -> "${ms.coerceAtLeast(0)}ms"
        ms < 60_000 -> NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(ms / 1000.0) + "s"
        else -> format(ms)
    }

    fun format(ms: Long): String {
        if (ms in 1..999) return "${ms}ms"
        val seconds = ((ms.coerceAtLeast(0) + 500) / 1000).coerceAtLeast(if (ms > 0) 1 else 0)
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
