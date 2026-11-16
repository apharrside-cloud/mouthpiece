package com.pharrlabs.voice.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorrectionDiffTest {
    @Test fun singleWordFix() =
        assertEquals(Correction("tracy", "Tracey"), CorrectionDiff.find("Tell Tracy hello.", "Tell Tracey hello."))

    @Test fun surroundingTextOutsideInsertionIsIgnored() =
        assertEquals(
            Correction("ethan", "Ethen"),
            CorrectionDiff.find("Hi all. Ask Ethan now.", "Hi all. Ask Ethen now.", inserted = "Ask Ethan now."),
        )

    @Test fun phraseFix() =
        assertEquals(Correction("farr lab", "Pharr Labs"), CorrectionDiff.find("Visit Farr Lab today.", "Visit Pharr Labs today."))

    @Test fun caseOnlyFixMidSentenceIsLearned() =
        assertEquals(Correction("olivia", "Olivia"), CorrectionDiff.find("Call olivia today.", "Call Olivia today."))

    @Test fun caseOnlyFixOfFirstWordIgnored() =
        assertNull(CorrectionDiff.find("hello there.", "Hello there."))

    @Test fun punctuationOnlyChangeIgnored() =
        assertNull(CorrectionDiff.find("Hello there.", "Hello there!"))

    @Test fun pureDeletionIgnored() =
        assertNull(CorrectionDiff.find("Hello big world.", "Hello world."))

    @Test fun pureInsertionIgnored() =
        assertNull(CorrectionDiff.find("Hello world.", "Hello big world."))

    @Test fun wholeRewriteIgnored() =
        assertNull(CorrectionDiff.find("Meet at noon tomorrow.", "Lunch is cancelled for everyone."))

    @Test fun unrelatedWordSwapIgnored() =
        assertNull(CorrectionDiff.find("Send it today.", "Send it xylophone."))

    @Test fun twoSeparateEditsIgnored() =
        assertNull(CorrectionDiff.find("Tracy and Ethan left.", "Tracey and Ethen left."))

    @Test fun noChange() = assertNull(CorrectionDiff.find("Same text.", "Same text."))

    @Test fun trailingPunctuationOnEditedWordKept() =
        assertEquals(Correction("tracy", "Tracey"), CorrectionDiff.find("Thanks, Tracy.", "Thanks, Tracey."))
}
