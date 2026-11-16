package com.pharrlabs.voice.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreTest {
    private class Mem : HistoryStorage {
        var json: String? = null
        override fun load() = json
        override fun save(json: String) { this.json = json }
    }

    private fun entry(n: Int) = HistoryEntry(timeMs = n.toLong(), pkg = "com.app$n", raw = "raw $n", text = "Text $n.", result = "SetText")

    @Test fun startsEmpty() = assertTrue(HistoryStore(Mem()).all().isEmpty())

    @Test fun newestFirst() {
        val h = HistoryStore(Mem())
        h.add(entry(1)); h.add(entry(2))
        assertEquals(listOf(2L, 1L), h.all().map { it.timeMs })
    }

    @Test fun keepsOnlyTheLastN() {
        val h = HistoryStore(Mem(), max = 3)
        (1..5).forEach { h.add(entry(it)) }
        assertEquals(listOf(5L, 4L, 3L), h.all().map { it.timeMs })
    }

    @Test fun persistsAcrossInstances() {
        val mem = Mem()
        HistoryStore(mem).add(entry(7))
        assertEquals(entry(7), HistoryStore(mem).all().single())
    }

    @Test fun clearEmptiesIt() {
        val h = HistoryStore(Mem())
        h.add(entry(1)); h.clear()
        assertTrue(h.all().isEmpty())
    }

    @Test fun removeDeletesOneEntry() {
        val h = HistoryStore(Mem())
        h.add(entry(1)); h.add(entry(2))
        h.remove(1L)
        assertEquals(listOf(2L), h.all().map { it.timeMs })
    }

    @Test fun corruptStorageReadsAsEmpty() {
        val mem = Mem().apply { json = "[{broken" }
        assertTrue(HistoryStore(mem).all().isEmpty())
    }

    @Test fun textWithQuotesAndNewlinesRoundTrips() {
        val mem = Mem()
        val e = HistoryEntry(1, "p", "she said \"hi\"\nthere", "She said \"hi\"\nThere.", "Pasted")
        HistoryStore(mem).add(e)
        assertEquals(e, HistoryStore(mem).all().single())
    }

    @Test fun emptyTextIsNotRecorded() {
        val h = HistoryStore(Mem())
        h.add(HistoryEntry(1, "p", "", "", "SetText"))
        assertTrue(h.all().isEmpty())
    }
}
