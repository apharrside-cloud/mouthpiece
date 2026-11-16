package com.pharrlabs.voice.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeStorage(var map: Map<String, String> = emptyMap(), override var seeded: Boolean = false) : DictionaryStorage {
    override var seedVersion: Int = if (seeded) 1 else 0
    override fun load() = map
    override fun save(map: Map<String, String>) { this.map = map }
}

class DictionaryStoreTest {
    @Test fun roundTrip() {
        val s = DictionaryStore(FakeStorage())
        assertTrue(s.put("pharr labs", "Pharr Labs"))
        assertEquals(mapOf("pharr labs" to "Pharr Labs"), s.get())
    }

    @Test fun blankKeyRejected() {
        val s = DictionaryStore(FakeStorage())
        assertFalse(s.put("   ", "X"))
        assertFalse(s.put("", "X"))
        assertTrue(s.get().isEmpty())
    }

    @Test fun blankValueRejected() {
        val s = DictionaryStore(FakeStorage())
        assertFalse(s.put("word", "  "))
        assertTrue(s.get().isEmpty())
    }

    @Test fun keysAreCaseInsensitiveAndTrimmed() {
        val s = DictionaryStore(FakeStorage())
        s.put("  Raleigh ", "Raleigh")
        s.put("RALEIGH", "Raleigh NC")
        assertEquals(mapOf("raleigh" to "Raleigh NC"), s.get())
    }

    @Test fun removeIsCaseInsensitive() {
        val s = DictionaryStore(FakeStorage())
        s.put("tracey", "Tracey")
        s.remove("TRACEY ")
        assertTrue(s.get().isEmpty())
    }

    @Test fun persistsThroughStorage() {
        val storage = FakeStorage()
        DictionaryStore(storage).put("a", "A")
        assertEquals(mapOf("a" to "A"), DictionaryStore(storage).get())
    }

    private val starter = mapOf("pharr labs" to "Pharr Labs", "raleigh" to "Raleigh", "tracey" to "Tracey")

    @Test fun seedsDefaultsOnceOnly() {
        val storage = FakeStorage()
        val s = DictionaryStore(storage, starter, 1)
        s.seedDefaultsOnce()
        assertEquals("Pharr Labs", s.get()["pharr labs"])
        assertEquals("Raleigh", s.get()["raleigh"])
        assertEquals("Tracey", s.get()["tracey"])
        s.remove("tracey")
        s.seedDefaultsOnce() // user deleted it: must not come back
        assertFalse(s.get().containsKey("tracey"))
    }

    @Test fun seedKeepsExistingUserEntries() {
        val s = DictionaryStore(FakeStorage(mapOf("raleigh" to "Raleigh, NC")), starter, 1)
        s.seedDefaultsOnce()
        assertEquals("Raleigh, NC", s.get()["raleigh"])
    }

    @Test fun noDefaultsMeansAnEmptyDictionary() {
        val s = DictionaryStore(FakeStorage())
        s.seedDefaultsOnce()
        assertTrue(s.get().isEmpty())
    }

    @Test fun newerSeedVersionAddsOnlyMissingEntries() {
        val storage = FakeStorage(mapOf("raleigh" to "Raleigh, NC"), seeded = true) // seeded by an older version
        val s = DictionaryStore(storage, starter + ("far labs" to "Pharr Labs"), 2)
        s.seedDefaultsOnce()
        assertEquals("Pharr Labs", s.get()["far labs"])
        assertEquals("Raleigh, NC", s.get()["raleigh"]) // the user's own edit wins
    }

    @Test fun sameSeedVersionDoesNotRunTwice() {
        val storage = FakeStorage()
        DictionaryStore(storage, starter, 2).seedDefaultsOnce()
        DictionaryStore(storage, starter, 2).also { it.remove("raleigh") }.seedDefaultsOnce()
        assertFalse(storage.map.containsKey("raleigh"))
    }

    @Test fun parseSeedReadsPairsAndIgnoresJunk() {
        assertEquals(mapOf("far labs" to "Pharr Labs", "x" to "Y"), DictionaryStore.parseSeed("Far Labs=Pharr Labs|x = Y|bad|=no|k="))
        assertTrue(DictionaryStore.parseSeed("").isEmpty())
    }
}
