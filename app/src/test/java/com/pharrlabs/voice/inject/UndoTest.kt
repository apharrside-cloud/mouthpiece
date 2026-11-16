package com.pharrlabs.voice.inject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UndoTest {
    @Test fun revertsToOriginalWhenFieldUnchanged() {
        val rec = Undo.record("Hello", 5, 5, "world.")
        assertEquals(Reverted("Hello", 5, 5), Undo.revert(rec, "Hello world."))
    }

    @Test fun restoresReplacedSelection() {
        val rec = Undo.record("Send it now", 5, 7, "that")
        assertEquals(Reverted("Send it now", 5, 7), Undo.revert(rec, "Send that now"))
    }

    @Test fun emptyFieldRevertsToEmpty() {
        val rec = Undo.record("", 0, 0, "Hi.")
        assertEquals(Reverted("", 0, 0), Undo.revert(rec, "Hi."))
    }

    @Test fun removesJustThePieceWhenUserTypedMoreAfterwards() {
        val rec = Undo.record("Hello", 5, 5, "world.")
        assertEquals(Reverted("Hello", 5, 5), Undo.revert(rec, "Hello world."))
        assertEquals(Reverted("Hello and bye", 5, 5), Undo.revert(rec, "Hello world. and bye"))
    }

    @Test fun midTextInsertRemovesBothAddedSpaces() {
        val rec = Undo.record("abcd", 2, 2, "x")
        assertEquals("ab x cd", rec.after)
        assertEquals(Reverted("abcd", 2, 2), Undo.revert(rec, "ab x cd"))
    }

    @Test fun returnsNullWhenPieceIsGone() {
        val rec = Undo.record("Hello", 5, 5, "world.")
        assertNull(Undo.revert(rec, "Totally different"))
    }

    @Test fun returnsNullWhenPieceIsAmbiguous() {
        val rec = Undo.record("", 0, 0, "ok")
        assertNull(Undo.revert(rec, "ok and ok again"))
    }

    @Test fun midTextInsertWithSpacesAlreadyPresent() {
        val rec = Undo.record("ab cd", 2, 2, "x")
        assertEquals("ab x cd", rec.after)
        assertEquals(Reverted("ab cd", 2, 2), Undo.revert(rec, "ab x cd"))
    }
}
