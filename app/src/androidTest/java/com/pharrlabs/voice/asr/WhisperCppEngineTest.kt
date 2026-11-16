package com.pharrlabs.voice.asr

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Needs the model on the device: adb push ggml-small.en-q5_1.bin to
 * /sdcard/Android/data/com.pharrlabs.voice/files/ (the app's external files dir).
 */
@RunWith(AndroidJUnit4::class)
class WhisperCppEngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val model = File(instrumentation.targetContext.getExternalFilesDir(null), "ggml-small.en-q5_1.bin")

    private fun jfk(): ShortArray {
        val bytes = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray((bytes.size - 44) / 2) { bb.short }
    }

    @Test fun transcribesSampleWav() = runBlocking<Unit> {
        val engine = WhisperCppEngine(model)
        try {
            val pcm = jfk()
            engine.transcribe(pcm) // warm-up (model load + first decode)
            val t0 = System.nanoTime()
            val text = engine.transcribe(pcm)
            val sec = (System.nanoTime() - t0) / 1e9
            val audioSec = pcm.size / 16000.0
            Log.i("ASR_TEST", "RESULT audioSec=%.1f decodeSec=%.2f rtf=%.3f text=%s".format(audioSec, sec, sec / audioSec, text))
            assertTrue("got: $text", text.lowercase().contains("ask not what your country can do for you"))
        } finally {
            engine.close()
        }
    }

    @Test fun quietMicLevelStillTranscribes() = runBlocking<Unit> {
        // Spike A: the Pixel mic delivers speech at ~1/10 of full scale. Normalization must cope.
        val src = jfk()
        val quiet = ShortArray(src.size) { (src[it] * 0.08).toInt().toShort() }
        val engine = WhisperCppEngine(model)
        try {
            val text = engine.transcribe(quiet)
            Log.i("ASR_TEST", "QUIET text=$text")
            assertTrue("got: $text", text.lowercase().contains("your country"))
        } finally {
            engine.close()
        }
    }

    @Test fun emptyPcmReturnsEmptyString() = runBlocking<Unit> {
        val engine = WhisperCppEngine(model)
        try {
            assertEquals("", engine.transcribe(ShortArray(0)))
        } finally {
            engine.close()
        }
    }

    @Test fun veryShortClipDoesNotCrash() = runBlocking<Unit> {
        val engine = WhisperCppEngine(model)
        try {
            val text = engine.transcribe(jfk().copyOfRange(0, 8000)) // 0.5 s
            Log.i("ASR_TEST", "SHORT text='$text'")
        } finally {
            engine.close()
        }
    }

    @Test fun missingModelThrowsModelMissing() = runBlocking<Unit> {
        val engine = WhisperCppEngine(File(model.parentFile, "does-not-exist.bin"))
        try {
            engine.transcribe(ShortArray(16000) { 500 })
            fail("expected ModelMissing")
        } catch (e: ModelMissing) {
            // expected
        } finally {
            engine.close()
        }
    }
}
