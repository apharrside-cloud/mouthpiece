package com.pharrlabs.voice.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** One file of a multi-file model. */
data class ModelFile(val name: String, val url: String, val sha256: String, val sizeBytes: Long)

/**
 * A downloadable model. Whisper models are a single ggml file ([url]/[sha256]/[sizeBytes]); multi-file models
 * (Parakeet) list every file in [parts] and are stored together in a folder named after the model.
 */
data class ModelSpec(val url: String, val sha256: String, val sizeBytes: Long, val parts: List<ModelFile> = emptyList()) {
    val isMultiFile: Boolean get() = parts.isNotEmpty()
    val totalBytes: Long get() = if (isMultiFile) parts.sumOf { it.sizeBytes } else sizeBytes
}

/** Downloads whisper models into [dir], verifying size and SHA-256 before the file becomes visible. */
/** Models dropped from the picker; their files are cleaned up. */
private val RETIRED = listOf("tiny.en-q5_1")

class ModelManager(private val dir: File, private val specs: Map<String, ModelSpec> = DEFAULT_SPECS) {

    fun modelFile(name: String): File = File(dir, "ggml-$name.bin")

    fun isMultiFile(name: String): Boolean = specs[name]?.isMultiFile == true

    /** Folder holding a multi-file model's files. */
    fun modelDir(name: String): File = File(dir, name)

    fun isInstalled(name: String): Boolean {
        val spec = specs[name] ?: return false
        if (spec.isMultiFile) return spec.parts.all { File(modelDir(name), it.name).let { f -> f.isFile && f.length() == it.sizeBytes } }
        val f = modelFile(name)
        return f.isFile && f.length() == spec.sizeBytes
    }

    /** Deletes models the app no longer offers (e.g. Large turbo) so they stop taking up space. Returns bytes freed. */
    fun removeRetired(): Long {
        var freed = 0L
        for (name in RETIRED) for (f in listOf(modelFile(name), File(dir, "ggml-$name.bin.part"))) {
            if (f.isFile) { val n = f.length(); if (f.delete()) freed += n }
        }
        return freed
    }

    /** Deletes a downloaded model (and any half-finished download). Returns the bytes freed; unknown names free nothing. */
    fun remove(name: String): Long {
        val spec = specs[name] ?: return 0L
        var freed = 0L
        if (spec.isMultiFile) {
            val d = modelDir(name)
            d.walkBottomUp().filter { it.isFile }.forEach { val n = it.length(); if (it.delete()) freed += n }
            d.deleteRecursively()
            return freed
        }
        for (f in listOf(modelFile(name), File(dir, "ggml-$name.bin.part"))) {
            if (f.isFile) { val n = f.length(); if (f.delete()) freed += n }
        }
        return freed
    }

    suspend fun download(name: String, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        val spec = specs[name] ?: return@withContext Result.failure(IllegalArgumentException("unknown model: $name"))
        dir.mkdirs()
        val files = if (spec.isMultiFile) {
            val d = modelDir(name).also { it.mkdirs() }
            spec.parts.map { Triple(it, File(d, it.name), it.sizeBytes) }
        } else {
            listOf(Triple(ModelFile(modelFile(name).name, spec.url, spec.sha256, spec.sizeBytes), modelFile(name), spec.sizeBytes))
        }
        val total = files.sumOf { it.third }
        var done = 0L
        var lastReported = -1
        fun report(bytesNow: Long) {
            val pct = ((done + bytesNow) * 100 / total).toInt().coerceAtMost(99)
            if (pct != lastReported) { lastReported = pct; onProgress(pct / 100f) }
        }
        try {
            for ((file, target, size) in files) {
                // A multi-file download that was interrupted keeps the files it finished (each was verified when it landed).
                if (spec.isMultiFile && target.isFile && target.length() == size) { done += size; continue }
                fetch(file, target, ::report)
                done += size
            }
            onProgress(1f)
            Result.success(if (spec.isMultiFile) modelDir(name) else modelFile(name))
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Result.failure(t)
        }
    }

    /** Downloads one file to a .part, verifies size and SHA-256, then moves it into place. Cleans up after any failure. */
    private suspend fun fetch(file: ModelFile, target: File, onBytes: (Long) -> Unit) {
        val part = File(target.parentFile, target.name + ".part")
        try {
            val conn = (URL(file.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Mouthpiece/0.1")
            }
            try {
                if (conn.responseCode != 200) error("download failed: HTTP ${conn.responseCode}")
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
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
                            onBytes(total)
                        }
                    }
                }
                if (total != file.sizeBytes) error("size mismatch: got $total bytes, expected ${file.sizeBytes}")
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(file.sha256, ignoreCase = true)) error("checksum mismatch: model file is corrupt")
            } finally {
                conn.disconnect()
            }
            target.delete()
            if (!part.renameTo(target)) error("could not move model into place")
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    companion object {
        private const val HF = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
        private const val HF_PARAKEET_V2 = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main"
        const val PARAKEET_V2 = "parakeet-tdt-0.6b-v2-int8"

        val DEFAULT_SPECS: Map<String, ModelSpec> = mapOf(
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
            "large-v3-turbo-q5_0" to ModelSpec(
                "$HF/ggml-large-v3-turbo-q5_0.bin",
                "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
                574_041_195L
            ),
            "medium.en-q5_0" to ModelSpec(
                "$HF/ggml-medium.en-q5_0.bin",
                "76733e26ad8fe1c7a5bf7531a9d41917b2adc0f20f2e4f5531688a8c6cd88eb0",
                539_225_533L
            ),
            PARAKEET_V2 to ModelSpec(
                url = "", sha256 = "", sizeBytes = 0L,
                parts = listOf(
                    ModelFile("encoder.int8.onnx", "$HF_PARAKEET_V2/encoder.int8.onnx", "92a10bd9c23936ea0566c29f963bc57c2a5df4261b6a8921c39abea66557ca8b", 652_282_309L),
                    ModelFile("decoder.int8.onnx", "$HF_PARAKEET_V2/decoder.int8.onnx", "4d097bae3bac5c96018d5293ba39f1b657296102b142affda40378f38b020f41", 7_257_753L),
                    ModelFile("joiner.int8.onnx", "$HF_PARAKEET_V2/joiner.int8.onnx", "77a29f3e5ac7d37074e0af465391b80f314015d5b97f0f90c6c7767f1d56f428", 1_739_080L),
                    ModelFile("tokens.txt", "$HF_PARAKEET_V2/tokens.txt", "ec182b70dd42113aff6c5372c75cac58c952443eb22322f57bbd7f53977d497d", 9_384L),
                ),
            ),
        )
    }
}
