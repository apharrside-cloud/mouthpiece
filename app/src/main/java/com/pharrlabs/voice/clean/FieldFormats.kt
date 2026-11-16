package com.pharrlabs.voice.clean

import com.pharrlabs.voice.field.FieldFormat

/**
 * Text for fields that expect a fixed form. Prose rules (capital letters, closing periods, number words that stay
 * words) are wrong there, so these fields bypass [Cleaner]: "sam at gmail dot com" becomes sam@gmail.com in an
 * email box, spoken digits become a phone number in a phone box, and a number box gets plain digits.
 */
object FieldFormats {
    private val digitWords = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
    )
    private val emailSymbols = mapOf("dot" to ".", "underscore" to "_", "dash" to "-", "hyphen" to "-", "plus" to "+")
    private val ws = Regex("""\s+""")
    private val trailing = Regex("""[\s.!?,;:]+$""")

    fun apply(raw: String, format: FieldFormat, dictionary: Map<String, String>): String = when (format) {
        FieldFormat.Text -> raw
        FieldFormat.Email -> email(raw, dictionary)
        FieldFormat.Phone -> phone(raw)
        FieldFormat.Number -> number(raw)
    }

    private fun email(raw: String, dictionary: Map<String, String>): String {
        var s = raw.trim().replace(trailing, "").replace(",", " ").lowercase()
        // A name the user taught ("far labs" -> "Pharr Labs") becomes one lowercase label, as inside a web address.
        for ((spoken, written) in dictionary) {
            val label = written.lowercase().replace(" ", "")
            if (spoken.isBlank() || !label.matches(Regex("""[a-z0-9-]+"""))) continue
            s = Regex("""\b${Regex.escape(spoken.trim().lowercase())}\b""").replace(s, label)
        }
        val tokens = s.split(ws).filter { it.isNotEmpty() }.map { emailSymbols[it] ?: digitWords[it] ?: it }.toMutableList()
        if (tokens.none { it.contains('@') }) {
            // The last "at" is the separator, so a name like "pat" or a word "at" earlier on is not mistaken for it.
            val at = tokens.indexOfLast { it == "at" }
            if (at > 0 && at < tokens.lastIndex) tokens[at] = "@"
        }
        return tokens.joinToString("")
    }

    private fun phone(raw: String): String {
        val s = raw.trim().replace(trailing, "")
        val tokens = s.lowercase().split(ws).filter { it.isNotEmpty() }
            .map { if (it == "oh") "0" else if (it == "plus") "+" else digitWords[it] ?: it }
        if (tokens.isNotEmpty() && tokens.all { it.length == 1 && (it[0].isDigit() || it == "+") }) {
            val joined = tokens.joinToString("")
            if (joined.startsWith("+")) return joined
            return when {
                joined.length == 7 -> joined.substring(0, 3) + "-" + joined.substring(3)
                joined.length == 10 -> joined.substring(0, 3) + "-" + joined.substring(3, 6) + "-" + joined.substring(6)
                joined.length == 11 && joined[0] == '1' ->
                    "1-" + joined.substring(1, 4) + "-" + joined.substring(4, 7) + "-" + joined.substring(7)
                else -> joined
            }
        }
        return s.filter { it.isDigit() || it in "+-()*# " }.trim()
    }

    private fun number(raw: String): String {
        var s = NumberFormatter.apply(raw.trim().replace(trailing, "").lowercase())
        s = Regex("""[a-z]+""").replace(s) { m ->
            when (val w = m.value) {
                "point" -> "."
                "minus", "negative" -> "-"
                else -> digitWords[w] ?: w
            }
        }
        s = Regex("""(?<=\d)\s*\.\s*(?=\d)""").replace(s, ".")
        s = Regex("""-\s+(?=\d)""").replace(s, "-")
        return s.filter { it.isDigit() || it == '.' || it == '-' }
    }
}
