package com.pharrlabs.voice.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private val json = """{"version": "0.4.0", "file": "pharr-voice-0.4.0.apk", "size_mb": "6.7", "sha256": "abc", "date": "2026-11-17"}"""

    @Test fun parsesTheSiteFile() {
        val i = UpdateCheck.parse(json)!!
        assertEquals("0.4.0", i.version)
        assertEquals("6.7", i.sizeMb)
        assertEquals("2026-11-17", i.date)
    }

    @Test fun rejectsGarbage() {
        assertNull(UpdateCheck.parse("not json"))
        assertNull(UpdateCheck.parse("{}"))
        assertNull(UpdateCheck.parse("""{"version": ""}"""))
        assertNull(UpdateCheck.parse("""{"version": "banana"}"""))
    }

    @Test fun newerVersionsAreDetected() {
        assertTrue(UpdateCheck.isNewer("0.4.0", "0.3.4"))
        assertTrue(UpdateCheck.isNewer("0.3.10", "0.3.9"))
        assertTrue(UpdateCheck.isNewer("1.0", "0.9.9"))
        assertTrue(UpdateCheck.isNewer("0.3.4.1", "0.3.4"))
    }

    @Test fun sameOrOlderIsNotAnUpdate() {
        assertFalse(UpdateCheck.isNewer("0.3.4", "0.3.4"))
        assertFalse(UpdateCheck.isNewer("0.3.3", "0.3.4"))
        assertFalse(UpdateCheck.isNewer("0.3", "0.3.0"))
    }

    @Test fun unparseableVersionsAreNeverAnUpdate() {
        assertFalse(UpdateCheck.isNewer("", "0.3.4"))
        assertFalse(UpdateCheck.isNewer("beta", "0.3.4"))
    }

    @Test fun checksAtMostOncePerInterval() {
        val h = 3_600_000L
        assertTrue(UpdateCheck.due(lastCheck = 0, now = 100 * h))
        assertFalse(UpdateCheck.due(lastCheck = 95 * h, now = 100 * h))
        assertTrue(UpdateCheck.due(lastCheck = 70 * h, now = 100 * h))
        assertTrue(UpdateCheck.due(lastCheck = 200 * h, now = 100 * h)) // clock went backwards
    }

    @Test fun availableOnlyWhenNewerAndNotSkipped() {
        val i = UpdateCheck.parse(json)!!
        assertTrue(UpdateCheck.available(i, "0.3.4", skipped = null))
        assertFalse(UpdateCheck.available(i, "0.4.0", skipped = null))
        assertFalse(UpdateCheck.available(i, "0.3.4", skipped = "0.4.0"))
        assertTrue(UpdateCheck.available(i.copy(version = "0.4.1"), "0.3.4", skipped = "0.4.0"))
    }

    @Test fun manualMessageSaysWhatHappened() {
        val newer = UpdateInfo("0.4.9", "6.8", "")
        assertEquals(true, UpdateCheck.manualMessage(newer, "0.4.8").startsWith("Update available: Mouthpiece 0.4.9 (6.8 MB)"))
        assertEquals(true, UpdateCheck.manualMessage(UpdateInfo("0.4.8", "", ""), "0.4.8").contains("up to date"))
        assertEquals(true, UpdateCheck.manualMessage(null, "0.4.8").contains("Couldn't reach"))
        assertEquals(false, UpdateCheck.manualMessage(UpdateInfo("0.4.9", "", ""), "0.4.8").contains("()"))
    }
}
