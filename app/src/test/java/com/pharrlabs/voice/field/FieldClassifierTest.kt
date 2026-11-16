package com.pharrlabs.voice.field

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldClassifierTest {
    private fun kind(
        viewId: String? = null, hint: String? = null, desc: String? = null,
        inputType: Int = 1, pkg: String = "com.example",
    ) = FieldClassifier.classify(FieldInfo(viewId, hint, desc, inputType, pkg))

    @Test fun gmailSubjectIsShort() = assertEquals(FieldKind.Short, kind(hint = "Subject", inputType = 442417))

    @Test fun gmailBodyIsProse() = assertEquals(FieldKind.Prose, kind(viewId = "editor", hint = "", inputType = 0))

    @Test fun messagesComposeIsProse() = assertEquals(FieldKind.Prose, kind(hint = "Text message", inputType = 147457))

    @Test fun chromeOmniboxByViewId() =
        assertEquals(FieldKind.Short, kind(viewId = "com.android.chrome:id/url_bar", inputType = 655361))

    @Test fun searchHintIsShort() = assertEquals(FieldKind.Short, kind(hint = "Search Keep"))

    @Test fun uriVariationIsShort() = assertEquals(FieldKind.Short, kind(inputType = 0x11 /* text | uri */))

    @Test fun emailAddressVariationIsShort() = assertEquals(FieldKind.Short, kind(inputType = 0x21))

    @Test fun personNameVariationIsShort() = assertEquals(FieldKind.Short, kind(inputType = 0x61))

    @Test fun titleViewIdIsShort() = assertEquals(FieldKind.Short, kind(viewId = "com.x:id/note_title"))

    @Test fun contentDescriptionSearchIsShort() = assertEquals(FieldKind.Short, kind(desc = "Search or type web address"))

    @Test fun plainMultilineTextIsProse() = assertEquals(FieldKind.Prose, kind(inputType = 0x20001))

    @Test fun emailWordInBodyHintDoesNotCount() = assertEquals(FieldKind.Prose, kind(hint = "Compose email"))

    private fun format(inputType: Int) = FieldClassifier.format(FieldInfo(null, null, null, inputType, "com.example"))

    @Test fun emailFieldFormat() = assertEquals(FieldFormat.Email, format(0x21))

    @Test fun webEmailFieldFormat() = assertEquals(FieldFormat.Email, format(0xD1))

    @Test fun phoneFieldFormat() = assertEquals(FieldFormat.Phone, format(0x3))

    @Test fun numberFieldFormat() = assertEquals(FieldFormat.Number, format(0x2))

    @Test fun decimalNumberFieldFormat() = assertEquals(FieldFormat.Number, format(0x2 or 0x2000))

    @Test fun plainAndUriAndMultilineAreText() {
        assertEquals(FieldFormat.Text, format(0x1))
        assertEquals(FieldFormat.Text, format(0x11))
        assertEquals(FieldFormat.Text, format(0x20001))
        assertEquals(FieldFormat.Text, format(0))
    }
}
