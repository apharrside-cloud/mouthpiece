package com.pharrlabs.voice.clean

/**
 * Spoken commands that change text that was just dictated: "capitalize that", "all caps", "lowercase that" and
 * "replace X with Y". A command is either the whole utterance (it then edits the previous dictation in the field)
 * or the end of an utterance, after a pause or a full stop ("bring the notes, all caps").
 *
 * Deliberately strict, like [SmartEdit]: the end-of-utterance form needs the separator, and a "replace" is only
 * honoured when the word it targets is actually in the text, so ordinary sentences that happen to contain these
 * words ("I typed it in all caps", "replace the filter with a new one") are left alone.
 */
object VoiceEdit {
    sealed interface Command {
        data object Capitalize : Command
        data object AllCaps : Command
        data object Lowercase : Command
        data class Replace(val from: String, val to: String) : Command
    }

    private const val CAP = """capitali[sz]e\s+(?:that|this|it)"""
    private const val ALL_CAPS = """(?:make\s+(?:that|it)\s+)?(?:all[\s-]*caps|all\s+capital\s+letters|upper[\s-]?case)(?:\s+(?:that|it))?"""
    private const val LOWER = """(?:make\s+(?:that|it)\s+)?(?:all\s+)?lower[\s-]?case(?:\s+(?:that|it))?"""
    private const val REPLACE = """replace\s+(.+?)\s+with\s+(.+?)"""
    private val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val standaloneRe = Regex("""^(?:(?<cap>$CAP)|(?<all>$ALL_CAPS)|(?<low>$LOWER)|$REPLACE)$""", opts)
    private val trailingRe = Regex(
        """^(.*?)([,.;:!?])\s+(?:(?<cap>$CAP)|(?<all>$ALL_CAPS)|(?<low>$LOWER)|$REPLACE)[\s.!?]*$""", opts,
    )
    private val trimTail = Regex("""[\s.!?,;:]+$""")

    /** The command when the whole [raw] utterance is one, else null. */
    fun standalone(raw: String): Command? {
        val s = raw.trim().replace(trimTail, "")
        if (s.isEmpty()) return null
        val m = standaloneRe.matchEntire(s) ?: return null
        return command(m, groupBase = 3)
    }

    /** The text before a command at the end of [raw] and that command, or null. */
    fun trailing(raw: String): Pair<String, Command>? {
        val m = trailingRe.matchEntire(raw.trim()) ?: return null
        val sep = m.groupValues[2]
        val before = (m.groupValues[1] + if (sep in listOf(".", "!", "?")) sep else "").trim()
        if (before.none { it.isLetterOrDigit() }) return null
        return before to command(m, groupBase = 5)
    }

    private fun command(m: MatchResult, groupBase: Int): Command {
        val g = m.groups
        return when {
            g["cap"] != null -> Command.Capitalize
            g["all"] != null -> Command.AllCaps
            g["low"] != null -> Command.Lowercase
            else -> Command.Replace(
                m.groupValues[groupBase + 1].trim().trim(',', ';', ':'),
                m.groupValues[groupBase + 2].trim().replace(trimTail, ""),
            )
        }
    }

    /** False for a "replace" whose target is not in [text]; such an utterance is plain dictation. */
    fun applies(cmd: Command, text: String): Boolean =
        cmd !is Command.Replace || cmd.from.isNotEmpty() && matcher(cmd.from).containsMatchIn(text)

    fun applyTo(cmd: Command, text: String): String = when (cmd) {
        Command.Capitalize -> Regex("""(^|\s)(\p{Ll})""").replace(text) { it.groupValues[1] + it.groupValues[2].uppercase() }
        Command.AllCaps -> text.uppercase()
        Command.Lowercase -> text.lowercase()
        is Command.Replace -> matcher(cmd.from).replace(text) { m ->
            val atSentenceStart = m.range.first == 0 || Regex("""[.?!]\s+$""").containsMatchIn(text.substring(0, m.range.first))
            if (atSentenceStart && cmd.to.firstOrNull()?.isLowerCase() == true) cmd.to.replaceFirstChar { it.uppercase() } else cmd.to
        }
    }

    private fun matcher(word: String) =
        Regex("""(?<![\p{L}\p{N}])${Regex.escape(word)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
}
