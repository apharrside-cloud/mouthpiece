package com.pharrlabs.voice.asr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingPreviewTest {
    private val sr = 16000
    private fun speech(ms: Int) = ShortArray(sr * ms / 1000) { if (it % 2 == 0) 3000 else -3000 }
    private fun quiet(ms: Int) = ShortArray(sr * ms / 1000) { 5 }

    private class Fake(val replies: MutableList<String>) : Transcriber {
        val sizes = mutableListOf<Int>()
        override suspend fun transcribe(pcm: ShortArray): String { sizes += pcm.size; return replies.removeAt(0) }
        override fun close() {}
    }

    @Test fun previewShowsCommittedPlusLiveTail() = runBlocking {
        val seen = mutableListOf<String>()
        val s = StreamingSession(Fake(mutableListOf("first part", "and more")), onText = { seen += it })
        val audio = speech(3000) + quiet(900) + speech(1500)
        s.poll(audio)
        assertEquals("and more", s.preview(audio))
        assertEquals("first part and more", seen.last())
    }

    @Test fun previewDoesNotCommitAnything() = runBlocking {
        val s = StreamingSession(Fake(mutableListOf("hello wor", "hello world")))
        val audio = speech(2000)
        s.preview(audio)
        assertEquals("", s.committedText)
        assertEquals("hello world", s.finish(audio))
    }

    @Test fun tooShortTailIsSkipped() = runBlocking {
        val fake = Fake(mutableListOf())
        assertNull(StreamingSession(fake).preview(speech(500)))
        assertEquals(0, fake.sizes.size)
    }

    @Test fun silentTailIsSkipped() = runBlocking {
        val fake = Fake(mutableListOf())
        assertNull(StreamingSession(fake).preview(quiet(3000)))
        assertEquals(0, fake.sizes.size)
    }

    @Test fun longTailIsCappedToTheMostRecentAudio() = runBlocking {
        val fake = Fake(mutableListOf("tail words"))
        StreamingSession(fake).preview(speech(9000), maxMs = 4000)
        assertEquals(true, fake.sizes.single() <= sr * 4)
    }

    @Test fun unchangedAudioIsNotPreviewedTwice() = runBlocking {
        val fake = Fake(mutableListOf("one", "two"))
        val s = StreamingSession(fake)
        val audio = speech(2000)
        s.preview(audio)
        assertNull(s.preview(audio))
        assertEquals(1, fake.sizes.size)
    }

    @Test fun tailGrowingEnoughIsPreviewedAgain() = runBlocking {
        val fake = Fake(mutableListOf("one", "one two"))
        val s = StreamingSession(fake)
        s.preview(speech(2000))
        assertEquals("one two", s.preview(speech(3000)))
    }
}
