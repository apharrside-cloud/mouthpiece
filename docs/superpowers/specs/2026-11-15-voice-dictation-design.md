# Voice Dictation (working name: "Pharr Voice") — Design Spec

Date: 2026-11-15 · Owner: Pharr Labs · Status: DRAFT, awaiting the owner's review

## 1. Purpose
A personal, free replacement for Wispr Flow on Android. When the owner focuses a text field in any app, a floating mic bubble appears. He taps it, talks, and cleaned-up text is inserted at the cursor. Transcription runs entirely on the phone: no subscription, no usage cap, no audio leaving the device.

**Success criteria**
- Bubble appears on focus of an editable field in common apps (Messages, Gmail, Chrome URL/forms, Slack/Teams, Notes, Keep) and disappears when focus leaves.
- Spoken sentence of ~10 s is inserted within ~3 s of stopping (measured on device; target, not promise).
- Output has no filler words, with sane capitalization and punctuation.
- Works offline (airplane mode).
- Installable as a sideloaded APK; builds reproducibly from this repo.

**Non-goals (YAGNI)**: Play Store release, iOS, cloud transcription, multi-user accounts, a custom keyboard, translation, history sync.

## 2. Target environment
- Device: Pixel 9 Pro XL (16 GB RAM, Tensor G4), current Android (minSdk 31, targetSdk 35).
- Dev: Windows PC, JDK 17, Android SDK 35, NDK 27, CMake 3.22 at `C:\Android\sdk`.
- Deploy: Wireless Debugging (adb over Wi-Fi) for installs and logs.

## 3. Architecture (native Kotlin + C++)
```
 Focus event ─► BubbleService (AccessibilityService)
                   │  shows overlay bubble next to editable field
                   ▼ tap
               Recorder (AudioRecord 16kHz mono PCM) ──► VAD trim
                   ▼
               Transcriber (interface) ─► WhisperCppEngine (JNI)  [swappable]
                   ▼ raw text
               Cleaner (rules, then optional LLM)
                   ▼ clean text
               Injector (SET_TEXT → paste → clipboard)
```
Units and boundaries:
| Unit | Responsibility | Depends on |
|---|---|---|
| `BubbleService` | Detect editable focus (skip password/secure fields), draw/position/hide the overlay, drive state (idle / listening / processing / error) | Android Accessibility APIs |
| `Recorder` | Capture PCM, expose start/stop, silence trimming | `AudioRecord` |
| `Transcriber` | `suspend fun transcribe(pcm: ShortArray): String` | engine impl |
| `WhisperCppEngine` | whisper.cpp built via NDK/CMake, quantized `small.en` model loaded from app storage | JNI, model file |
| `Cleaner` | Pure function `clean(raw, dictionary): String`: filler removal, casing, punctuation, spoken commands ("new line", "period"), dictionary substitutions | none (unit-testable on JVM) |
| `Injector` | Insert at cursor/selection, preserving existing text; fallbacks | Accessibility node API, clipboard |
| `SettingsActivity` | Permission onboarding, model download/select, dictionary editor, bubble prefs | DataStore |

## 4. Key behaviors
- **Trigger**: tap bubble to start, tap again to stop (hold-to-talk as an option later). Haptic tick on start/stop.
- **Privacy**: bubble never shows on password/secure fields; no network access except the one-time model download (user-initiated); no audio persisted.
- **Injection order**: (1) `ACTION_SET_TEXT` with merged text honoring selection range; (2) clipboard + `ACTION_PASTE`; (3) copy to clipboard and toast. Original clipboard restored after paste fallback when possible.
- **Models**: `small.en` q5_1 by default (downloaded in-app from the official whisper.cpp Hugging Face repo, checksum verified). `base.en` selectable for speed.
- **Phase 2 cleanup**: optional on-device LLM pass (Gemini Nano via AICore/ML Kit GenAI if available on the device, else a small GGUF model) for self-corrections and tone. Falls back to the rules-only output on any failure.

## 5. Error handling
- Mic busy / permission revoked: bubble turns red, one-line notice, tap opens settings.
- Transcription empty/failed: nothing inserted, brief toast; audio discarded.
- Injection failure: fallback chain above; final fallback always leaves text on the clipboard.
- Service killed by OEM/battery policy: onboarding prompts to exempt app from battery optimization; settings screen shows service status.

## 6. Risks (resolved by Phase 0 spike before further build)
1. **Background mic from an accessibility overlay** on Android 14/15 (while-in-use restrictions). Spike must prove `AudioRecord` starts from the service when triggered via the overlay. Fallback design: a microphone-type foreground service started from the overlay tap.
2. **Non-accessible fields** (Chrome page content, custom-drawn editors, games): may not report as editable. Accept; log known-bad apps in the README.
3. **Sideload friction**: Android 13+ "Allow restricted settings" step before the accessibility toggle; possible Play Protect warning. Documented in onboarding.
4. **Latency/accuracy** of `small.en` on Tensor G4 (CPU only; whisper.cpp has no Tensor NPU path). Spike benchmarks `base.en` and `small.en`. If too slow, evaluate sherpa-onnx (Moonshine/Parakeet) behind the same `Transcriber` interface.

## 7. Testing
- `Cleaner`: JVM unit tests written first (TDD) with table-driven cases (fillers, casing, commands, dictionary).
- `Transcriber`: instrumented test on the phone with a bundled sample WAV, asserting expected keywords and logging real-time factor.
- Bubble/Injector: manual acceptance checklist on the phone in 6+ apps, driven by adb (`uiautomator dump`, `logcat`) where possible.
- Every phase ends with a built APK and observed output from the real device — not a claim.

## 8. Phases
- **Phase 0 — Spike (throwaway):** bare app with accessibility bubble + AudioRecord + whisper.cpp `base.en`/`small.en` on-device; answers risks 1 and 4. Code may be discarded.
- **Phase 1 — MVP:** full pipeline in §3 with rule-based cleanup, settings, dictionary. Signed release APK.
- **Phase 2 — Polish:** LLM cleanup, hold-to-talk, per-app overrides, bubble customization.

## 9. Repo and deliverables
`D:\Hermes Database\Personal\Projects\voice-dictation\` (git). Project note filed at `Personal/Projects/voice-dictation.md`; task added to Todoist/tasklist.
