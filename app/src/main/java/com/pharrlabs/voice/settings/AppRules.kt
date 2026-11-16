package com.pharrlabs.voice.settings

import com.pharrlabs.voice.clean.PeriodMode
import com.pharrlabs.voice.clean.Style
import org.json.JSONObject

/** Per-app overrides. Anything left at its default is not stored. */
data class AppRule(
    val period: PeriodMode = PeriodMode.Auto,
    /** null = use the built-in preset for the app, else the global default. */
    val style: Style? = null,
)

interface RulesStorage {
    fun load(): String?
    fun save(json: String)
}

/** Per-app rules keyed by package name, persisted as one JSON object. */
class AppRules(private val storage: RulesStorage) {
    fun all(): Map<String, AppRule> {
        val json = runCatching { JSONObject(storage.load() ?: "{}") }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().mapNotNull { pkg ->
            val o = json.optJSONObject(pkg) ?: return@mapNotNull null
            pkg to AppRule(
                period = enumOr(o.optString("period"), PeriodMode.Auto),
                style = Style.values().firstOrNull { it.name == o.optString("style") },
            )
        }.toMap()
    }

    fun ruleFor(pkg: String): AppRule = all()[pkg] ?: AppRule()

    fun set(pkg: String, rule: AppRule) {
        val map = all().toMutableMap()
        if (rule == AppRule()) map.remove(pkg) else map[pkg] = rule
        val json = JSONObject()
        map.forEach { (p, r) -> json.put(p, JSONObject().put("period", r.period.name).also { o -> r.style?.let { o.put("style", it.name) } }) }
        storage.save(json.toString())
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
