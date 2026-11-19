package com.pharrlabs.voice.inject

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholdersTest {
    @Test fun telegramMessagePlaceholderIsNotRealText() {
        assertTrue(Placeholders.isPlaceholder("Message", null, false))
        assertTrue(Placeholders.isPlaceholder("Message", "Message", false))
    }

    @Test fun gmailAndOtherKnownPlaceholders() {
        assertTrue(Placeholders.isPlaceholder("Compose email", null, false))
        assertTrue(Placeholders.isPlaceholder("Type a message", null, false))
        assertTrue(Placeholders.isPlaceholder("Type a message...", null, false))
    }

    @Test fun anyFieldWhoseTextEqualsItsHintIsEmpty() {
        assertTrue(Placeholders.isPlaceholder("Reply to Sam", "reply to sam", false))
    }

    @Test fun hintFlagAlwaysWins() {
        assertTrue(Placeholders.isPlaceholder("anything", null, true))
    }

    @Test fun realTextIsNeverTreatedAsPlaceholder() {
        assertFalse(Placeholders.isPlaceholder("Message me later", null, false))
        assertFalse(Placeholders.isPlaceholder("See you at 5", "Message", false))
        assertFalse(Placeholders.isPlaceholder("", "Message", false))
        assertFalse(Placeholders.isPlaceholder(null, null, false))
    }
}
