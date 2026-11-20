package com.pharrlabs.voice.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreTest {
    private val clock = { 0L }
    private class Mem : HistoryStorage {
        var json: String? = null
        override fun load() = json
        override fun save(json: String) { this.json = json }
    }

    private fun entry(n: Int) = HistoryEntry(timeMs = n.toLong(), pkg = "com.app$n", raw = "raw $n", text = "Text $n.", result = "SetText")

    @Test fun startsEmpty() = assertTrue(HistoryStore(Mem(), clock).all().isEmpty())

    @Test fun newestFirst() {
        val h = HistoryStore(Mem(), clock)
        h.add(entry(1)); h.add(entry(2))
        assertEquals(listOf(2L, 1L), h.all().map { it.timeMs })
    }

    @Test fun retainsMoreThanTwentyDictations() {
        val h = HistoryStore(Mem(), clock)
        (1..50).forEach { h.add(entry(it)) }
        assertEquals((50L downTo 1L).toList(), h.all().map { it.timeMs })
    }

    @Test fun persistsAcrossInstances() {
        val mem = Mem()
        HistoryStore(mem, clock).add(entry(7))
        assertEquals(entry(7), HistoryStore(mem, clock).all().single())
    }

    @Test fun clearEmptiesIt() {
        val h = HistoryStore(Mem(), clock)
        h.add(entry(1)); h.clear()
        assertTrue(h.all().isEmpty())
    }

    @Test fun removeDeletesOneEntry() {
        val h = HistoryStore(Mem(), clock)
        h.add(entry(1)); h.add(entry(2))
        h.remove(1L)
        assertEquals(listOf(2L), h.all().map { it.timeMs })
    }

    @Test fun corruptStorageReadsAsEmpty() {
        val mem = Mem().apply { json = "[{broken" }
        assertTrue(HistoryStore(mem, clock).all().isEmpty())
    }

    @Test fun textWithQuotesAndNewlinesRoundTrips() {
        val mem = Mem()
        val e = HistoryEntry(1, "p", "she said \"hi\"\nthere", "She said \"hi\"\nThere.", "Pasted")
        HistoryStore(mem, clock).add(e)
        assertEquals(e, HistoryStore(mem, clock).all().single())
    }

    @Test fun emptyTextIsNotRecorded() {
        val h = HistoryStore(Mem(), clock)
        h.add(HistoryEntry(1, "p", "", "", "SetText"))
        assertTrue(h.all().isEmpty())
    }

    @Test fun expiresAtThirtyDaysAndPhysicallyPrunesStorageOnRead() {
        val mem = Mem()
        var now = HistoryStore.RETENTION_MS
        val h = HistoryStore(mem) { now }
        h.add(entry(1)); h.add(entry(2))
        now += 1
        assertEquals(listOf(entry(2)), h.all())
        assertEquals(listOf(entry(2)), HistoryStore(mem, clock).all()) // Old entry is gone from storage, too.
        now += 1
        assertTrue(h.all().isEmpty())
        assertEquals("[]", mem.json)
    }

    @Test fun legacyEntriesRemainReadableAndTimingIsUnknown() {
        val mem = Mem().apply { json = """[{"t":1,"p":"app","r":"old text","x":"Old text.","s":"Pasted"}]""" }
        val h = HistoryStore(mem, clock)
        assertEquals(HistoryEntry(1, "app", "old text", "Old text.", "Pasted"), h.all().single())
        h.add(entry(2).copy(recordingMs = 4000, processingMs = 850, modelName = "tiny.en-q5_1"))
        assertEquals(4000L, h.all().first().recordingMs)
        assertEquals(850L, h.all().first().processingMs)
        assertEquals("tiny.en-q5_1", h.all().first().modelName)
        assertEquals(null, h.all().last().modelName)
        assertEquals(null, h.all().last().recordingMs)
    }

    @Test fun staleWritesCannotReintroduceExpiredData() {
        val mem = Mem()
        val h = HistoryStore(mem) { HistoryStore.RETENTION_MS + 2 }
        h.add(entry(1))
        assertTrue(h.all().isEmpty())
    }

    @Test fun deletingThroughAnotherInstanceDoesNotResurrectEntries() {
        val mem = Mem()
        val a = HistoryStore(mem, clock)
        val b = HistoryStore(mem, clock)
        a.add(entry(1)); b.remove(1); a.add(entry(2))
        assertEquals(listOf(entry(2)), b.all())
    }
}
