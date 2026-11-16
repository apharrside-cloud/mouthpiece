package com.pharrlabs.voice.history

import org.json.JSONArray
import org.json.JSONObject

/** One dictation: when, where, what was heard, what was typed and how it got there (SetText, Pasted, ClipboardOnly). */
data class HistoryEntry(
    val timeMs: Long,
    val pkg: String,
    val raw: String,
    val text: String,
    val result: String,
)

interface HistoryStorage {
    fun load(): String?
    fun save(json: String)
}

/** The last few dictations, newest first, so text an app swallowed can be copied back. Stored on the device only. */
class HistoryStore(private val storage: HistoryStorage, private val max: Int = 20) {
    fun all(): List<HistoryEntry> {
        val arr = runCatching { JSONArray(storage.load() ?: "[]") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HistoryEntry(o.optLong("t"), o.optString("p"), o.optString("r"), o.optString("x"), o.optString("s"))
        }
    }

    fun add(entry: HistoryEntry) {
        if (entry.text.isBlank()) return
        write((listOf(entry) + all()).take(max))
    }

    fun remove(timeMs: Long) = write(all().filterNot { it.timeMs == timeMs })

    fun clear() = write(emptyList())

    private fun write(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().put("t", e.timeMs).put("p", e.pkg).put("r", e.raw).put("x", e.text).put("s", e.result))
        }
        storage.save(arr.toString())
    }
}
