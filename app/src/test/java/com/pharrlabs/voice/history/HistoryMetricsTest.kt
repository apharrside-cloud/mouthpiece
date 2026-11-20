package com.pharrlabs.voice.history

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class HistoryMetricsTest {
    private fun entry(text: String, ms: Long? = null) = HistoryEntry(1, "app", text, text, "SetText", ms)

    @Test fun wordsUseFinalTextAndHandlePunctuationAndWhitespace() {
        assertEquals(7, HistoryMetrics.wordCount("Don't re-type café…\nIt’s 2026, hello world!"))
        assertEquals(0, HistoryMetrics.wordCount(" … — \n"))
    }

    @Test fun averagesUseAllSavedDictationsButTimingOnlyUsesTimedEntries() {
        val m = HistoryMetrics.from(listOf(entry("one two", 10_000), entry("one two three four", 20_000), entry("legacy entry")))
        assertEquals(8, m.words)
        assertEquals(3, m.dictations)
        assertEquals(8.0 / 3, m.averageWords, 0.0001)
        assertEquals(2, m.timedDictations)
        assertEquals(30_000L, m.recordingMs)
        assertEquals(15_000L, m.averageRecordingMs)
        assertEquals(12.0, m.wordsPerMinute!!, 0.0001)
    }

    @Test fun emptyAndLegacyOnlyTimingDoesNotInventZeroSpeedOrDuration() {
        val m = HistoryMetrics.from(emptyList())
        assertEquals(0.0, m.averageWords, 0.0)
        assertNull(m.averageRecordingMs)
        assertNull(m.wordsPerMinute)
        assertNull(HistoryMetrics.from(listOf(entry("old words"))).averageRecordingMs)
        assertNull(HistoryMetrics.from(listOf(entry("invalid duration", 0))).wordsPerMinute)
    }

    @Test fun removalAndExpiryUpdateMetricsFromTheSameRetainedEntries() {
        val storage = object : HistoryStorage {
            var json: String? = null
            override fun load() = json
            override fun save(json: String) { this.json = json }
        }
        var now = 10L
        val h = HistoryStore(storage) { now }
        h.add(entry("first two", 1000).copy(timeMs = 1))
        h.add(entry("three four five", 2000).copy(timeMs = 2))
        h.remove(1)
        assertEquals(3, HistoryMetrics.from(h.all()).words)
        now = HistoryStore.RETENTION_MS + 2
        assertEquals(0, HistoryMetrics.from(h.all()).dictations)
    }

    @Test fun timingUsesCapturedSamplesAndFormatsReadableDurations() {
        assertEquals(1250L, HistoryTiming.recordingMs(20_000))
        assertEquals(0L, HistoryTiming.recordingMs(-1))
        assertEquals("80ms", HistoryTiming.format(80))
        assertEquals("1m 5s", HistoryTiming.format(65_000))
        assertEquals("1h 1m", HistoryTiming.format(3_660_000))
    }

    @Test fun processingAveragesStayWithTheirModelAndExcludeUnattributedOrMissingTimings() {
        val rows = listOf(
            entry("short").copy(modelName = "tiny", processingMs = 1000),
            entry("longer words here").copy(modelName = "tiny", processingMs = 2000),
            entry("other model").copy(modelName = "small", processingMs = 9000),
            entry("old model unknown").copy(processingMs = 100_000),
            entry("missing time").copy(modelName = "tiny"),
            entry("invalid time").copy(modelName = "tiny", processingMs = -10),
            entry("blank model").copy(modelName = " ", processingMs = 100_000),
        )
        val stats = HistoryMetrics.processingByModel(rows)
        assertEquals(setOf("tiny", "small"), stats.keys)
        assertEquals(ProcessingStats(2, 1500), stats["tiny"])
        assertEquals(ProcessingStats(1, 9000), stats["small"])
        assertTrue(HistoryMetrics.processingByModel(emptyList()).isEmpty())
    }

    @Test fun processingAveragesUpdateWhenEntriesAreDeletedOrExpire() {
        val storage = object : HistoryStorage {
            var json: String? = null
            override fun load() = json
            override fun save(json: String) { this.json = json }
        }
        var now = 10L
        val h = HistoryStore(storage) { now }
        h.add(entry("one").copy(timeMs = 1, modelName = "tiny", processingMs = 1000))
        h.add(entry("two").copy(timeMs = 2, modelName = "tiny", processingMs = 3000))
        assertEquals(ProcessingStats(2, 2000), HistoryMetrics.processingByModel(h.all())["tiny"])
        h.remove(1)
        assertEquals(ProcessingStats(1, 3000), HistoryMetrics.processingByModel(h.all())["tiny"])
        now = HistoryStore.RETENTION_MS + 2
        assertTrue(HistoryMetrics.processingByModel(h.all()).isEmpty())
    }

    @Test fun zeroProcessingTimeIsAValidMeasurementAndShortWaitsKeepTheirPrecision() {
        val stats = HistoryMetrics.processingByModel(listOf(entry("instant").copy(modelName = "tiny", processingMs = 0)))
        assertEquals(ProcessingStats(1, 0), stats["tiny"])
        assertEquals("0ms", HistoryTiming.formatProcessing(0, Locale.US))
        assertEquals("850ms", HistoryTiming.formatProcessing(850, Locale.US))
        assertEquals("1.5s", HistoryTiming.formatProcessing(1500, Locale.US))
        assertEquals("1,5s", HistoryTiming.formatProcessing(1500, Locale.FRANCE))
        assertEquals("1m 5s", HistoryTiming.formatProcessing(65_000, Locale.US))
    }
}
