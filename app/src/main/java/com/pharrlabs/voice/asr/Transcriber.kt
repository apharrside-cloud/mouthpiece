package com.pharrlabs.voice.asr

import java.io.File

/** Thrown when the whisper model file is absent so callers can show an error state. */
class ModelMissing(val file: File) : Exception("Speech model not found: ${file.name}")

/** JNI bindings to whisper.cpp (see src/main/cpp/whisper_jni.cpp). */
internal object WhisperNative {
    init { System.loadLibrary("whisper_jni") }
    external fun initContext(modelPath: String): Long
    external fun fullTranscribe(ctx: Long, pcm: FloatArray, threads: Int): String
    external fun freeContext(ctx: Long)
}

interface Transcriber {
    suspend fun transcribe(pcm: ShortArray): String
    fun close()
}
