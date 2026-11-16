package com.pharrlabs.voice.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {
    private val allowed = setOf("model", "bubble_size", "smart_edit", "hidden_apps", "dictionary", "app_rules")

    @Test fun roundTripsAllTypes() {
        val settings = mapOf<String, Any?>(
            "model" to "small.en-q5_1",
            "bubble_size" to 64,
            "smart_edit" to false,
            "hidden_apps" to setOf("com.a", "com.b"),
            "dictionary" to """{"tracy":"Tracey"}""",
        )
        assertEquals(settings, Backup.decode(Backup.encode(settings), allowed))
    }

    @Test fun unknownKeysAreDropped() {
        val json = Backup.encode(mapOf("model" to "x", "evil_key" to "y"))
        assertEquals(mapOf<String, Any?>("model" to "x"), Backup.decode(json, allowed))
    }

    @Test fun wrongAppRejected() {
        assertNull(Backup.decode("""{"app":"other","version":1,"settings":{}}""", allowed))
    }

    @Test fun newerVersionRejected() {
        assertNull(Backup.decode("""{"app":"pharr-voice","version":99,"settings":{}}""", allowed))
    }

    @Test fun garbageRejected() {
        assertNull(Backup.decode("not json", allowed))
    }

    @Test fun malformedEntryIsSkippedNotFatal() {
        val json = """{"app":"pharr-voice","version":1,"settings":{"bubble_size":{"t":"int","v":"abc"},"model":{"t":"string","v":"base.en-q5_1"}}}"""
        assertEquals(mapOf<String, Any?>("model" to "base.en-q5_1"), Backup.decode(json, allowed))
    }

    @Test fun nullValuesSkippedOnEncode() {
        assertEquals(mapOf<String, Any?>("model" to "m"), Backup.decode(Backup.encode(mapOf("model" to "m", "bubble_size" to null)), allowed))
    }

    @Test fun encodeTagsBackupAsMouthpiece() {
        assertEquals("mouthpiece", org.json.JSONObject(Backup.encode(mapOf("model" to "m"))).getString("app"))
    }

    @Test fun backupsFromBeforeTheRenameStillRestore() {
        val old = """{"app":"pharr-voice","version":1,"settings":{"model":{"t":"string","v":"base.en-q5_1"}}}"""
        assertEquals(mapOf<String, Any?>("model" to "base.en-q5_1"), Backup.decode(old, allowed))
    }
}
