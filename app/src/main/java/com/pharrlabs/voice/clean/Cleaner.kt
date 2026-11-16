package com.pharrlabs.voice.clean

import com.pharrlabs.voice.field.FieldFormat

/**
 * Rule-based cleanup of raw Whisper output: drops artifacts and filler words, applies spoken
 * punctuation/layout commands and the personal dictionary, then fixes casing and end punctuation.
 */
/** When a finished dictation gets a closing period. */
enum class PeriodMode {
    /** Prose gets one; a lone sentence in a short field (subject, URL bar) does not. */
    Auto,
    Always,
    /** Never end with a period (question and exclamation marks are kept). */
    Never,
}

object Cleaner {
    private val bracketArtifact = Regex("""\[[^\]]*]""")
    private val parenArtifact = Regex(
        """\((?:music|applause|laughter|laughs|silence|noise|inaudible|blank_audio|sighs|coughs)\)""",
        RegexOption.IGNORE_CASE
    )
    private val filler = Regex("""\b(?:um+|uh+|erm|er|hmm+)\b[,.]?\s*""", RegexOption.IGNORE_CASE)

    private val newParagraph = Regex("""[,.]?\s*\bnew paragraph\b[,.]?\s*""", RegexOption.IGNORE_CASE)
    private val newLine = Regex("""[,.]?\s*\bnew line\b[,.]?\s*""", RegexOption.IGNORE_CASE)
    private val punctuationCommands = listOf(
        Regex("""\s*\bquestion mark\b[,.]?\s*""", RegexOption.IGNORE_CASE) to "? ",
        Regex("""\s*\bexclamation (?:mark|point)\b[,.]?\s*""", RegexOption.IGNORE_CASE) to "! ",
        Regex("""\s*\b(?:full stop|period)\b[,.]?\s*""", RegexOption.IGNORE_CASE) to ". ",
        Regex("""\s*\bsemicolon\b[,.]?\s*""", RegexOption.IGNORE_CASE) to "; ",
        Regex("""\s*\bcolon\b[,.]?\s*""", RegexOption.IGNORE_CASE) to ": ",
        Regex("""\s*\bcomma\b[,.]?\s*""", RegexOption.IGNORE_CASE) to ", ",
    )

    private val standaloneI = Regex("""\bi\b(?=$|[^'\w]|'(?:m|ll|ve|d)\b)""")
    private val sentenceStart = Regex("""(^|[.?!]\s+|\n)(\p{Ll})""")

    /**
     * @param singleLine true for one-line fields (subject, URL bar, search): newlines become spaces and a lone
     * sentence is returned without its closing period, which would otherwise be wrong in those fields.
     */
    fun clean(raw: String, dictionary: Map<String, String> = emptyMap(), singleLine: Boolean = false,
        period: PeriodMode = PeriodMode.Auto,
        smartEdit: Boolean = true,
        style: Style = Style.Standard,
        formatNumbers: Boolean = true,
        voiceEdit: Boolean = true,
        format: FieldFormat = FieldFormat.Text,
    ): String {
        if (format != FieldFormat.Text) return FieldFormats.apply(raw, format, dictionary)
        // "..., all caps" / "..., replace X with Y" at the end changes the rest of this same dictation.
        var body = raw
        var after: VoiceEdit.Command? = null
        if (voiceEdit) VoiceEdit.trailing(raw)?.let { (before, cmd) ->
            if (VoiceEdit.applies(cmd, before)) {
                if (cmd is VoiceEdit.Command.Replace) body = VoiceEdit.applyTo(cmd, before) else { body = before; after = cmd }
            }
        }
        val cleaned = cleanCore(body, dictionary, singleLine, period, smartEdit, style, formatNumbers)
        return after?.let { VoiceEdit.applyTo(it, cleaned) } ?: cleaned
    }

    private fun cleanCore(raw: String, dictionary: Map<String, String>, singleLine: Boolean,
        period: PeriodMode, smartEdit: Boolean, style: Style, formatNumbers: Boolean,
    ): String {
        if (style == Style.Literal) return literal(raw, dictionary)
        var s = raw
        s = bracketArtifact.replace(s, " ")
        s = parenArtifact.replace(s, " ")
        s = filler.replace(s, " ")
        s = newParagraph.replace(s, "\n\n")
        s = newLine.replace(s, "\n")
        for ((regex, replacement) in punctuationCommands) s = regex.replace(s, replacement)
        if (smartEdit) s = SmartEdit.apply(s)
        // Web addresses are set aside so the dictionary, number words and capitalization cannot alter them.
        val (masked, urls) = SpokenWeb.extract(s, dictionary)
        s = masked
        for ((spoken, written) in dictionary) {
            if (spoken.isBlank()) continue
            val pattern = Regex("""\b${Regex.escape(spoken.trim())}\b""", RegexOption.IGNORE_CASE)
            s = pattern.replace(s) { written }
        }
        if (formatNumbers) s = NumberFormatter.apply(s)
        if (style == Style.Formal) s = FormalStyle.apply(s)
        s = tidySpacing(s)
        if (s.isEmpty() || s.none { it.isLetterOrDigit() }) return ""
        s = standaloneI.replace(s, "I")
        s = sentenceStart.replace(s) { it.groupValues[1] + it.groupValues[2].uppercase() }
        if (!s.lines().last().startsWith("- ")) s = ensureTerminalPunctuation(s)
        if (singleLine) s = s.replace(Regex("""\s*\n+\s*"""), " ")
        val lone = !s.dropLast(1).contains(Regex("""[.?!…]\s"""))
        val dropPeriod = when (period) {
            PeriodMode.Always -> false
            PeriodMode.Never -> true
            PeriodMode.Auto -> style == Style.Casual || (singleLine && lone)
        }
        if (dropPeriod && s.endsWith(".")) s = s.dropLast(1)
        return SpokenWeb.restore(s, urls)
    }

    private fun literal(raw: String, dictionary: Map<String, String>): String {
        var s = raw.trim()
        for ((spoken, written) in dictionary) {
            if (spoken.isBlank()) continue
            s = Regex("""${Regex.escape(spoken.trim())}""", RegexOption.IGNORE_CASE).replace(s) { written }
        }
        return s
    }

    private fun tidySpacing(input: String): String =
        input
            .replace(Regex("""[ \t]+([,.?!:;])"""), "$1")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n[ \t]+"""), "\n")
            .trim()

    private fun ensureTerminalPunctuation(text: String): String {
        val last = text.last()
        return when {
            last in ".?!…" -> text
            last in ",;:" -> text.dropLast(1) + "."
            else -> "$text."
        }
    }
}
