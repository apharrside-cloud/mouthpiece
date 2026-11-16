package com.pharrlabs.voice.clean

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanerTest {
    @Test fun removesFillers() =
        assertEquals("So I think we should go.", Cleaner.clean("um so I think uh we should go"))

    @Test fun removesFillerWithItsComma() =
        assertEquals("So I think we should go.", Cleaner.clean("Um, so I think we should go."))

    @Test fun keepsLikeAsAWord() =
        assertEquals("I like it.", Cleaner.clean("i like it"))

    @Test fun capitalizesStandaloneI() =
        assertEquals("I think I can.", Cleaner.clean("i think i can"))

    @Test fun spokenPunctuationComma() =
        assertEquals("Call me, maybe?", Cleaner.clean("call me comma maybe question mark"))

    @Test fun spokenPunctuationPeriod() =
        assertEquals("Send it.", Cleaner.clean("send it period"))

    @Test fun newLineCommand() =
        assertEquals("Hello\nHow are you.", Cleaner.clean("hello new line how are you"))

    @Test fun newParagraphCommand() =
        assertEquals("First\n\nSecond.", Cleaner.clean("first new paragraph second"))

    @Test fun dictionaryReplacesPhrases() =
        assertEquals(
            "Open Pharr Labs dashboard.",
            Cleaner.clean("open pharr labs dashboard", mapOf("pharr labs" to "Pharr Labs"))
        )

    @Test fun whisperArtifactsBecomeEmpty() {
        for (raw in listOf("[BLANK_AUDIO]", "(music)", "[Music]", " ", "", "um uh")) {
            assertEquals("input <$raw>", "", Cleaner.clean(raw))
        }
    }

    @Test fun doesNotDoublePunctuate() =
        assertEquals("Is this right?", Cleaner.clean("Is this right?"))

    @Test fun doesNotStripFillersInsideWords() =
        assertEquals("Humble umbrella.", Cleaner.clean("humble umbrella"))

    @Test fun keepsRealParentheses() =
        assertEquals("Call (555) 0100.", Cleaner.clean("call (555) 0100"))

    @Test fun singleLineFieldDropsTrailingPeriodOfOneSentence() {
        assertEquals("Quarterly planning update", Cleaner.clean("um quarterly planning update", singleLine = true))
    }

    @Test fun singleLineFieldKeepsPunctuationOfMultipleSentences() {
        assertEquals("Hi there. How are you?", Cleaner.clean("hi there period how are you question mark", singleLine = true))
    }

    @Test fun singleLineFieldKeepsQuestionMark() {
        assertEquals("Are you free?", Cleaner.clean("are you free question mark", singleLine = true))
    }

    @Test fun singleLineFieldFlattensNewlines() {
        assertEquals("One. Two.", Cleaner.clean("one period new line two", singleLine = true))
    }

    @Test fun periodAlwaysKeepsPeriodEvenInShortField() {
        assertEquals("Quarterly update.", Cleaner.clean("quarterly update", singleLine = true, period = PeriodMode.Always))
    }

    @Test fun periodNeverDropsTrailingPeriodEverywhere() {
        assertEquals("Hi there. How are you", Cleaner.clean("hi there period how are you", period = PeriodMode.Never))
    }

    @Test fun periodNeverKeepsQuestionAndExclamation() {
        assertEquals("Are you free?", Cleaner.clean("are you free question mark", period = PeriodMode.Never))
        assertEquals("Great!", Cleaner.clean("great exclamation point", period = PeriodMode.Never))
    }

    @Test fun periodAutoOnProseKeepsPeriod() {
        assertEquals("See you soon.", Cleaner.clean("see you soon", period = PeriodMode.Auto))
    }

    @Test fun smartEditScratchThatApplied() =
        assertEquals("Thanks.", Cleaner.clean("call john scratch that thanks"))

    @Test fun smartEditCanBeDisabled() =
        assertEquals("Call john scratch that thanks.", Cleaner.clean("call john scratch that thanks", smartEdit = false))

    @Test fun bulletListKeepsNoTrailingPeriodOnLastItem() =
        assertEquals("Groceries:\n- Milk\n- Eggs", Cleaner.clean("groceries colon bullet point milk bullet point eggs"))
}
