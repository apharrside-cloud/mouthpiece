# Third-party notices

Mouthpiece is MIT licensed (see `LICENSE`). It includes or downloads the following MIT-licensed work.

| Component | Use | License |
|---|---|---|
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) / ggml | Speech recognition engine, vendored in `app/src/main/cpp/whisper.cpp` (its own `LICENSE` is kept there) | MIT, Copyright (c) 2023-2026 The ggml authors |
| [OpenAI Whisper](https://github.com/openai/whisper) models (tiny.en, base.en, small.en, medium.en, large-v3-turbo) | Downloaded by the app on first use, in whisper.cpp's ggml format, from `huggingface.co/ggerganov/whisper.cpp`. Not bundled in this repo or the APK. | MIT, Copyright (c) 2022 OpenAI |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Runtime dependency | Apache-2.0 |

Test-only dependencies (JUnit, OkHttp MockWebServer, org.json, kotlinx-coroutines-test) are not shipped in the app.
