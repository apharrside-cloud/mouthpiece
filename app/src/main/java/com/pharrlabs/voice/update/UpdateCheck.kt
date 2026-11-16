package com.pharrlabs.voice.update

import org.json.JSONObject

data class UpdateInfo(val version: String, val sizeMb: String, val date: String)

/** Pure logic for the "a newer version is available" banner; the network fetch lives in [UpdateFetcher]. */
object UpdateCheck {
    const val INTERVAL_MS = 12 * 3_600_000L

    fun parse(json: String): UpdateInfo? = runCatching {
        val o = JSONObject(json)
        val v = o.optString("version").trim()
        if (parts(v) == null) null else UpdateInfo(v, o.optString("size_mb"), o.optString("date"))
    }.getOrNull()

    private fun parts(v: String): List<Int>? {
        if (v.isEmpty()) return null
        val p = v.split('.').map { it.toIntOrNull() ?: return null }
        return p.ifEmpty { null }
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote) ?: return false
        val l = parts(local) ?: return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** True when it has been long enough since [lastCheck] (also when the clock moved backwards). */
    fun due(lastCheck: Long, now: Long): Boolean = lastCheck > now || now - lastCheck >= INTERVAL_MS

    fun available(info: UpdateInfo, installed: String, skipped: String?): Boolean =
        isNewer(info.version, installed) && info.version != skipped
}
