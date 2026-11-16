package com.pharrlabs.voice.inject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PieceEditTest {
    @Test fun swapsThePieceAndPutsTheCursorAfterIt() {
        val r = PieceEdit.replace("Hello. bring the notes.", " bring the notes.", " BRING THE NOTES.")!!
        assertEquals("Hello. BRING THE NOTES.", r.text)
        assertEquals(23, r.selStart)
        assertEquals(23, r.selEnd)
    }

    @Test fun keepsTextTypedAfterTheDictation() {
        val r = PieceEdit.replace("one two three", "two", "TWO")!!
        assertEquals("one TWO three", r.text)
        assertEquals(7, r.selStart)
    }

    @Test fun refusesWhenThePieceIsGone() = assertNull(PieceEdit.replace("something else", "two", "TWO"))

    @Test fun refusesWhenThePieceAppearsTwice() = assertNull(PieceEdit.replace("go go", "go", "GO"))
}
