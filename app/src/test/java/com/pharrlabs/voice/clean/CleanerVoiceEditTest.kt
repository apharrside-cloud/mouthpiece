package com.pharrlabs.voice.clean

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanerVoiceEditTest {
    @Test fun trailingAllCaps() =
        assertEquals("BRING THE NOTES.", Cleaner.clean("Bring the notes, all caps."))

    @Test fun trailingCapitalize() =
        assertEquals("Bring The Notes.", Cleaner.clean("bring the notes, capitalize that"))

    @Test fun trailingLowercase() =
        assertEquals("see you soon.", Cleaner.clean("See you soon. Lowercase that."))

    @Test fun trailingReplace() =
        assertEquals("Meet at one.", Cleaner.clean("Meet at noon, replace noon with one."))

    @Test fun trailingReplaceOfAnAbsentWordLeavesTheText() =
        assertEquals("Meet at noon, replace lunch with one.", Cleaner.clean("Meet at noon, replace lunch with one."))

    @Test fun replacementGoesThroughTheDictionary() =
        assertEquals("Ask Tracey.", Cleaner.clean("Ask her, replace her with tracey.", mapOf("tracey" to "Tracey")))

    @Test fun ordinaryUseOfTheWordsIsUntouched() =
        assertEquals("I typed it in all caps.", Cleaner.clean("I typed it in all caps"))

    @Test fun switchedOffLeavesEverythingAsSpoken() =
        assertEquals("Bring the notes, all caps.", Cleaner.clean("Bring the notes, all caps.", voiceEdit = false))

    @Test fun allCapsSurvivesTheNumberFormatter() =
        assertEquals("CALL 555-1234.", Cleaner.clean("call five five five one two three four, all caps."))

    @Test fun emailFieldBypassesProseRules() =
        assertEquals("sam@gmail.com", Cleaner.clean("Sam at gmail dot com.", format = com.pharrlabs.voice.field.FieldFormat.Email))

    @Test fun phoneFieldBypassesProseRules() =
        assertEquals("555-1234", Cleaner.clean("five five five one two three four.", format = com.pharrlabs.voice.field.FieldFormat.Phone))
}
