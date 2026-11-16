package com.pharrlabs.voice.clean

/** How a dictation is phrased and punctuated for a given app. */
enum class Style(val label: String) {
    Standard("Standard"),
    /** Texting: the message ends without a closing period (? and ! are kept). */
    Casual("Casual"),
    /** Email/work: contractions and slang are written out. */
    Formal("Formal"),
    /** Terminals, code, URLs: the transcript as heard, only trimmed (dictionary still applies). */
    Literal("Literal"),
}

internal object FormalStyle {
    private val pairs = listOf(
        "can't" to "cannot", "won't" to "will not", "shan't" to "shall not",
        "gonna" to "going to", "wanna" to "want to", "gotta" to "have to", "kinda" to "kind of", "sorta" to "sort of",
        "yeah" to "yes", "yep" to "yes", "nope" to "no", "ok" to "okay",
        "I'm" to "I am", "I'll" to "I will", "I've" to "I have", "I'd" to "I would",
        "n't" to " not", "'re" to " are", "'ll" to " will", "'ve" to " have",
    )
    private val rules = pairs.map { (from, to) ->
        val pattern = if (from.startsWith("'") || from == "n't") """(?<=\w)${Regex.escape(from)}\b""" else """\b${Regex.escape(from)}\b"""
        Regex(pattern, RegexOption.IGNORE_CASE) to to
    }

    fun apply(text: String): String {
        var s = text.replace('\u2019', '\'')
        for ((regex, to) in rules) {
            s = regex.replace(s) { m ->
                val isWord = !to.startsWith(" ")
                if (isWord && m.value.first().isUpperCase() && m.value.length > 1 && !m.value.startsWith("I'")) {
                    to.replaceFirstChar { it.uppercase() }
                } else to
            }
        }
        return s
    }
}
