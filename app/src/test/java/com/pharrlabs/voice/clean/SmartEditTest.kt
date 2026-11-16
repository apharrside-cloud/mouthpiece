package com.pharrlabs.voice.clean

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartEditTest {
    @Test fun scratchThatDropsPreviousSentence() =
        assertEquals("Send the report. Thanks.", SmartEdit.apply("Send the report. Call John. Scratch that. Thanks."))

    @Test fun scratchThatWithNoPriorSentenceDropsPriorClause() =
        assertEquals("", SmartEdit.apply("Call John scratch that").trim())

    @Test fun scratchThatMidSentenceDropsFromSentenceStart() =
        assertEquals("Okay.", SmartEdit.apply("Meet at noon scratch that. Okay."))

    @Test fun restartRephraseKeepsLaterVersion() =
        assertEquals("I want to go to the park.", SmartEdit.apply("I want to go to the store, no wait, I want to go to the park."))

    @Test fun singleWordCorrectionReplacesLastWord() =
        assertEquals("Let's meet at four.", SmartEdit.apply("Let's meet at three, no wait, four."))

    @Test fun iMeanCorrection() =
        assertEquals("Send it to Sarah.", SmartEdit.apply("Send it to Susan, I mean Sarah."))

    @Test fun ambiguousCorrectionKeepsBothAndDropsMarker() =
        assertEquals("Let's meet at three, four o'clock.", SmartEdit.apply("Let's meet at three, no wait, four o'clock."))

    @Test fun bulletPointsBecomeList() =
        assertEquals("Groceries:\n- Milk\n- Eggs\n- Bread", SmartEdit.apply("Groceries: bullet point milk bullet point eggs bullet point bread"))

    @Test fun nextBulletAlias() =
        assertEquals("- Milk\n- Eggs", SmartEdit.apply("bullet point milk next bullet eggs"))

    @Test fun plainTextUntouched() =
        assertEquals("I mean business when I say it.", SmartEdit.apply("I mean business when I say it."))

    @Test fun iMeanAtSentenceStartUntouched() =
        assertEquals("It works. I mean it.", SmartEdit.apply("It works. I mean it."))

    @Test fun emptyStaysEmpty() = assertEquals("", SmartEdit.apply(""))
}
