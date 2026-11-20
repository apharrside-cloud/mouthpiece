# Third-party notices

Mouthpiece is MIT licensed (see `LICENSE`). It includes or downloads the following open-source work.

| Component | Use | License |
|---|---|---|
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) / ggml | Speech recognition engine, vendored in `app/src/main/cpp/whisper.cpp` (its own `LICENSE` is kept there) | MIT, Copyright (c) 2023-2026 The ggml authors |
| [OpenAI Whisper](https://github.com/openai/whisper) models (tiny.en, base.en, small.en, medium.en) | Downloaded by the app on first use, in whisper.cpp's ggml format, from `huggingface.co/ggerganov/whisper.cpp`. Not bundled in this repo or the APK. | MIT, Copyright (c) 2022 OpenAI |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) and ONNX Runtime | Runs the Parakeet model. Prebuilt arm64 libraries and Kotlin bindings are in `app/src/main/jniLibs` and `app/src/main/java/com/k2fsa`. | Apache-2.0 (sherpa-onnx); MIT (ONNX Runtime) |
| [NVIDIA Parakeet TDT 0.6B v2](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2) (int8 ONNX export by k2-fsa) | Downloaded by the app on first use from `huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8`. Not bundled in this repo or the APK. | CC-BY-4.0, Copyright (c) NVIDIA Corporation |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Runtime dependency | Apache-2.0 |

Test-only dependencies (JUnit, OkHttp MockWebServer, org.json, kotlinx-coroutines-test) are not shipped in the app.
