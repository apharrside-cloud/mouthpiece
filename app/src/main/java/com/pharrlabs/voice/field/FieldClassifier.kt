package com.pharrlabs.voice.field

import android.text.InputType

/** What an accessibility node tells us about a text field, kept free of framework types so it is unit-testable. */
data class FieldInfo(
    val viewId: String?,
    val hint: String?,
    val contentDescription: String?,
    val inputType: Int,
    val packageName: String,
)

enum class FieldKind {
    /** Messages, email bodies, notes: full sentences with punctuation. */
    Prose,
    /** Subject lines, search boxes, URL bars, names: short text where a trailing period is wrong. */
    Short,
}

/** What a field expects to receive, beyond prose: addresses, phone numbers and numbers have a fixed form. */
enum class FieldFormat { Text, Email, Phone, Number }

/**
 * Apps often declare every field multi-line (Gmail's subject and Chrome's address bar both do), so the
 * kind is inferred from the input-type variation, the view id and the hint/description text instead.
 */
object FieldClassifier {
    private val shortWords = Regex("""\b(subject|search|url|web address|address bar|title|query|username|user name)\b""")

    private val shortVariations = setOf(
        InputType.TYPE_TEXT_VARIATION_URI,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
        InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
        InputType.TYPE_TEXT_VARIATION_FILTER,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    )

    fun classify(info: FieldInfo): FieldKind {
        if ((info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (info.inputType and InputType.TYPE_MASK_VARIATION) in shortVariations
        ) return FieldKind.Short
        val text = listOfNotNull(info.viewId, info.hint, info.contentDescription)
            .joinToString(" ") { it.lowercase().replace(Regex("[^a-z]+"), " ") }
        return if (shortWords.containsMatchIn(text)) FieldKind.Short else FieldKind.Prose
    }

    /** The structured format the field's input type asks for; [FieldFormat.Text] for anything else. */
    fun format(info: FieldInfo): FieldFormat {
        val type = info.inputType
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_PHONE -> FieldFormat.Phone
            InputType.TYPE_CLASS_NUMBER -> FieldFormat.Number
            InputType.TYPE_CLASS_TEXT -> when (type and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldFormat.Email
                else -> FieldFormat.Text
            }
            else -> FieldFormat.Text
        }
    }
}
