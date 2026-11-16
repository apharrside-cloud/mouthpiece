package com.pharrlabs.voice.learn

/** A fix the user made to dictated text: [from] (as spoken/transcribed, lowercase) became [to]. */
data class Correction(val from: String, val to: String)

/**
 * Detects "the user fixed one word or short phrase in what I just typed" by diffing the field text right
 * after injection against its current text. Only a single, small, similar-looking replacement counts, so
 * rewrites, deletions and additions never turn into dictionary entries.
 */
object CorrectionDiff {
    private const val MAX_WORDS = 3
    private const val MIN_SIMILARITY = 0.5

    /** @param inserted restricts matches to words that were part of the dictated text (defaults to all of [before]). */
    fun find(before: String, after: String, inserted: String = before): Correction? {
        if (before == after) return null
        val a = words(before)
        val b = words(after)
        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix].norm == b[prefix].norm && a[prefix].raw == b[prefix].raw) prefix++
        var suffix = 0
        while (suffix < a.size - prefix && suffix < b.size - prefix &&
            a[a.size - 1 - suffix].raw == b[b.size - 1 - suffix].raw
        ) suffix++
        val oldSpan = a.subList(prefix, a.size - suffix)
        val newSpan = b.subList(prefix, b.size - suffix)
        if (oldSpan.isEmpty() || newSpan.isEmpty()) return null
        if (oldSpan.size > MAX_WORDS || newSpan.size > MAX_WORDS) return null
        // Two separate edits with unchanged words between them are not one correction.
        if (oldSpan.size == newSpan.size && oldSpan.indices.any { oldSpan[it].raw == newSpan[it].raw }) return null

        val from = oldSpan.joinToString(" ") { it.norm }
        val to = newSpan.joinToString(" ") { it.clean }
        if (from.isEmpty() || to.isEmpty()) return null
        // Punctuation-only edit (the words are the same once punctuation is ignored).
        val caseOnly = from == to.lowercase()
        if (caseOnly && oldSpan.joinToString(" ") { it.clean } == to) return null
        // Auto-capitalised sentence start: a case-only change of the very first word is not vocabulary.
        if (caseOnly && prefix == 0) return null
        if (!words(inserted).map { it.norm }.windowed(oldSpan.size).any { it.joinToString(" ") == from }) return null
        if (!caseOnly && similarity(from, to.lowercase()) < MIN_SIMILARITY) return null
        return Correction(from, to)
    }

    private class Word(val raw: String) {
        val clean: String = raw.trim { !it.isLetterOrDigit() && it != '\'' }
        val norm: String = clean.lowercase()
    }

    private fun words(s: String): List<Word> =
        s.split(Regex("""\s+""")).filter { it.isNotBlank() }.map { Word(it) }

    /** 1 - editDistance / maxLength. */
    internal fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return 1.0 - dp[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
