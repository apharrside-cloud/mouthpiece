package com.pharrlabs.voice.clean

/**
 * Turns spoken numbers into written ones: "twenty five dollars" -> "$25", "three thirty pm" -> "3:30 PM",
 * "january fifth twenty twenty six" -> "January 5th, 2026", "five five five one two three four" -> "555-1234".
 *
 * Single words below ten ("two kids") stay words; ten and up become digits. Pure and idempotent.
 */
object NumberFormatter {
    private enum class K { WORD, NUM, SPACE, OTHER }
    private class Tok(val s: String, val k: K) {
        val lower: String = s.lowercase()
    }

    private val tokenRe = Regex("""[A-Za-z]+(?:'[A-Za-z]+)?|\d+(?:,\d{3})*(?:\.\d+)?|\s+|[\s\S]""")

    private val ones = mapOf(
        "zero" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L,
        "seven" to 7L, "eight" to 8L, "nine" to 9L,
    )
    private val teens = mapOf(
        "ten" to 10L, "eleven" to 11L, "twelve" to 12L, "thirteen" to 13L, "fourteen" to 14L, "fifteen" to 15L,
        "sixteen" to 16L, "seventeen" to 17L, "eighteen" to 18L, "nineteen" to 19L,
    )
    private val tens = mapOf(
        "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L, "sixty" to 60L, "seventy" to 70L,
        "eighty" to 80L, "ninety" to 90L,
    )
    private val scales = mapOf("thousand" to 1_000L, "million" to 1_000_000L, "billion" to 1_000_000_000L)
    private val ordSmall = mapOf(
        "first" to 1L, "second" to 2L, "third" to 3L, "fourth" to 4L, "fifth" to 5L, "sixth" to 6L,
        "seventh" to 7L, "eighth" to 8L, "ninth" to 9L, "tenth" to 10L, "eleventh" to 11L, "twelfth" to 12L,
        "thirteenth" to 13L, "fourteenth" to 14L, "fifteenth" to 15L, "sixteenth" to 16L, "seventeenth" to 17L,
        "eighteenth" to 18L, "nineteenth" to 19L, "twentieth" to 20L, "thirtieth" to 30L, "fortieth" to 40L,
        "fiftieth" to 50L, "sixtieth" to 60L, "seventieth" to 70L, "eightieth" to 80L, "ninetieth" to 90L,
    )
    private val ordScale = mapOf("hundredth" to 100L, "thousandth" to 1_000L, "millionth" to 1_000_000L)
    private val months = listOf(
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
        "november", "december",
    )
    private val digitWords = ones + ("oh" to 0L)

    private fun isNumberWord(w: String) =
        w in ones || w in teens || w in tens || w == "hundred" || w in scales || w in ordSmall || w in ordScale || w == "and"

