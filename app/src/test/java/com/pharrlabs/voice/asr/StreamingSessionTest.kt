package com.pharrlabs.voice.asr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingSessionTest {
    private val sr = 16000
    private fun speech(ms: Int) = ShortArray(sr * ms / 1000) { if (it % 2 == 0) 3000 else -3000 }
    private fun quiet(ms: Int) = ShortArray(sr * ms / 1000) { 5 }

    /** Returns "seg<N>" for the Nth call so ordering is visible. */
    private class Fake(val replies: MutableList<String>) : Transcriber {
        val calls = mutableListOf<Int>()
        override suspend fun transcribe(pcm: ShortArray): String { calls += pcm.size; return replies.removeAt(0) }
        override fun close() {}
    }

    @Test fun finishWithoutSegmentsTranscribesWholeRecording() = runBlocking {
        val fake = Fake(mutableListOf("hello world"))
        val s = StreamingSession(fake)
        assertEquals("hello world", s.finish(speech(1500)))
    }

    @Test fun committedSegmentsComeBeforeTail() = runBlocking {
        val fake = Fake(mutableListOf("first part", "second part"))
        val s = StreamingSession(fake)
        val audio = speech(3000) + quiet(900) + speech(1500)
        val consumed = s.poll(audio)
        assertEquals(true, consumed > 0)
        assertEquals("first part", s.committedText)
        assertEquals("first part second part", s.finish(audio))
    }

    @Test fun pollTwiceDoesNotRetranscribeCommittedAudio() = runBlocking {
        val fake = Fake(mutableListOf("one", "two"))
        val s = StreamingSession(fake)
        val audio = speech(3000) + quiet(900) + speech(500)
        s.poll(audio)
        s.poll(audio)
        assertEquals(1, fake.calls.size)
    }

    @Test fun silentTailIsSkipped() = runBlocking {
        val fake = Fake(mutableListOf("only"))
        val s = StreamingSession(fake)
        val audio = speech(3000) + quiet(900)
        s.poll(audio)
        assertEquals("only", s.finish(audio))
        assertEquals(1, fake.calls.size)
    }

    @Test fun allSilenceYieldsEmpty() = runBlocking {
        val fake = Fake(mutableListOf())
        assertEquals("", StreamingSession(fake).finish(quiet(3000)))
    }

    @Test fun blankTranscriptsAreDropped() = runBlocking {
        val fake = Fake(mutableListOf("  ", "tail"))
        val s = StreamingSession(fake)
        val audio = speech(3000) + quiet(900) + speech(1500)
        s.poll(audio)
        assertEquals("tail", s.finish(audio))
    }

    @Test fun onSegmentCallbackReceivesRunningText() = runBlocking {
        val seen = mutableListOf<String>()
        val fake = Fake(mutableListOf("alpha"))
        val s = StreamingSession(fake, onText = { seen += it })
        s.poll(speech(3000) + quiet(900) + speech(300))
        assertEquals(listOf("alpha"), seen)
    }
}
