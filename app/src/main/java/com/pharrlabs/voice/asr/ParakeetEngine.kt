package com.pharrlabs.voice.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device NVIDIA Parakeet TDT transcriber (sherpa-onnx, int8). Same contract as [WhisperCppEngine]: the model
 * loads on first use and is reused. Phone benchmark (Pixel, 4 threads): about as fast as Whisper small, with
 * punctuation and capitalization built in.
 */
class ParakeetEngine(private val modelDir: File, private val threads: Int = 4) : Transcriber {
    private val lock = Mutex()
    private var recognizer: OfflineRecognizer? = null
    @Volatile private var closed = false

    override suspend fun transcribe(pcm: ShortArray): String {
        if (pcm.isEmpty()) return ""
        return withContext(Dispatchers.Default) {
            lock.withLock {
                try {
                    val r = ensureLoaded()
                    Chunker.split(pcm).map { chunk ->
                        val stream = r.createStream()
                        try {
                            stream.acceptWaveform(Pcm.toNormalizedFloats(chunk), SAMPLE_RATE)
                            r.decode(stream)
                            r.getResult(stream).text.trim()
                        } finally {
                            stream.release()
                        }
                    }.filter { it.isNotEmpty() }.joinToString(" ")
                } finally {
                    if (closed) freeLocked()
                }
            }
        }
    }

    private fun ensureLoaded(): OfflineRecognizer {
        check(!closed) { "engine closed" }
        recognizer?.let { return it }
        val encoder = File(modelDir, "encoder.int8.onnx")
        if (!encoder.isFile) throw ModelMissing(encoder)
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = File(modelDir, "decoder.int8.onnx").absolutePath,
                    joiner = File(modelDir, "joiner.int8.onnx").absolutePath,
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = threads,
                modelType = "nemo_transducer",
            ),
        )
        return try {
            OfflineRecognizer(assetManager = null, config = config).also { recognizer = it }
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("could not load Parakeet (corrupt or unsupported model)", e)
        }
    }

    private fun freeLocked() {
        recognizer?.release()
        recognizer = null
    }

    /** Safe from the main thread: if a transcription is running, the model is freed when it ends. */
    override fun close() {
        closed = true
        if (lock.tryLock()) {
            try { freeLocked() } finally { lock.unlock() }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16000
    }
}
