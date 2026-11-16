package com.pharrlabs.voice.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class ModelSpec(val url: String, val sha256: String, val sizeBytes: Long)

/** Downloads whisper models into [dir], verifying size and SHA-256 before the file becomes visible. */
class ModelManager(private val dir: File, private val specs: Map<String, ModelSpec> = DEFAULT_SPECS) {

    fun modelFile(name: String): File = File(dir, "ggml-$name.bin")

    fun isInstalled(name: String): Boolean {
        val spec = specs[name] ?: return false
        val f = modelFile(name)
        return f.isFile && f.length() == spec.sizeBytes
    }

    /** Deletes a downloaded model (and any half-finished download). Returns the bytes freed; unknown names free nothing. */
    fun remove(name: String): Long {
        if (name !in specs) return 0L
        var freed = 0L
        for (f in listOf(modelFile(name), File(dir, "ggml-$name.bin.part"))) {
            if (f.isFile) { val n = f.length(); if (f.delete()) freed += n }
        }
        return freed
    }

    suspend fun download(name: String, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        val spec = specs[name] ?: return@withContext Result.failure(IllegalArgumentException("unknown model: $name"))
        dir.mkdirs()
        val part = File(dir, "ggml-$name.bin.part")
        try {
            val conn = (URL(spec.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Mouthpiece/0.1")
            }
            try {
                if (conn.responseCode != 200) error("download failed: HTTP ${conn.responseCode}")
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var lastReported = -1
                conn.inputStream.use { input ->
                    part.outputStream().buffered().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            total += n
                            val pct = (total * 100 / spec.sizeBytes).toInt().coerceAtMost(99)
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
                if (total != spec.sizeBytes) error("size mismatch: got $total bytes, expected ${spec.sizeBytes}")
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(spec.sha256, ignoreCase = true)) error("checksum mismatch: model file is corrupt")
            } finally {
                conn.disconnect()
            }
            val target = modelFile(name)
            target.delete()
            if (!part.renameTo(target)) error("could not move model into place")
            onProgress(1f)
            Result.success(target)
        } catch (t: Throwable) {
            part.delete()
            if (t is kotlinx.coroutines.CancellationException) throw t
            Result.failure(t)
        }
    }

    companion object {
        private const val HF = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

        val DEFAULT_SPECS: Map<String, ModelSpec> = mapOf(
            "tiny.en-q5_1" to ModelSpec(
                "$HF/ggml-tiny.en-q5_1.bin",
                "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b",
                32_166_155L
            ),
            "small.en-q5_1" to ModelSpec(
                "$HF/ggml-small.en-q5_1.bin",
                "bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30",
                190_098_681L
            ),
            "base.en-q5_1" to ModelSpec(
                "$HF/ggml-base.en-q5_1.bin",
                "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f",
                59_721_011L
            ),
            "medium.en-q5_0" to ModelSpec(
                "$HF/ggml-medium.en-q5_0.bin",
                "76733e26ad8fe1c7a5bf7531a9d41917b2adc0f20f2e4f5531688a8c6cd88eb0",
                539_225_533L
            ),
            "large-v3-turbo-q5_0" to ModelSpec(
                "$HF/ggml-large-v3-turbo-q5_0.bin",
                "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
                574_041_195L
            ),
        )
    }
}
