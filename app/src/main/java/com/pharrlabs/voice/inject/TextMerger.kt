package com.pharrlabs.voice.inject

import kotlin.math.max
import kotlin.math.min

data class Merged(val text: String, val cursor: Int)

object TextMerger {
    /**
     * Replaces [selStart, selEnd) of [existing] with [insert], adding a leading space when the text
     * before the insertion point ends in a non-space and [insert] starts with a letter/digit, and a
     * trailing space when the text after starts with a letter/digit. A negative selection (unknown)
     * appends at the end. [Merged.cursor] is the end of the inserted text.
     */
    fun merge(existing: String, selStart: Int, selEnd: Int, insert: String): Merged {
        val (from, to) = range(existing, selStart, selEnd)
        val before = existing.substring(0, from)
        val after = existing.substring(to)
        val leading = before.isNotEmpty() && !before.last().isWhitespace() &&
            insert.firstOrNull()?.isLetterOrDigit() == true
        val trailing = after.isNotEmpty() && after.first().isLetterOrDigit() &&
            insert.isNotEmpty() && !insert.last().isWhitespace()
        val inserted = (if (leading) " " else "") + insert
        val text = before + inserted + (if (trailing) " " else "") + after
        return Merged(text, before.length + inserted.length)
    }

    /** Normalized [from, to) of the selection, clamped to the text; unknown means "at the end". */
    fun range(existing: String, selStart: Int, selEnd: Int): Pair<Int, Int> {
        if (selStart < 0 || selEnd < 0) return existing.length to existing.length
        val a = min(selStart, selEnd).coerceIn(0, existing.length)
        val b = max(selStart, selEnd).coerceIn(0, existing.length)
        return a to b
    }
}
