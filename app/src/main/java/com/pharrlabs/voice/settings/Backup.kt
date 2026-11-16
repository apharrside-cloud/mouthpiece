package com.pharrlabs.voice.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings export/import as one small JSON file, so a phone swap or reinstall keeps the dictionary, per-app
 * rules and preferences. History is deliberately not included.
 */
object Backup {
    const val APP = "pharr-voice"
    const val VERSION = 1

    fun encode(settings: Map<String, Any?>): String {
        val out = JSONObject()
        settings.forEach { (k, v) ->
            when (v) {
                is String -> out.put(k, JSONObject().put("t", "string").put("v", v))
                is Int -> out.put(k, JSONObject().put("t", "int").put("v", v))
                is Boolean -> out.put(k, JSONObject().put("t", "bool").put("v", v))
                is Set<*> -> out.put(k, JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() })))
                else -> Unit // null or unsupported types are not exported
            }
        }
        return JSONObject().put("app", APP).put("version", VERSION).put("settings", out).toString(2)
    }

    /** Parsed settings restricted to [allowedKeys], or null if this is not a backup this version can read. */
    fun decode(json: String, allowedKeys: Set<String>): Map<String, Any?>? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optString("app") != APP || root.optInt("version", Int.MAX_VALUE) > VERSION) return null
        val settings = root.optJSONObject("settings") ?: return null
        val result = linkedMapOf<String, Any?>()
        for (k in settings.keys()) {
            if (k !in allowedKeys) continue
            val e = settings.optJSONObject(k) ?: continue
            val value: Any? = runCatching {
                when (e.getString("t")) {
                    "string" -> e.getString("v")
                    "int" -> e.get("v").let { if (it is Int) it else error("not an int") }
                    "bool" -> e.get("v").let { if (it is Boolean) it else error("not a bool") }
                    "set" -> e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                    else -> null
                }
            }.getOrNull()
            if (value != null) result[k] = value
        }
        return result
    }
}
