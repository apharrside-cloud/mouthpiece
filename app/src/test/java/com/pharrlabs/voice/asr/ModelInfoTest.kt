package com.pharrlabs.voice.asr

import com.pharrlabs.voice.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelInfoTest {
    @Test fun everyOfferedModelHasDetails() {
        for ((name, _) in Prefs.MODELS) assertNotNull("no details for $name", ModelInfo.of(name))
    }

    @Test fun unknownModelHasNoDetails() = assertNull(ModelInfo.of("nope"))

    @Test fun smallIsOpenAiWhisperSmallEnglish() {
        val i = ModelInfo.of("small.en-q5_1")!!
        assertEquals("OpenAI Whisper small.en", i.family)
        assertEquals("244M", i.params)
        assertEquals("q5_1", i.quantization)
        assertEquals("ggml-small.en-q5_1.bin", i.file)
    }

    @Test fun detailLineNamesModelSizeAndFile() {
        assertEquals(
            "OpenAI Whisper medium.en · 769M parameters · q5_0 quantized · ggml-medium.en-q5_0.bin",
            ModelInfo.of("medium.en-q5_0")!!.line(),
        )
    }

    @Test fun fileNamesMatchTheDownloader() {
        for ((name, _) in Prefs.MODELS.filter { it.first != ModelManager.PARAKEET_V2 }) assertEquals("ggml-$name.bin", ModelInfo.of(name)!!.file)
    }

    @Test fun turboIsLargeV3Turbo() {
        val i = ModelInfo.of("large-v3-turbo-q5_0")!!
        assertEquals("OpenAI Whisper large-v3-turbo", i.family)
        assertEquals("809M", i.params)
    }

    @Test fun parakeetIsDescribedAsParakeet() {
        val i = ModelInfo.of(ModelManager.PARAKEET_V2)!!
        assertTrue(i.family.contains("Parakeet"))
        assertEquals("int8", i.quantization)
    }

    @Test fun allRunThroughWhisperCpp() {
        assertTrue(ModelInfo.RUNTIME.contains("whisper.cpp"))
    }
}
