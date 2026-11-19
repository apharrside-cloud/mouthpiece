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
    /** Untrimmed captured audio; null for dictations saved before timing was added. Audio itself is never saved. */
    val recordingMs: Long? = null,
    /** Elapsed wait after stopping, through transcription, cleanup and insertion. */
    val processingMs: Long? = null,
    /** Model that actually transcribed this dictation, rather than the currently selected model. */
    val modelName: String? = null,
)

interface HistoryStorage {
    fun load(): String?
    fun save(json: String)
}

/** A rolling 30 days, newest first. Reading also removes expired data from local storage. */
class HistoryStore(private val storage: HistoryStorage, private val now: () -> Long = System::currentTimeMillis) {
    fun all(): List<HistoryEntry> = synchronized(lock) { retained() }

    private fun retained(): List<HistoryEntry> {
        val arr = runCatching { JSONArray(storage.load() ?: "[]") }.getOrNull() ?: return emptyList()
        val entries = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HistoryEntry(o.optLong("t"), o.optString("p"), o.optString("r"), o.optString("x"), o.optString("s"),
                o.optLong("d", -1).takeIf { it > 0 }, o.optLong("w", -1).takeIf { it >= 0 },
                o.optString("m").takeIf { !o.isNull("m") && it.isNotBlank() })
        }
        val cutoff = now() - RETENTION_MS
        val kept = entries.filter { it.timeMs > cutoff && it.text.isNotBlank() }.sortedByDescending { it.timeMs }
        if (kept != entries) write(kept)
        return kept
    }

    fun add(entry: HistoryEntry) = synchronized(lock) {
        if (entry.text.isBlank()) return
        val kept = retained()
        if (entry.timeMs > now() - RETENTION_MS) write((listOf(entry) + kept).sortedByDescending { it.timeMs })
    }

    fun remove(timeMs: Long) = synchronized(lock) { write(retained().filterNot { it.timeMs == timeMs }) }

    fun clear() = synchronized(lock) { write(emptyList()) }

    private fun write(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().put("t", e.timeMs).put("p", e.pkg).put("r", e.raw).put("x", e.text).put("s", e.result)
                .put("d", e.recordingMs).put("w", e.processingMs).put("m", e.modelName))
        }
        storage.save(arr.toString())
    }

    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        // Prefs can create separate store instances for the activity, service and cleanup job.
        private val lock = Any()
    }
}
