package com.pharrlabs.voice.inject

import org.junit.Assert.assertEquals
import org.junit.Test

class TextMergerTest {
    @Test fun appendsWithSpace() =
        assertEquals(Merged("Hello world.", 12), TextMerger.merge("Hello", 5, 5, "world."))

    @Test fun emptyFieldNoSpace() =
        assertEquals(Merged("Hi.", 3), TextMerger.merge("", 0, 0, "Hi."))

    @Test fun replacesSelection() =
        assertEquals(Merged("Send that now", 9), TextMerger.merge("Send it now", 5, 7, "that"))

    @Test fun midTextAddsTrailingSpace() =
        assertEquals("ab x cd", TextMerger.merge("abcd", 2, 2, "x").text)

    @Test fun midTextBeforeExistingSpaceDoesNotDoubleIt() =
        assertEquals("ab x cd", TextMerger.merge("ab cd", 2, 2, "x").text)

    @Test fun unknownSelectionAppends() =
        assertEquals(Merged("Hi there", 8), TextMerger.merge("Hi", -1, -1, "there"))

    @Test fun noSpaceBeforePunctuationInsert() =
        assertEquals("Hi, there", TextMerger.merge("Hi", 2, 2, ", there").text)

    @Test fun noSpaceAfterNewline() =
        assertEquals("Hi\nThere.", TextMerger.merge("Hi\n", 3, 3, "There.").text)

    @Test fun cursorLandsAtEndOfInsertedText() {
        val m = TextMerger.merge("abcd", 2, 2, "x")
        assertEquals(4, m.cursor) // "ab x|" then the added trailing space and "cd"
    }

    @Test fun reversedSelectionIsNormalized() =
        assertEquals("Send that now", TextMerger.merge("Send it now", 7, 5, "that").text)

    @Test fun outOfRangeSelectionIsClamped() =
        assertEquals("Hi there", TextMerger.merge("Hi", 99, 99, "there").text)
}
