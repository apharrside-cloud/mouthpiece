# Phase 0 Spike Results

Date: 2026-11-15 · Device: Pixel 9 Pro XL (Tensor G4, 16 GB), Android 17 / API 37, arm64-v8a.
Spike code lives in `app/.../spike/` and is throwaway; Task 6 productionizes the JNI/engine, Task 8 replaces the bubble service.

## Spike A — mic capture from an accessibility overlay (Risk 1): PASS, no foreground service needed

| Check | Result |
|---|---|
| Service connects on API 37 | Yes. Enabled over adb (`settings put secure enabled_accessibility_services ...`); no "Allow restricted settings" step was needed for an adb-installed APK. A sideloaded release APK installed from a browser/file manager may still need that step (unverified; the onboarding screen documents it). |
| Overlay type | `TYPE_ACCESSIBILITY_OVERLAY` works with no `SYSTEM_ALERT_WINDOW` permission. |
| `AudioRecord` from a tap on the overlay, other app (Messages) in foreground | `state=1 (INITIALIZED)`, `recordingState=3 (RECORDING)`. `dumpsys audio`: `src:VOICE_RECOGNITION not silenced pack:com.pharrlabs.voice`; appops access `fg-s` (allowed). |
| Signal | Quiet room baseline RMS 13.3 / 12.8 (peak 101). Real speech, 3 clips: RMS 219.4 / 272.1 / 294.9 (peak 1808 / 1755 / 3125). |
| Fallback (mic foreground service) | Not required. |

Bubble appeared for editable focus in: Google Messages compose field (tap to focus), Chrome page search box (`EditText`, web input exposed), Chrome omnibox. Gmail compose and Keep launched via intent did not auto-focus a field, so no focus event fired (not a failure; untested further).

Findings that change later tasks:
1. **Mic gain is low** (speech RMS ~250, peak ~2–3k of 32k). The plan's `SilenceTrimmer` default `thresholdRms = 500` would discard real speech. Task 5 uses **200** and a unit test pins that speech at RMS ≈ 250 survives; transcriber input is peak-normalized (Task 6).
2. **Stale bubble position.** The bubble was anchored to the field's pre-keyboard bounds and ended up near the bottom of the screen until a later event moved it. Task 8 re-anchors on `TYPE_WINDOWS_CHANGED`/`WINDOW_CONTENT_CHANGED` and clamps it above the keyboard.
3. Playing audio through the phone speaker did not register on the mic (Bluetooth routing suspected), so verification used live speech. Automated mic tests are not possible this way; acceptance uses live speech.

## Spike B — whisper.cpp on Tensor G4 (Risk 4): PASS with `small.en` + shrunk audio context

Build: whisper.cpp **v1.9.5** (commit `d1be6fde11ac6e0407606b4e42fe72d34add8037`), CPU only, `-DCMAKE_BUILD_TYPE=Release` forced (also in debug builds), `GGML_CPU_ARM_ARCH=armv8.2-a+dotprod+i8mm+fp16`. Runtime sysinfo: `NEON=1 ARM_FMA=1 FP16_VA=1 MATMUL_INT8=1 DOTPROD=1 REPACK=1`.
Models (official ggerganov/whisper.cpp HF repo): `ggml-base.en-q5_1.bin` (59,721,011 B, sha256 `4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f`), `ggml-small.en-q5_1.bin` (190,098,681 B, sha256 `bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30`).
Method: best of 2 timed runs after 1 warm-up, JFK sample (11.0 s) and 3x-looped JFK (30.0 s), screen on, app in foreground.

| Model | Threads | audio_ctx | 11 s clip | 30 s clip |
|---|---|---|---|---|
| base.en q5_1 | 4 | full 30 s | 1.69 s (RTF 0.154) | 2.23 s |
| base.en q5_1 | 6 | full | 1.75 s | 2.36 s |
| small.en q5_1 | 4 | full | 5.84 s (RTF 0.531) | 7.29 s |
| small.en q5_1 | 6 | full | 6.66 s | 8.84 s |
| **base.en q5_1** | 4 | **shrunk** | **0.89 s** (RTF 0.081) | 2.29 s |
| **small.en q5_1** | 4 | **shrunk** | **2.88 s** (RTF 0.261) | 7.63 s |

All runs produced the correct JFK transcript. `base.en` + shrink dropped one comma ("for you ask"); `small.en` kept punctuation.

Decision:
- **Default `small.en` q5_1, 4 threads, `audio_ctx` shrunk to `clip_seconds*50 + 100` frames (capped at 1500)**: meets the spec target (≤ ~3 s for a 10 s clip). `base.en` is the fast option (0.9 s).
- More threads do not help (6 is slower than 4).
- A **locked / screen-off** phone ran the same `base.en` job ~4x slower (6.56 s vs 1.69 s) because of CPU scheduling. Real dictation happens with the screen on and another app foreground, so this is not a blocker; do not benchmark with the screen off.
- Long audio: whisper encodes ≤30 s windows, so the `Chunker` (Task 6) splits at silence, and a 30 s chunk costs ~7.6 s with `small.en`. Dictations over ~15 s will feel slow; mitigation if needed in Phase 2: `base.en` for long clips.
- sherpa-onnx evaluation is not needed for the MVP.

## Task 6 device check (WhisperCppEngine in the app, instrumented)

`small.en-q5_1`, 4 threads, shrunk audio_ctx, JFK 11.0 s clip, app Activity in foreground: **decode 4.33 s (RTF 0.39)**, correct transcript. Spike B measured 2.88 s for the same model/clip; the gap is unexplained (candidates: thermal state after repeated runs, instrumentation process priority, peak-normalization changing decode path). Re-measure with the real service in Task 10; if still > 3 s, try `base.en` default or thread count 3/5.
Quiet-mic case (JFK scaled to 8%, like the Pixel mic): full correct transcript, so `Pcm.toNormalizedFloats` works. A 0.5 s clip returns "And" without crashing (clips < 1.2 s are zero-padded).
Instrumented tests must NOT be run via `gradlew connectedDebugAndroidTest`: it uninstalls the app afterwards, deleting the models pushed to `/sdcard/Android/data/com.pharrlabs.voice/files/`. Use `installDebug installDebugAndroidTest`, push the model, then `adb shell am instrument -w -e class ...WhisperCppEngineTest com.pharrlabs.voice.test/androidx.test.runner.AndroidJUnitRunner`.
