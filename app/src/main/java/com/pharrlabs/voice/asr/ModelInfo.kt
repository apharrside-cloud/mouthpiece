package com.pharrlabs.voice.asr

/** The exact model behind each choice in the picker, so nobody has to guess what "Fast" or "Balanced" means. */
data class ModelInfo(val family: String, val params: String, val quantization: String, val file: String) {

    fun line() = "$family · $params parameters · $quantization quantized · $file"

    companion object {
        const val RUNTIME = "Whisper models run on your phone with whisper.cpp (ggml format); Parakeet runs with sherpa-onnx (int8)."

        private fun whisper(variant: String, params: String, quant: String, key: String) =
            ModelInfo("OpenAI Whisper $variant", params, quant, "ggml-$key.bin")

        private val all = mapOf(
            "tiny.en-q5_1" to whisper("tiny.en", "39M", "q5_1", "tiny.en-q5_1"),
            "base.en-q5_1" to whisper("base.en", "74M", "q5_1", "base.en-q5_1"),
            "small.en-q5_1" to whisper("small.en", "244M", "q5_1", "small.en-q5_1"),
            "medium.en-q5_0" to whisper("medium.en", "769M", "q5_0", "medium.en-q5_0"),
            "parakeet-tdt-0.6b-v2-int8" to ModelInfo("NVIDIA Parakeet TDT v2 (English)", "600M", "int8", "encoder/decoder/joiner .int8.onnx"),
        )

        fun of(name: String): ModelInfo? = all[name]
    }
}
