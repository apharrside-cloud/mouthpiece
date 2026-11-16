package com.pharrlabs.voice.clean

import org.junit.Assert.assertEquals
import org.junit.Test

class StyleTest {
    @Test fun standardKeepsContractions() =
        assertEquals("I don't think we're ready.", Cleaner.clean("I don't think we're ready", style = Style.Standard))

    @Test fun formalExpandsContractions() =
        assertEquals("I do not think we are ready.", Cleaner.clean("I don't think we're ready", style = Style.Formal))

    @Test fun formalReplacesSlang() =
        assertEquals("Yes, I am going to send it.", Cleaner.clean("yeah, I'm gonna send it", style = Style.Formal))

    @Test fun formalKeepsSentenceCase() =
        assertEquals("We cannot attend.", Cleaner.clean("we can't attend", style = Style.Formal))

    @Test fun formalHandlesCurlyApostrophe() =
        assertEquals("They are late.", Cleaner.clean("they\u2019re late", style = Style.Formal))

    @Test fun casualDropsPeriodOnShortMessage() =
        assertEquals("On my way", Cleaner.clean("on my way", style = Style.Casual))

    @Test fun casualKeepsPeriodsBetweenSentences() =
        assertEquals("Running late. Be there soon", Cleaner.clean("running late period be there soon", style = Style.Casual))

    @Test fun casualKeepsQuestionMark() =
        assertEquals("You coming?", Cleaner.clean("you coming question mark", style = Style.Casual))

    @Test fun literalOnlyTrimsAndAppliesDictionary() =
        assertEquals("git status um please", Cleaner.clean("  git status um please ", mapOf("pls" to "please"), style = Style.Literal))

    @Test fun literalDoesNotCapitalizeOrAddPeriod() =
        assertEquals("ls -la", Cleaner.clean("ls -la", style = Style.Literal))

    @Test fun literalEmptyStaysEmpty() = assertEquals("", Cleaner.clean("   ", style = Style.Literal))
}
