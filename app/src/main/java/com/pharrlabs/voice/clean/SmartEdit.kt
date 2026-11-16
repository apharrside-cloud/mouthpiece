package com.pharrlabs.voice.clean

/**
 * Spoken editing commands applied to raw transcript before [Cleaner]'s punctuation pass:
 * "scratch that", self-corrections ("no wait", "I mean") and "bullet point" lists.
 *
 * Deliberately conservative: when a correction is ambiguous both versions are kept (only the marker is
 * dropped), because silently deleting words the speaker meant to keep is worse than leaving a self-correction in.
 */
object SmartEdit {
    private val scratch = Regex("""[,.]?\s*\b(?:scratch that|delete that|strike that)\b[,.]?\s*""", RegexOption.IGNORE_CASE)
    private val correction = Regex("""(?:\s*,\s*\bI mean\b|\s*,?\s*\b(?:no,? wait|wait,? no|or rather)\b),?\s+""", RegexOption.IGNORE_CASE)
    private val bullet = Regex("""\s*\b(?:bullet point|next bullet|new bullet)\b[,.:]?\s*""", RegexOption.IGNORE_CASE)

    fun apply(input: String): String {
        var s = input
        s = applyScratch(s)
        s = applyCorrections(s)
        s = applyBullets(s)
        return s.trim()
    }

    private fun applyScratch(input: String): String {
        var s = input
        while (true) {
            val m = scratch.find(s) ?: return s
            val before = s.substring(0, m.range.first)
            // Sentence containing the command: back to the previous terminator (or the start).
            val cut = before.lastIndexOfAny(charArrayOf('.', '?', '!')).let { if (it < 0) 0 else it + 1 }
            val kept = if (before.isNotBlank() && before.trimEnd().lastOrNull() in listOf('.', '?', '!')) {
                // Command stands alone after a finished sentence: drop that whole sentence.
                val trimmed = before.trimEnd().dropLast(1)
                val prevEnd = trimmed.lastIndexOfAny(charArrayOf('.', '?', '!')).let { if (it < 0) 0 else it + 1 }
                before.substring(0, prevEnd)
            } else before.substring(0, cut)
            s = (kept.trimEnd() + " " + s.substring(m.range.last + 1).trimStart()).trim()
        }
    }

    private fun applyCorrections(input: String): String {
        var s = input
        var guard = 0
        while (guard++ < 10) {
            val m = correction.find(s) ?: return s
            val before = s.substring(0, m.range.first)
            // A marker at the start of the text or of a sentence ("It works. Sorry, I mean it.") is ordinary speech.
            if (before.isBlank() || before.trimEnd().lastOrNull() in listOf('.', '?', '!')) return s
            val afterRaw = s.substring(m.range.last + 1)
            val clauseEnd = afterRaw.indexOfAny(charArrayOf(',', '.', '?', '!')).let { if (it < 0) afterRaw.length else it }
            val fix = afterRaw.substring(0, clauseEnd).trim()
            val tail = afterRaw.substring(clauseEnd)
            val fixWords = fix.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (fixWords.isEmpty()) { s = before + tail; continue }
            val sentStart = before.lastIndexOfAny(charArrayOf('.', '?', '!')).let { if (it < 0) 0 else it + 1 }
            val clause = before.substring(sentStart)
            val clauseWords = Regex("""\S+""").findAll(clause).toList()
            val first = fixWords.first().lowercase().trim(',', ';')
            // Restart: the correction begins by repeating an earlier word of the clause -> replace from there.
            val restart = if (fixWords.size >= 2) clauseWords.indexOfFirst { it.value.lowercase().trim(',', ';') == first } else -1
            val replaced = when {
                restart >= 0 -> before.substring(0, sentStart + clauseWords[restart].range.first) + fix
                fixWords.size == 1 && clauseWords.isNotEmpty() ->
                    before.substring(0, sentStart + clauseWords.last().range.first) + fix
                else -> null
            }
            s = if (replaced != null) replaced + tail
            else before.trimEnd().trimEnd(',') + ", " + fix + tail
            // Re-scan from scratch; markers inside replaced text were consumed.
        }
        return s
    }

    private fun applyBullets(input: String): String {
        if (!bullet.containsMatchIn(input)) return input
        val parts = input.split(bullet)
        val head = parts.first().trim()
        val items = parts.drop(1).map { it.trim().trimEnd('.', ',') }.filter { it.isNotEmpty() }
            .map { "- " + it.replaceFirstChar { c -> c.uppercase() } }
        return (if (head.isEmpty()) items else listOf(head) + items).joinToString("\n")
    }
}
