package com.pharrlabs.voice.feedback

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    @Test fun buildsPayloadWithOnlyTheTypedTextAndBasicInfo() {
        val o = JSONObject(Feedback.payload(FeedbackKind.IDEA, "  Add a dark bubble  ", " a@b.c ", "0.4.9", "17", "Pixel 9")!!)
        assertEquals("idea", o.getString("type"))
        assertEquals("Add a dark bubble", o.getString("message"))
        assertEquals("a@b.c", o.getString("contact"))
        assertEquals("0.4.9", o.getString("version"))
        assertEquals(6, o.length())
    }

    @Test fun blankMessageSendsNothing() {
        assertNull(Feedback.payload(FeedbackKind.PROBLEM, "   ", "x", "0.4.9", "17", "Pixel"))
    }

    @Test fun longMessageIsTrimmedToTheServerLimit() {
        val o = JSONObject(Feedback.payload(FeedbackKind.PROBLEM, "x".repeat(9000), "", "0.4.9", "17", "Pixel")!!)
        assertEquals(Feedback.MAX_MESSAGE, o.getString("message").length)
    }

    @Test fun mapsServerAnswersToUserMessages() {
        assertEquals(SendResult.SENT, Feedback.resultFor(200))
        assertEquals(SendResult.BUSY, Feedback.resultFor(429))
        assertEquals(SendResult.FAILED, Feedback.resultFor(500))
        assertEquals(SendResult.FAILED, Feedback.resultFor(null))
        assertTrue(Feedback.message(SendResult.SENT).startsWith("Thanks"))
    }
}
