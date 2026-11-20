package com.pharrlabs.voice.settings

import android.content.Context
import com.pharrlabs.voice.BuildConfig
import com.pharrlabs.voice.bubble.BubbleIcon
import com.pharrlabs.voice.bubble.BubbleVisibility
import com.pharrlabs.voice.bubble.BubbleStyle
import com.pharrlabs.voice.bubble.BubbleTint
import com.pharrlabs.voice.clean.PeriodMode
import com.pharrlabs.voice.clean.Style
import com.pharrlabs.voice.clean.StylePresets
import com.pharrlabs.voice.history.HistoryStorage
import com.pharrlabs.voice.history.HistoryStore
import org.json.JSONObject

/** SharedPreferences-backed settings and dictionary storage. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("pharr_voice", Context.MODE_PRIVATE)

    var modelName: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString("model", v).apply()

    var bubbleSizeDp: Int
        get() = sp.getInt("bubble_size", 56).coerceIn(40, 80)
        set(v) = sp.edit().putInt("bubble_size", v.coerceIn(40, 80)).apply()

    /** Outline of the floating bubble. */
    var bubbleShape: BubbleStyle
        get() = BubbleStyle.parse(sp.getString("bubble_shape", null))
        set(v) = sp.edit().putString("bubble_shape", v.name).apply()

    /** Master switch for the floating button; flipped by the Quick Settings tile. Not part of backups. */
    var bubbleOn: Boolean
        get() = sp.getBoolean("bubble_on", true)
        set(v) = sp.edit().putBoolean("bubble_on", v).apply()

    /** Epoch millis until which the bubble stays hidden after being dragged to the bottom (0 = not hidden). */
    var bubbleHiddenUntil: Long
        get() = sp.getLong("bubble_hidden_until", 0L)
        set(v) = sp.edit().putLong("bubble_hidden_until", v).apply()

    fun bubbleActive(now: Long = System.currentTimeMillis()) = BubbleVisibility.active(bubbleOn, bubbleHiddenUntil, now)

    /** Checks when Settings opens and periodically in the background. */
    var updateCheck: Boolean
        get() = sp.getBoolean("update_check", true)
        set(v) = sp.edit().putBoolean("update_check", v).apply()

    /** Notification opt-in. Android's notification permission is requested separately. */
    var updateNotifications: Boolean
        get() = sp.getBoolean("update_notifications", false)
        set(v) = sp.edit().putBoolean("update_notifications", v).apply()

    var updateNotifiedVersion: String?
        get() = sp.getString("update_notified_version", null)
        set(v) = sp.edit().putString("update_notified_version", v).apply()

    var updateLastCheck: Long
        get() = sp.getLong("update_last_check", 0L)
        set(v) = sp.edit().putLong("update_last_check", v).apply()

    /** Latest version the site reported, kept so the update action shows without another request. */
    var updateFound: String?
        get() = sp.getString("update_found", null)
        set(v) = sp.edit().putString("update_found", v).apply()

    var updateFoundSizeMb: String
        get() = sp.getString("update_found_size", "").orEmpty()
        set(v) = sp.edit().putString("update_found_size", v).apply()

    var updateSkipped: String?
        get() = sp.getString("update_skipped", null)
        set(v) = sp.edit().putString("update_skipped", v).apply()

    /** Symbol drawn on the floating button. */
    var bubbleTint: BubbleTint
        get() = BubbleTint.parse(sp.getString("bubble_color", null))
        set(v) = sp.edit().putString("bubble_color", v.name).apply()

    /** True once the welcome splash has been shown; not part of backups, so a restored install still gets it once. */
    var splashSeen: Boolean
        get() = sp.getBoolean("splash_seen", false)
        set(v) = sp.edit().putBoolean("splash_seen", v).apply()

    var bubbleIcon: BubbleIcon
        get() = BubbleIcon.parse(sp.getString("bubble_icon", null))
        set(v) = sp.edit().putString("bubble_icon", v.name).apply()

    /** True puts the bubble at the left edge of the field instead of the right. */
    var bubbleLeft: Boolean
        get() = sp.getBoolean("bubble_left", false)
        set(v) = sp.edit().putBoolean("bubble_left", v).apply()

    /** True parks the bubble in the bottom-left corner of the screen (by the navigation bar) instead of by the field. */
    var bubbleCorner: Boolean
        get() = sp.getBoolean("bubble_corner", false)
        set(v) = sp.edit().putBoolean("bubble_corner", v).apply()

    /** Default closing-period behavior; individual apps can override it. */
    var periodMode: PeriodMode
        get() = runCatching { PeriodMode.valueOf(sp.getString("period_mode", null) ?: "Auto") }.getOrDefault(PeriodMode.Auto)
        set(v) = sp.edit().putString("period_mode", v.name).apply()

    /** Spoken editing commands: scratch that, self-corrections, bullet lists. */
    var smartEdit: Boolean
        get() = sp.getBoolean("smart_edit", true)
        set(v) = sp.edit().putBoolean("smart_edit", v).apply()

    /** "capitalize that", "all caps", "lowercase that", "replace X with Y" change what was just dictated. */
    var voiceEdit: Boolean
        get() = sp.getBoolean("voice_edit", true)
        set(v) = sp.edit().putBoolean("voice_edit", v).apply()

    /** How the bubble starts dictation: tap to start/stop, hold to talk, or both. */
    var interaction: Interaction
        get() = Interaction.values().firstOrNull { it.name == sp.getString("interaction", null) } ?: Interaction.Both
        set(v) = sp.edit().putString("interaction", v.name).apply()

    /** Optional: stop a tap-started dictation after this much silence once you have spoken. 0 (default) = keep listening until you tap again. */
    var autoStopMs: Int
        get() = sp.getInt("auto_stop_ms", 0)
        set(v) = sp.edit().putInt("auto_stop_ms", v.coerceAtLeast(0)).apply()

    /** Transcribe finished phrases while you are still talking, so less waiting after you stop. */
    var streaming: Boolean
        get() = sp.getBoolean("streaming", true)
        set(v) = sp.edit().putBoolean("streaming", v).apply()

    /** Show the text recognised so far above the bubble while recording. */
    var liveCaption: Boolean
        get() = sp.getBoolean("live_caption", true)
        set(v) = sp.edit().putBoolean("live_caption", v).apply()

    /** Show Undo / Retry for a few seconds after each dictation. */
    var undoChip: Boolean
        get() = sp.getBoolean("undo_chip", true)
        set(v) = sp.edit().putBoolean("undo_chip", v).apply()

    /** Where the user dragged the bubble, as pixels from its default spot on the field. */
    var bubbleOffsetX: Int
        get() = sp.getInt("bubble_off_x", 0)
        set(v) = sp.edit().putInt("bubble_off_x", v).apply()
    var bubbleOffsetY: Int
        get() = sp.getInt("bubble_off_y", 0)
        set(v) = sp.edit().putInt("bubble_off_y", v).apply()

    /** Bubble opacity (percent) while idle; it is fully opaque while listening and processing. */
    var idleOpacityPct: Int
        get() = sp.getInt("idle_opacity", 90).coerceIn(25, 100)
        set(v) = sp.edit().putInt("idle_opacity", v.coerceIn(25, 100)).apply()

    /** Apps where the bubble never appears. */
    var hiddenApps: Set<String>
        get() = sp.getStringSet("hidden_apps", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("hidden_apps", v).apply()

    /** Offer to remember a word when the user corrects dictated text. */
    /** Write spoken numbers, money, times, dates and phone numbers as digits. */
    var formatNumbers: Boolean
        get() = sp.getBoolean("format_numbers", true)
        set(v) = sp.edit().putBoolean("format_numbers", v).apply()

    var autoLearn: Boolean
        get() = sp.getBoolean("auto_learn", true)
        set(v) = sp.edit().putBoolean("auto_learn", v).apply()

    /** Style for apps with no explicit rule and no built-in preset. */
    var defaultStyle: Style
        get() = Style.values().firstOrNull { it.name == sp.getString("default_style", null) } ?: Style.Standard
        set(v) = sp.edit().putString("default_style", v.name).apply()

    /** Explicit app rule, else the built-in preset, else the global default. */
    fun styleFor(pkg: String): Style = appRules().ruleFor(pkg).style ?: StylePresets.forPackage(pkg) ?: defaultStyle

    private val rulesStorage = object : RulesStorage {
        override fun load(): String? = sp.getString("app_rules", null)
        override fun save(json: String) { sp.edit().putString("app_rules", json).apply() }
    }

    fun appRules() = AppRules(rulesStorage)

    val dictionaryStorage: DictionaryStorage = object : DictionaryStorage {
        override fun load(): Map<String, String> {
            val json = runCatching { JSONObject(sp.getString("dictionary", "{}") ?: "{}") }.getOrNull() ?: return emptyMap()
            return json.keys().asSequence().associateWith { json.getString(it) }
        }

        override fun save(map: Map<String, String>) {
            sp.edit().putString("dictionary", JSONObject(map).toString()).apply()
        }

        override var seeded: Boolean
            get() = sp.getBoolean("dictionary_seeded", false)
            set(v) = sp.edit().putBoolean("dictionary_seeded", v).apply()

        override var seedVersion: Int
            get() = sp.getInt("dictionary_seed_version", if (seeded) 1 else 0)
            set(v) { sp.edit().putInt("dictionary_seed_version", v).putBoolean("dictionary_seeded", v > 0).apply() }
    }

    fun dictionary() = (if (BuildConfig.SHARE) DictionaryStore(dictionaryStorage)
    else DictionaryStore(dictionaryStorage, DictionaryStore.parseSeed(BuildConfig.OWNER_TERMS), DictionaryStore.OWNER_VERSION)).also { it.seedDefaultsOnce() }

    var themeMode: ThemeMode
        get() = ThemeMode.values().firstOrNull { it.name == sp.getString("theme_mode", null) } ?: ThemeMode.System
        set(v) = sp.edit().putString("theme_mode", v.name).apply()

    /** Keep a local log of recent dictations (never leaves the phone). */
    var historyEnabled: Boolean
        get() = sp.getBoolean("history_enabled", true)
        set(v) = sp.edit().putBoolean("history_enabled", v).apply()

    private val historyStorage = object : HistoryStorage {
        override fun load(): String? = sp.getString("history", null)
        override fun save(json: String) { sp.edit().putString("history", json).apply() }
    }

    fun history() = HistoryStore(historyStorage)

    /** Everything except history and the one-time seed flag, as a portable JSON backup. */
    fun exportBackup(): String =
        Backup.encode(sp.all.filterKeys { it in BACKUP_KEYS })

    /** Applies a backup; returns how many settings were restored, or null if the file is not a valid backup. */
    fun importBackup(json: String): Int? {
        val data = Backup.decode(json, BACKUP_KEYS) ?: return null
        val e = sp.edit()
        data.forEach { (k, v) ->
            when (v) {
                is String -> e.putString(k, v)
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
            }
        }
        e.apply()
        return data.size
    }

    enum class Interaction(val label: String) { Both("Tap or hold"), TapOnly("Tap only"), HoldOnly("Hold to talk only") }

    companion object {
        val BACKUP_KEYS = setOf(
            "model", "bubble_size", "bubble_shape", "bubble_icon", "bubble_color", "bubble_left", "bubble_corner", "period_mode", "smart_edit", "voice_edit", "interaction", "auto_stop_ms",
            "streaming", "live_caption", "undo_chip", "bubble_off_x", "bubble_off_y", "idle_opacity", "hidden_apps",
            "auto_learn", "format_numbers", "update_check", "update_notifications", "theme_mode", "default_style", "app_rules", "dictionary", "dictionary_seeded", "history_enabled",
        )
        const val DEFAULT_MODEL = "small.en-q5_1"
        /** Best accuracy for its speed on a phone; flagged in the model list. */
        const val RECOMMENDED_MODEL = "parakeet-tdt-0.6b-v2-int8"
        val MODELS = listOf(
            "tiny.en-q5_1" to "Fastest (tiny, ~32 MB)",
            "base.en-q5_1" to "Fast (base, ~60 MB)",
            "small.en-q5_1" to "Balanced (small, ~190 MB)",
            "parakeet-tdt-0.6b-v2-int8" to "Accurate + quick (Parakeet 0.6B, ~660 MB)",
            "medium.en-q5_0" to "More accurate (medium, ~540 MB, slower)",
        )
    }
}
