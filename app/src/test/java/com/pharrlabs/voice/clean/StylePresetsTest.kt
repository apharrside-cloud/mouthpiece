package com.pharrlabs.voice.clean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StylePresetsTest {
    @Test fun messagesIsCasual() = assertEquals(Style.Casual, StylePresets.forPackage("com.google.android.apps.messaging"))
    @Test fun gmailIsFormal() = assertEquals(Style.Formal, StylePresets.forPackage("com.google.android.gm"))
    @Test fun termuxIsLiteral() = assertEquals(Style.Literal, StylePresets.forPackage("com.termux"))
    @Test fun unknownAppHasNoPreset() = assertNull(StylePresets.forPackage("com.unknown.app"))
}
