package com.pharrlabs.voice.inject

/**
 * Some apps (Telegram's "Message", Gmail's "Compose email") expose their grey placeholder as real text in an
 * empty field and never set the hint flag. Treating it as existing text made every dictation start with that word.
 */
object Placeholders {
    private val KNOWN = setOf(
        "compose email", "message", "type a message", "write a message", "text message", "send a message", "imessage",
    )

    /** True when [text] is just the field's placeholder, so the field is really empty. */
    fun isPlaceholder(text: String?, hint: String?, showingHint: Boolean): Boolean {
        if (showingHint) return true
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return false
        if (t.lowercase().trimEnd('.', '\u2026') in KNOWN) return true
        val h = hint?.trim().orEmpty()
        return h.isNotEmpty() && t.equals(h, ignoreCase = true)
    }
}
