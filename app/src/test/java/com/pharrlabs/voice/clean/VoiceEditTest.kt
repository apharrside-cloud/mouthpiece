package com.pharrlabs.voice.clean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEditTest {
    // ---- a command that is the whole utterance

    @Test fun standaloneCapitalize() {
        assertEquals(VoiceEdit.Command.Capitalize, VoiceEdit.standalone("Capitalize that."))
        assertEquals(VoiceEdit.Command.Capitalize, VoiceEdit.standalone("capitalise it"))
    }

    @Test fun standaloneAllCaps() {
        assertEquals(VoiceEdit.Command.AllCaps, VoiceEdit.standalone("All caps."))
        assertEquals(VoiceEdit.Command.AllCaps, VoiceEdit.standalone("all caps that"))
        assertEquals(VoiceEdit.Command.AllCaps, VoiceEdit.standalone("Make that all caps."))
        assertEquals(VoiceEdit.Command.AllCaps, VoiceEdit.standalone("Uppercase that."))
        assertEquals(VoiceEdit.Command.AllCaps, VoiceEdit.standalone("All-caps."))
    }

    @Test fun standaloneLowercase() {
        assertEquals(VoiceEdit.Command.Lowercase, VoiceEdit.standalone("Lowercase that."))
        assertEquals(VoiceEdit.Command.Lowercase, VoiceEdit.standalone("lower case that"))
        assertEquals(VoiceEdit.Command.Lowercase, VoiceEdit.standalone("All lowercase."))
    }

    @Test fun standaloneReplace() {
        assertEquals(VoiceEdit.Command.Replace("noon", "one"), VoiceEdit.standalone("Replace noon with one."))
        assertEquals(VoiceEdit.Command.Replace("the date", "Friday"), VoiceEdit.standalone("replace the date with Friday"))
    }

    @Test fun ordinarySentencesAreNotCommands() {
        assertNull(VoiceEdit.standalone("I typed it in all caps."))
        assertNull(VoiceEdit.standalone("Please capitalize that word properly."))
        assertNull(VoiceEdit.standalone("Hello there."))
        assertNull(VoiceEdit.standalone(""))
    }

    // ---- a command at the end of the same utterance

    @Test fun trailingNeedsAPauseBeforeIt() {
        val t = VoiceEdit.trailing("Bring the notes, all caps.")!!
        assertEquals("Bring the notes", t.first)
        assertEquals(VoiceEdit.Command.AllCaps, t.second)
    }

    @Test fun trailingAfterASentence() {
        val t = VoiceEdit.trailing("See you tomorrow. Capitalize that.")!!
        assertEquals("See you tomorrow.", t.first)
        assertEquals(VoiceEdit.Command.Capitalize, t.second)
    }

    @Test fun trailingWithoutAPauseIsJustWords() {
        assertNull(VoiceEdit.trailing("I typed it in all caps."))
        assertNull(VoiceEdit.trailing("Please lowercase that"))
    }

    @Test fun trailingReplace() {
        val t = VoiceEdit.trailing("Meet at noon, replace noon with one.")!!
        assertEquals("Meet at noon", t.first)
        assertEquals(VoiceEdit.Command.Replace("noon", "one"), t.second)
    }

    @Test fun nothingBeforeTheCommandIsNotTrailing() {
        assertNull(VoiceEdit.trailing("All caps."))
    }

    // ---- applying

    @Test fun capitalizeMakesEveryWordStartUppercase() =
        assertEquals("Bring The Notes.", VoiceEdit.applyTo(VoiceEdit.Command.Capitalize, "bring the notes."))

    @Test fun capitalizeKeepsTheRestOfEachWord() =
        assertEquals("Call McDonald Now", VoiceEdit.applyTo(VoiceEdit.Command.Capitalize, "call McDonald now"))

    @Test fun capitalizeHandlesNewLines() =
        assertEquals("One Two\nThree", VoiceEdit.applyTo(VoiceEdit.Command.Capitalize, "one two\nthree"))

    @Test fun allCaps() = assertEquals("BRING THE NOTES.", VoiceEdit.applyTo(VoiceEdit.Command.AllCaps, "Bring the notes."))

    @Test fun lowercase() = assertEquals("hello world.", VoiceEdit.applyTo(VoiceEdit.Command.Lowercase, "Hello World."))

    @Test fun replaceWholeWordsIgnoringCase() {
        assertEquals("Meet at one. One is fine.", VoiceEdit.applyTo(VoiceEdit.Command.Replace("noon", "one"), "Meet at noon. Noon is fine."))
    }

    @Test fun replaceDoesNotTouchPartsOfWords() {
        assertEquals("afternoon at one", VoiceEdit.applyTo(VoiceEdit.Command.Replace("noon", "one"), "afternoon at noon"))
    }

    @Test fun replaceWithRegexCharactersIsLiteral() {
        assertEquals("pay \$5", VoiceEdit.applyTo(VoiceEdit.Command.Replace("five", "\$5"), "pay five"))
    }

    @Test fun replaceAppliesOnlyWhenTheTargetIsPresent() {
        assertTrue(VoiceEdit.applies(VoiceEdit.Command.Replace("noon", "one"), "Meet at noon."))
        assertFalse(VoiceEdit.applies(VoiceEdit.Command.Replace("noon", "one"), "Meet at five."))
        assertTrue(VoiceEdit.applies(VoiceEdit.Command.AllCaps, "anything"))
    }
}
