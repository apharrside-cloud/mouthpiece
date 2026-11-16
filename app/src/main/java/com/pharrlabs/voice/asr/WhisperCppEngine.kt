package com.pharrlabs.voice.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device whisper.cpp transcriber. The model loads once on first use and the context is reused.
 * Spike B on the Pixel 9 Pro XL: 4 threads is fastest; small.en decodes an 11 s clip in ~2.9 s.
 */
class WhisperCppEngine(private val modelFile: File, private val threads: Int = 4) : Transcriber {
    private val lock = Mutex()
    private var ctx = 0L
    @Volatile private var closed = false

    override suspend fun transcribe(pcm: ShortArray): String {
        if (pcm.isEmpty()) return ""
        return withContext(Dispatchers.Default) {
            lock.withLock {
                try {
                    ensureLoaded()
                    Chunker.split(pcm)
                        .map { WhisperNative.fullTranscribe(ctx, Pcm.toNormalizedFloats(it), threads).trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(" ")
                } finally {
                    if (closed) freeLocked()
                }
            }
        }
    }

    private fun ensureLoaded() {
        check(!closed) { "engine closed" }
        if (ctx != 0L) return
        if (!modelFile.isFile) throw ModelMissing(modelFile)
        ctx = WhisperNative.initContext(modelFile.absolutePath)
        if (ctx == 0L) throw IllegalStateException("could not load ${modelFile.name} (corrupt or unsupported model)")
    }

    private fun freeLocked() {
        if (ctx != 0L) {
            WhisperNative.freeContext(ctx)
            ctx = 0L
        }
    }

    /** Safe from the main thread: if a transcription is running, the context is freed when it ends. */
    override fun close() {
        closed = true
        if (lock.tryLock()) {
            try { freeLocked() } finally { lock.unlock() }
        }
    }
}
