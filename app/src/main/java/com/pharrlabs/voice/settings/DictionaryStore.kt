package com.pharrlabs.voice.settings

/** Persistence for the dictionary; the Android implementation is [PrefsDictionaryStorage]. */
interface DictionaryStorage {
    fun load(): Map<String, String>
    fun save(map: Map<String, String>)
    /** True once the starter entries have been written, so deleted defaults do not come back. */
    var seeded: Boolean

    /** Which generation of starter entries has been applied (0 = none). Older installs only have [seeded], which counts as 1. */
    var seedVersion: Int
        get() = if (seeded) 1 else 0
        set(v) { seeded = v > 0 }
}

/** Personal dictionary: spoken phrase (lowercase) to the written form Whisper tends to get wrong. */
class DictionaryStore(
    private val storage: DictionaryStorage,
    private val defaults: Map<String, String> = emptyMap(),
    private val defaultsVersion: Int = 1,
) {
    fun get(): Map<String, String> = storage.load()

    /** Returns false (and stores nothing) if either side is blank. */
    fun put(spoken: String, written: String): Boolean {
        val key = spoken.trim().lowercase()
        val value = written.trim()
        if (key.isEmpty() || value.isEmpty()) return false
        storage.save(storage.load() + (key to value))
        return true
    }

    fun remove(spoken: String) {
        storage.save(storage.load() - spoken.trim().lowercase())
    }

    /** Adds starter entries once per [defaultsVersion]; user entries always win and deleted ones stay deleted until the next version. */
    fun seedDefaultsOnce() {
        if (storage.seedVersion >= defaultsVersion) return
        storage.save(defaults + storage.load())
        storage.seedVersion = defaultsVersion
    }

    companion object {
        /** Version of the owner's starter list; bump when the list in build.gradle.kts gains entries. */
        const val OWNER_VERSION = 2

        /** Parses "spoken=Written|spoken2=Written2". Blank input gives an empty map. */
        fun parseSeed(raw: String): Map<String, String> =
            raw.split('|').mapNotNull { pair ->
                val i = pair.indexOf('=')
                if (i <= 0) null else pair.substring(0, i).trim().lowercase() to pair.substring(i + 1).trim()
            }.filter { it.second.isNotEmpty() }.toMap()
    }
}
