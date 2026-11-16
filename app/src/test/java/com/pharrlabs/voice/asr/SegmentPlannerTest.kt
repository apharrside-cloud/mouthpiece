package com.pharrlabs.voice.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlannerTest {
    private val sr = 16000
    private fun speech(ms: Int) = ShortArray(sr * ms / 1000) { if (it % 2 == 0) 3000 else -3000 }
    private fun quiet(ms: Int) = ShortArray(sr * ms / 1000) { 5 }
    private fun cat(vararg parts: ShortArray) = parts.fold(ShortArray(0)) { a, b -> a + b }

    @Test fun noCutWhileStillSpeaking() =
        assertNull(SegmentPlanner.nextCut(speech(4000)))

    @Test fun cutsAtPauseAfterEnoughSpeech() {
        val pcm = cat(speech(3000), quiet(900), speech(500))
        val cut = SegmentPlanner.nextCut(pcm)
        assertNotNull(cut)
        // Cut lands inside the pause, after the speech and before the next speech.
        assertTrue("cut=$cut", cut!! in (3000 * 16)..(3900 * 16))
    }

    @Test fun shortPauseDoesNotCut() =
        assertNull(SegmentPlanner.nextCut(cat(speech(3000), quiet(400), speech(2000))))

    @Test fun tooShortSegmentDoesNotCut() =
        assertNull(SegmentPlanner.nextCut(cat(speech(1000), quiet(1000), speech(1000))))

    @Test fun leadingSilenceDoesNotCount() =
        assertNull(SegmentPlanner.nextCut(cat(quiet(3000), speech(300), quiet(1000))))

    @Test fun silenceOnlyNeverCuts() =
        assertNull(SegmentPlanner.nextCut(quiet(10000)))

    @Test fun fromOffsetIsRespected() {
        val pcm = cat(speech(3000), quiet(900), speech(3000), quiet(900), speech(200))
        val first = SegmentPlanner.nextCut(pcm)!!
        val second = SegmentPlanner.nextCut(pcm, from = first)!!
        assertTrue(second > 6900 * 16 - 100)
        assertTrue(second <= pcm.size)
    }

    @Test fun emptyInput() = assertNull(SegmentPlanner.nextCut(ShortArray(0)))

    @Test fun cutNeverBeyondInput() {
        val pcm = cat(speech(3000), quiet(800))
        val cut = SegmentPlanner.nextCut(pcm)
        assertNotNull(cut)
        assertTrue(cut!! <= pcm.size)
        assertEquals(true, cut > 0)
    }
}
