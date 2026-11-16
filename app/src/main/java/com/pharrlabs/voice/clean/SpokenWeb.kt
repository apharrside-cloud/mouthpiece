package com.pharrlabs.voice.clean

/**
 * Web addresses. Whisper writes "voice dot pharrlabs dot com" as words (or as "Voice.FarLabs.com"); this turns
 * both into a lowercase address and runs each label through the personal dictionary, so a name the user taught
 * ("far labs" -> "Pharr Labs") comes out as "pharrlabs" inside a domain.
 *
 * [extract] swaps found addresses for placeholders so later steps (dictionary, capitalization, number words)
 * cannot damage them; [restore] puts them back.
 */
object SpokenWeb {
    private const val TLDS = "com|org|net|io|app|dev|co|us|edu|gov|ai|me|info|xyz|tv|uk|ca"
    private const val SEG = """[A-Za-z0-9-]+(?:[ \t]+[A-Za-z0-9-]+)?"""
    private val spoken = Regex("""(?i)\b(?:$SEG[ \t]+dot[ \t]+)+(?:$TLDS)\b((?:[ \t]+slash[ \t]+[\w.-]+)*)""")
    private val literal = Regex("""(?i)\b(?:[a-z0-9-]+\.)+(?:$TLDS)\b(?![\w@-])""")
    private val dotSplit = Regex("""(?i)[ \t]+dot[ \t]+""")
    private val slashSplit = Regex("""(?i)[ \t]+slash[ \t]+""")
    private val ws = Regex("""[ \t]+""")

    /** Words that can sit before "dot com" in ordinary speech ("the dot com bubble"). */
    private val notALabel = setOf(
        "a", "an", "the", "this", "that", "these", "those", "my", "your", "our", "his", "her", "its", "their",
        "of", "in", "on", "at", "to", "for", "and", "or", "but", "is", "was", "are", "no", "any", "every",
    )

    fun apply(text: String, dictionary: Map<String, String>): String {
        val (masked, urls) = extract(text, dictionary)
        return restore(masked, urls)
    }

    fun extract(text: String, dictionary: Map<String, String>): Pair<String, List<String>> {
        val labels = labelMap(dictionary)
        val urls = ArrayList<String>()
        val joined = spoken.replace(text) { m -> joinSpoken(m, labels) }
        val masked = literal.replace(joined) { m ->
            urls += mapLabels(m.value, labels)
            placeholder(urls.size - 1)
        }
        return masked to urls
    }

    fun restore(text: String, urls: List<String>): String {
        var s = text
        for (i in urls.indices.reversed()) s = s.replace(placeholder(i), urls[i])
        return s
    }

    private fun placeholder(i: Int) = "\uE000xq" + i.toString().map { 'a' + (it - '0') }.joinToString("") + "\uE001"

    /** "far labs" -> "pharrlabs": every dictionary entry as a single lowercase label. */
    private fun labelMap(dictionary: Map<String, String>): Map<String, String> {
        val out = HashMap<String, String>()
        for ((k, v) in dictionary) {
            val label = v.lowercase().replace(" ", "")
            if (label.matches(Regex("""[a-z0-9-]+"""))) out[k.lowercase().replace(" ", "")] = label
        }
        return out
    }

    private fun mapLabels(domain: String, labels: Map<String, String>): String {
        val parts = domain.lowercase().split('.')
        return parts.mapIndexed { i, p -> if (i < parts.size - 1) labels[p] ?: p else p }.joinToString(".")
    }

    private fun joinSpoken(m: MatchResult, labels: Map<String, String>): String {
        val path = m.groupValues[1]
        val core = m.value.removeSuffix(path)
        val parts = core.split(dotSplit)
        val firstWords = parts.first().trim().split(ws)
        if (firstWords.last().lowercase() in notALabel) return m.value
        var prefix = ""
        val first: String
        if (firstWords.size == 2) {
            val both = (firstWords[0] + firstWords[1]).lowercase()
            if (both in labels || both in labels.values) {
                first = both
            } else {
                prefix = firstWords[0] + " "
                first = firstWords[1].lowercase()
            }
        } else first = firstWords[0].lowercase()
        val rest = parts.drop(1).map { it.trim().split(ws).joinToString("").lowercase() }
        val domain = (listOf(first) + rest).joinToString(".")
        val tail = if (path.isBlank()) "" else slashSplit.split(path).drop(1).joinToString("") { "/" + it.trim().lowercase() }
        return prefix + domain + tail
    }
}