    // Pass 0: digit times such as "3:30 p.m." or "5pm" (spoken ones are converted in the token pass).
    private val digitTime = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*([AaPp])(?:\.\s?[Mm](\.)?|\s?[Mm]\b)""")

    fun apply(text: String): String {
        if (text.isEmpty()) return text
        return convert(normalizeDigitTimes(text))
    }

    private fun normalizeDigitTimes(text: String): String =
        digitTime.replace(text) { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2]
            if (hour !in 1..12 || (minute.isNotEmpty() && minute.toInt() > 59)) return@replace m.value
            val suffix = if (m.groupValues[3].lowercase() == "a") "AM" else "PM"
            val body = if (minute.isEmpty()) "$hour $suffix" else "$hour:$minute $suffix"
            val dotted = m.groupValues[4].isNotEmpty()
            if (dotted && endsSentence(text, m.range.last + 1)) "$body." else body
        }

    /** True when the text from [from] is the end, a line break, or whitespace followed by an uppercase letter. */
    private fun endsSentence(text: String, from: Int): Boolean {
        if (from >= text.length) return false // end of dictation: the cleaner adds the period itself
        val rest = text.substring(from)
        if (rest.startsWith("\n")) return true
        val m = Regex("""^[ \t]+(\S)""").find(rest) ?: return false
        return m.groupValues[1][0].isUpperCase()
    }

    // ------------------------------------------------------------------ token pass

    private class Span(val end: Int, val value: Long, val ordinal: Boolean, val words: Int, val hadScale: Boolean)

    private lateinit var toks: List<Tok>

    private fun tokenize(s: String): List<Tok> = tokenRe.findAll(s).map {
        val t = it.value
        val k = when {
            t[0].isLetter() -> K.WORD
            t[0].isDigit() -> K.NUM
            t[0].isWhitespace() -> K.SPACE
            else -> K.OTHER
        }
        Tok(t, k)
    }.toList()

    private fun convert(text: String): String {
        val list = tokenize(text)
        val saved = if (::toks.isInitialized) toks else null
        toks = list
        try {
            val sb = StringBuilder()
            var i = 0
            while (i < list.size) {
                val r = handle(i)
                if (r == null) { sb.append(list[i].s); i++ } else { sb.append(r.first); i = r.second }
            }
            return sb.toString()
        } finally {
            if (saved != null) toks = saved
        }
    }

    private fun word(i: Int): String? = toks.getOrNull(i)?.takeIf { it.k == K.WORD }?.lower

    private fun plainSpace(i: Int): Boolean = toks.getOrNull(i)?.let { it.k == K.SPACE && !it.s.contains('\n') } == true

    /** Index of the next word after a plain space (or null). */
    private fun nextWordIdx(i: Int): Int? = if (plainSpace(i) && word(i + 1) != null) i + 1 else null

    private fun handle(i: Int): Pair<String, Int>? {
        val t = toks[i]
        if (t.k == K.NUM) return handleDigits(i)
        if (t.k != K.WORD) return null
        val w = t.lower
        if (w in months) handleDate(i)?.let { return it }
        handleYear(i)?.let { return it }
        handleDigitRun(i)?.let { return it }
        return handleNumber(i)
    }

    // ---- cardinal / ordinal parsing

    /** Collects the run of number-ish words starting at [start]: (word, token index). */
    private fun numberWords(start: Int): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        var i = start
        while (true) {
            val w = word(i) ?: break
            if (!(isNumberWord(w) || (out.isEmpty() && w == "a"))) break
            out += w to i
            val next = i + 1
            i = when {
                plainSpace(next) && word(next + 1) != null -> next + 1
                toks.getOrNull(next)?.s == "-" && word(next + 1) != null -> next + 1
                else -> break
            }
        }
        return out
    }

    private fun parseNumber(start: Int, allowSecond: Boolean = false): Span? {
        val ws = numberWords(start)
        if (ws.isEmpty()) return null
        var total = 0L
        var cur = 0L
        var lastScale = Long.MAX_VALUE
        var hasHundred = false
        var consumed = 0
        var prevBig = false // last consumed was hundred/scale
        var ordinal = false
        var hadScale = false
        var i = 0
        if (ws[0].first == "a") {
            val nxt = ws.getOrNull(1)?.first ?: return null
            if (nxt != "hundred" && nxt !in scales) return null
            cur = 1; i = 1
        }
        loop@ while (i < ws.size) {
            val w = ws[i].first
            val small = ones[w] ?: teens[w] ?: tens[w]
            when {
                small != null -> {
                    if (w == "zero") {
                        if (i == 0) { cur = 0; consumed = 1; prevBig = false }
                        break@loop
                    }
                    val r = cur % 100
                    val ok = when (w) {
                        in tens -> r == 0L
                        in teens -> r == 0L
                        else -> r == 0L || (r >= 20 && r % 10 == 0L)
                    }
                    if (!ok) break@loop
                    cur += small; consumed = i + 1; prevBig = false
                }
                w == "hundred" -> {
                    if (cur !in 1..99 || hasHundred) break@loop
                    cur *= 100; hasHundred = true; consumed = i + 1; prevBig = true
                }
                w in scales -> {
                    val sc = scales.getValue(w)
                    if (cur <= 0 || sc >= lastScale) break@loop
                    total += cur * sc; cur = 0; lastScale = sc; hasHundred = false; hadScale = true
                    consumed = i + 1; prevBig = true
                }
                w == "and" -> {
                    val nx = ws.getOrNull(i + 1)?.first
                    val nextSmall = nx != null && nx != "zero" && (nx in ones || nx in teens || nx in tens)
                    if (!(prevBig && nextSmall)) break@loop
                    // joiner only; consumed advances when the number after it is accepted
                }
                w in ordSmall -> {
                    val v = ordSmall.getValue(w)
                    if (w == "second" && cur > 0 && !allowSecond) break@loop
                    val r = cur % 100
                    val ok = if (v >= 10) r == 0L else r == 0L || (r >= 20 && r % 10 == 0L)
                    if (!ok) break@loop
                    cur += v; consumed = i + 1; ordinal = true
                    break@loop
                }
                w in ordScale -> {
                    val sc = ordScale.getValue(w)
                    if (sc == 100L) {
                        if (cur !in 1..99 || hasHundred) break@loop
                        cur *= 100
                    } else {
                        if (cur <= 0 || sc >= lastScale) break@loop
                        total += cur * sc; cur = 0; hadScale = true
                    }
                    consumed = i + 1; ordinal = true
                    break@loop
                }
                else -> break@loop
            }
            i++
        }
        if (consumed == 0) return null
        return Span(ws[consumed - 1].second + 1, total + cur, ordinal, consumed, hadScale)
    }

    private fun suffix(n: Long): String = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1L -> "st"
        n % 10 == 2L -> "nd"
        n % 10 == 3L -> "rd"
        else -> "th"
    }

    private fun group(n: Long): String = "%,d".format(java.util.Locale.US, n)

    private fun render(sp: Span, forced: Boolean = false): String? {
        if (sp.ordinal) return if (sp.value >= 10 || forced) "${sp.value}${suffix(sp.value)}" else null
        if (sp.value < 10 && !forced) return null
        return if (sp.value in 2001..2099) sp.value.toString() else group(sp.value)
    }

    private fun joinRaw(from: Int, to: Int): String = (from until to).joinToString("") { toks[it].s }

    // ---- point decimals

    /** After an integer span ending at [end]: "point one four" -> (".14", endIdx). */
    private fun decimalTail(end: Int): Pair<String, Int>? {
        if (!plainSpace(end) || word(end + 1) != "point") return null
        var i = end + 2
        val digits = StringBuilder()
        var last = -1
        while (plainSpace(i) && word(i + 1) in digitWords) {
            digits.append(digitWords.getValue(word(i + 1)!!)); last = i + 1; i += 2
        }
        if (digits.isEmpty()) return null
        return ".$digits" to last + 1
    }

    // ---- money / percent

    private fun centsAfter(end: Int): Pair<Long, Int>? {
        var i = end
        if (!plainSpace(i)) return null
        i++
        if (word(i) == "and") { if (!plainSpace(i + 1)) return null; i += 2 }
        val value: Long
        val after: Int
        if (toks.getOrNull(i)?.k == K.NUM) {
            value = toks[i].s.replace(",", "").toLongOrNull() ?: return null; after = i + 1
        } else {
            val sp = parseNumber(i) ?: return null
            if (sp.ordinal) return null
            value = sp.value; after = sp.end
        }
        if (value !in 0..99 || !plainSpace(after)) return null
        if (word(after + 1) != "cents" && word(after + 1) != "cent") return null
        return value to after + 2
    }

    private fun moneyOrPercent(amount: String, end: Int, isInt: Boolean): Pair<String, Int>? {
        if (!plainSpace(end)) return null
        val w = word(end + 1) ?: return null
        when (w) {
            "percent" -> return "$amount%" to end + 2
            "dollars", "dollar", "bucks", "buck" -> {
                var text = "$$amount"
                var next = end + 2
                if (isInt) {
                    centsAfter(next)?.let { (c, e) -> text += ".%02d".format(c); next = e }
                } else if (amount.substringAfter('.').length == 1) text += "0"
                return text to next
            }
        }
        return null
    }

    // ---- digits already written ("25 dollars", "50 percent")

    private fun handleDigits(i: Int): Pair<String, Int>? {
        val raw = toks[i].s
        val isInt = !raw.contains('.')
        // Digits that continue an existing amount/decimal (e.g. "$25 dollars") are left alone.
        if (i > 0 && toks[i - 1].s == "$") return null
        return moneyOrPercent(raw, i + 1, isInt)
    }

    // ---- years

    private fun smallGroup(w: String): Long? = ones[w]?.takeIf { it in 1..9 }

    /** Year like "twenty twenty six" / "nineteen ninety nine" / "twenty oh five" starting at [i]. */
    private fun parseYear(i: Int): Pair<Long, Int>? {
        val first = when (word(i)) { "eighteen" -> 18L; "nineteen" -> 19L; "twenty" -> 20L; else -> return null }
        if (!plainSpace(i + 1)) return null
        val a = word(i + 2) ?: return null
        val second: Long
        var end: Int
        when {
            a == "oh" -> {
                if (!plainSpace(i + 3)) return null
                second = smallGroup(word(i + 4) ?: return null) ?: return null
                end = i + 5
            }
            a in teens -> { second = teens.getValue(a); end = i + 3 }
            a in tens -> {
                var v = tens.getValue(a); end = i + 3
                val sep = if (plainSpace(end) || toks.getOrNull(end)?.s == "-") end + 1 else -1
                if (sep > 0) smallGroup(word(sep) ?: "")?.let { v += it; end = sep + 1 }
                second = v
            }
            else -> return null
        }
        // "twenty twenty six hundred" etc. would be a cardinal, not a year
        if (plainSpace(end) && (word(end + 1) == "hundred" || word(end + 1) in scales)) return null
        return first * 100 + second to end
    }

    private fun handleYear(i: Int): Pair<String, Int>? {
        val (y, end) = parseYear(i) ?: return null
        return y.toString() to end
    }

    // ---- dates

    private fun handleDate(i: Int): Pair<String, Int>? {
        val m = word(i) ?: return null
        if (!plainSpace(i + 1)) return null
        val day = parseNumber(i + 2, allowSecond = true) ?: return null
        if (!day.ordinal || day.value !in 1..31) return null
        val name = m.replaceFirstChar { it.uppercase() }
        var text = "$name ${day.value}${suffix(day.value)}"
        var end = day.end
        if (plainSpace(end)) {
            parseYear(end + 1)?.let { (y, e) -> text += ", $y"; end = e }
                ?: parseNumber(end + 1)?.let { sp ->
                    if (!sp.ordinal && sp.value in 2000..2099 && sp.hadScale) { text += ", ${sp.value}"; end = sp.end }
                }
        }
        return text to end
    }

    // ---- runs of single digits (phone numbers, zip codes)

    private fun handleDigitRun(i: Int): Pair<String, Int>? {
        if (word(i) !in digitWords) return null
        val digits = StringBuilder()
        var j = i
        var last = i
        while (true) {
            val w = word(j) ?: break
            val d = digitWords[w] ?: break
            digits.append(d); last = j
            if (plainSpace(j + 1) && word(j + 2) in digitWords) j += 2 else break
        }
        if (digits.length < 4) return null
        val d = digits.toString()
        val text = when {
            d.length == 7 -> "${d.take(3)}-${d.drop(3)}"
            d.length == 10 -> "${d.take(3)}-${d.substring(3, 6)}-${d.drop(6)}"
            d.length == 11 && d[0] == '1' -> "1-${d.substring(1, 4)}-${d.substring(4, 7)}-${d.drop(7)}"
            else -> d
        }
        return text to last + 1
    }

    // ---- times

    private class AmPm(val text: String, val end: Int, val dotted: Boolean)

    private fun ampmAt(i: Int): AmPm? {
        val w = word(i) ?: return null
        if (w == "am" || w == "pm") return AmPm(w.uppercase(), i + 1, false)
        if (w != "a" && w != "p") return null
        val up = if (w == "a") "AM" else "PM"
        // "a m" / "p m"
        if (plainSpace(i + 1) && word(i + 2) == "m") return AmPm(up, i + 3, false)
        // "a.m." / "p.m"
        if (toks.getOrNull(i + 1)?.s == "." && word(i + 2) == "m") {
            val trailing = toks.getOrNull(i + 3)?.s == "."
            return AmPm(up, if (trailing) i + 4 else i + 3, trailing)
        }
        return null
    }

    private fun minuteAt(i: Int): Pair<String, Int>? {
        if (word(i) == "oh" && plainSpace(i + 1)) {
            val d = smallGroup(word(i + 2) ?: return null) ?: return null
            return "0$d" to i + 3
        }
        val sp = parseNumber(i) ?: return null
        if (sp.ordinal || sp.value !in 10..59) return null
        return sp.value.toString() to sp.end
    }

    private fun tryTime(hour: Long, endOfHour: Int): Pair<String, Int>? {
        if (hour !in 1..12) return null
        if (plainSpace(endOfHour) && word(endOfHour + 1) == "o'clock") return "$hour o'clock" to endOfHour + 2
        if (!plainSpace(endOfHour)) return null
        ampmAt(endOfHour + 1)?.let { ap -> return finishTime("$hour", ap) }
        val (min, afterMin) = minuteAt(endOfHour + 1) ?: return null
        if (!plainSpace(afterMin)) return null
        val ap = ampmAt(afterMin + 1) ?: return null
        return finishTime("$hour:$min", ap)
    }

    private fun finishTime(body: String, ap: AmPm): Pair<String, Int> {
        var end = ap.end
        var text = "$body ${ap.text}"
        if (ap.dotted) {
            // The dotted abbreviation's last period is only a real sentence end before a new sentence.
            val keep = toks.getOrNull(end)?.let { it.k == K.SPACE && toks.getOrNull(end + 1)?.s?.firstOrNull()?.isUpperCase() == true } == true ||
                toks.getOrNull(end)?.s?.contains('\n') == true
            if (keep) text += "."
        }
        return text to end
    }

    // ---- general numbers

    private fun handleNumber(i: Int): Pair<String, Int>? {
        val sp = parseNumber(i) ?: return null
        var end = sp.end
        // "three point one four"
        if (!sp.ordinal) {
            val dec = decimalTail(end)
            if (dec != null) {
                val whole = group(sp.value)
                val amount = whole + dec.first
                moneyOrPercent(amount, dec.second, false)?.let { return it }
                return amount to dec.second
            }
        }
        if (!sp.ordinal) {
            tryTime(sp.value, end)?.let { return it }
            val amount = if (sp.value in 2001..2099) sp.value.toString() else group(sp.value)
            moneyOrPercent(amount, end, true)?.let { return it }
        }
        val r = render(sp) ?: return null
        return r to end
    }
}
