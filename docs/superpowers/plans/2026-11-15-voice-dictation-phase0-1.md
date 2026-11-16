# Voice Dictation (Phase 0 spike + Phase 1 MVP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sideloadable Android app that shows a floating mic bubble on focused text fields, transcribes speech on-device with whisper.cpp, cleans it up, and inserts it at the cursor.

**Architecture:** Native Kotlin app. An `AccessibilityService` owns the bubble overlay and drives a record → transcribe → clean → inject pipeline. whisper.cpp is compiled via NDK/CMake and reached through JNI behind a `Transcriber` interface. Pure-logic units (`Cleaner`, `SilenceTrimmer`, `TextMerger`, `BubbleStateMachine`, `FieldFilter`) are JVM-testable.

**Tech Stack:** Kotlin 2.0.x, AGP 8.7.x, Gradle 8.9 (wrapper), JDK 17, NDK 27.2.12479018, CMake 3.22.1, whisper.cpp (pinned release tag), JUnit 4, DataStore.

**Spec:** `docs/superpowers/specs/2026-11-15-voice-dictation-design.md` (approved 2026-11-15). Phase 2 (LLM cleanup, hold-to-talk, per-app overrides) is out of scope here and gets its own plan after the MVP.

## Global Constraints
- Package `com.pharrlabs.voice`; app name "Pharr Voice"; repo `D:\Hermes Database\Personal\Projects\voice-dictation\`.
- minSdk 31, targetSdk 35, compileSdk 35. Device under test: Pixel 9 Pro XL, Tensor G4, **Android 17 (API 37)**, arm64-v8a only (`abiFilters "arm64-v8a"`).
- Build env: `JAVA_HOME=C:/Android/jdk17`, `ANDROID_HOME=C:/Android/sdk`, adb at `C:/Android/sdk/platform-tools`; device connected via Wireless Debugging (`adb devices` must list the Pixel; re-pair if not).
- Audio: 16 kHz, mono, 16-bit PCM. Model: `small.en` q5_1 default, `base.en` selectable, downloaded in-app from the official whisper.cpp Hugging Face repo with SHA-256 verification. Sample-rate/model files never committed to git.
- Privacy: never show the bubble on password/secure fields; no audio persisted; no network except the user-initiated model download.
- Injection order: `ACTION_SET_TEXT` merged at selection → clipboard + `ACTION_PASTE` → clipboard + toast. Original clipboard restored after the paste fallback when possible.
- Every task ends with real observed output (test run, `adb`/`logcat` capture), never an unverified claim.
- All shell commands run in git-bash from the repo root after `source scripts/env.sh`; native tools get `C:/...` paths.

## Review Focus
1. **Accidental tap / silence** (empty or sub-300 ms recording, only background noise): nothing inserted, no crash, bubble returns to idle. → Task 5, Task 6.
2. **Empty field showing hint text** (Android reports the hint as `text` when empty; `isShowingHintText` true): hint must never be treated as existing content. **Cursor mid-text with a selection**: replaced selection, not appended. → Task 7.
3. **Focus flicker and secure fields** (focus jumps between fields, password field, field inside a dialog that closes): bubble never appears on secure fields, never leaks after focus is lost. → Task 8.
4. **Long dictation** (>30 s; whisper's window is 30 s): chunk at silence boundaries, hard cap 120 s, text is not truncated. → Task 6.
5. **Mic unavailable** (phone call, another app recording, permission revoked mid-use) and **model missing/corrupt download**: visible error state with one-line reason, no crash. → Task 5, Task 6, Task 9.

---

## Phase 0: Spike (throwaway code; the output is answers)

### Task 1: Project scaffold and device deploy loop
**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/pharrlabs/voice/SettingsActivity.kt`, `scripts/env.sh`, `scripts/deploy.sh`, `.gitignore`, `README.md`
- Test: `app/src/test/java/com/pharrlabs/voice/SmokeTest.kt`

**Interfaces:**
- Produces: `scripts/env.sh` (exports JAVA_HOME, ANDROID_HOME, PATH incl. platform-tools; writes `local.properties` `sdk.dir`), `scripts/deploy.sh` (`./gradlew :app:installDebug` then `adb shell am start -n com.pharrlabs.voice/.SettingsActivity`), a debug APK that launches on the Pixel.

- [ ] **Step 1: Bootstrap the Gradle wrapper** by downloading Gradle 8.9 bin zip from services.gradle.org to the scratch dir, running `gradle wrapper --gradle-version 8.9` from it (or generating `gradle/wrapper/*` with the wrapper jar from that distribution). Verify: `./gradlew --version` prints Gradle 8.9 and JVM 17.
- [ ] **Step 2: Write failing test** `SmokeTest.appIdIsPharrVoice` asserting `BuildConfig.APPLICATION_ID == "com.pharrlabs.voice"`. Run `./gradlew :app:testDebugUnitTest` → FAIL (no module).
- [ ] **Step 3: Create the module**: single `app` module, Kotlin DSL, `buildFeatures { buildConfig = true }`, abiFilters arm64-v8a, `SettingsActivity` showing a "Pharr Voice" TextView. `.gitignore` excludes `build/`, `.gradle/`, `local.properties`, `*.bin`, `*.keystore`.
- [ ] **Step 4: Run test** → PASS. Run `./gradlew :app:assembleDebug` → APK exists at `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 5: Deploy**: `scripts/deploy.sh`; verify with `adb shell dumpsys activity activities | grep SettingsActivity` and a `adb exec-out screencap -p` screenshot showing the app on the phone.
- [ ] **Step 6: Commit** `chore: scaffold app and device deploy loop`.

### Task 2: Spike A: bubble + mic from an accessibility overlay (risk 1)
**Files:**
- Create: `app/src/main/java/com/pharrlabs/voice/spike/SpikeBubbleService.kt`, `app/src/main/res/xml/accessibility_service_config.xml`, `docs/spike-results.md`
- Modify: `AndroidManifest.xml` (service, `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`), `SettingsActivity.kt` (button to request mic permission)

**Interfaces:**
- Produces: a documented answer in `docs/spike-results.md` to: *Does `AudioRecord` capture non-silent audio when started from a tap on a `TYPE_ACCESSIBILITY_OVERLAY` view while another app (Messages) is foreground, on API 37?* Also records which fallback (mic foreground service) was required, if any.

- [ ] **Step 1: Implement** `SpikeBubbleService : AccessibilityService` listening for `TYPE_VIEW_FOCUSED`; when `event.source?.isEditable == true`, add a 56dp circular mic view via `WindowManager` (type `TYPE_ACCESSIBILITY_OVERLAY`) near the node's `getBoundsInScreen`; remove it on focus loss. Tap starts `AudioRecord` (16 kHz mono) for 3 s, logs `RMS=<value>` and sample count under tag `SPIKE`.
- [ ] **Step 2: Build, deploy, enable** the service (user toggles in Settings → Accessibility; first the one-time "Allow restricted settings" in App info → ⋮). Grant mic permission.
- [ ] **Step 3: Exercise**: open Messages, focus the text field, confirm the bubble appears (`adb exec-out screencap`), tap it by `adb shell input tap <x> <y>` while speaking/playing audio near the phone. Capture `adb logcat -d -s SPIKE`. Expected: `RMS` clearly above the silent-run baseline (record a silent baseline first).
- [ ] **Step 4: If recording is silent or `startRecording` fails**, switch the tap handler to start a microphone-type foreground service that owns `AudioRecord`, rerun Step 3. Record which variant works.
- [ ] **Step 5: Write results** to `docs/spike-results.md` (device, API level, variant that works, logcat excerpt, apps where the bubble did/didn't appear: Messages, Chrome URL bar, Chrome page form, Gmail compose, Keep).
- [ ] **Step 6: Commit** `spike: accessibility bubble and mic capture results`.

### Task 3: Spike B: whisper.cpp on Tensor G4 (risk 4)
**Files:**
- Create: `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/whisper_jni.cpp`, `app/src/main/cpp/whisper.cpp/` (clone of the latest stable whisper.cpp release tag, tag recorded in `docs/spike-results.md`, vendored and committed minus `models/`/`samples/`), `app/src/main/java/com/pharrlabs/voice/spike/WhisperBench.kt`
- Modify: `app/build.gradle.kts` (`externalNativeBuild { cmake { path, version "3.22.1" } }`, `ndkVersion "27.2.12479018"`), `docs/spike-results.md`

**Interfaces:**
- Produces: JNI functions `initContext(modelPath: String): Long`, `fullTranscribe(ctx: Long, pcm: FloatArray, threads: Int): String`, `freeContext(ctx: Long)` (mirroring whisper.cpp's `examples/whisper.android` binding); benchmark numbers (real-time factor = decode seconds ÷ audio seconds) for `base.en` and `small.en` q5_1 at 4 threads and 6 threads, on a 10 s and a 30 s clip.

- [ ] **Step 1: Configure CMake** to build whisper.cpp for arm64-v8a with `-O3` and `-march=armv8.2-a+dotprod+i8mm+fp16` (fall back to `armv8.2-a+dotprod` if the toolchain rejects it), linked with `whisper_jni.cpp`.
- [ ] **Step 2: Build** `./gradlew :app:assembleDebug`; expected `libwhisper_jni.so` inside the APK (`unzip -l`).
- [ ] **Step 3: Push models and a sample** with `adb push` (models `ggml-base.en-q5_1.bin`, `ggml-small.en-q5_1.bin` downloaded from the official ggerganov/whisper.cpp Hugging Face repo; JFK sample from the whisper.cpp repo, plus a 30 s clip made by looping it) to `/sdcard/Android/data/com.pharrlabs.voice/files/`.
- [ ] **Step 4: Run `WhisperBench`** from a debug button (or `adb shell am start -n ... --es bench 1`); log `BENCH model=… threads=… audioSec=… decodeSec=… rtf=… text=…` under tag `BENCH`. Capture via `adb logcat -d -s BENCH`.
- [ ] **Step 5: Record results and a recommendation** in `docs/spike-results.md`: pass if `small.en` decodes a 10 s clip in ≤ 3 s with correct transcript; otherwise recommend `base.en` or evaluating sherpa-onnx behind the same interface.
- [ ] **Step 6: Commit** `spike: whisper.cpp benchmark on Tensor G4`.
- [ ] **Step 7: CHECKPOINT with the owner**: report Task 2 and Task 3 findings and the chosen mic-capture variant and model. Phase 1 starts only after he confirms; if either risk failed, update the spec first.

---

## Phase 1: MVP

### Task 4: Cleaner (pure Kotlin, TDD)
**Files:**
- Create: `app/src/main/java/com/pharrlabs/voice/clean/Cleaner.kt`
- Test: `app/src/test/java/com/pharrlabs/voice/clean/CleanerTest.kt`

**Interfaces:**
- Produces: `object Cleaner { fun clean(raw: String, dictionary: Map<String, String> = emptyMap()): String }`. Dictionary keys match case-insensitively on whole words/phrases and are replaced verbatim by the value.

- [ ] **Step 1: Write failing tests** (JUnit, one `@Test` each, exact assertions):
  - `removesFillers`: `clean("um so I think uh we should go")` == `"So I think we should go."`
  - `removesFillerWithItsComma`: `clean("Um, so I think we should go.")` == `"So I think we should go."`
  - `keepsLikeAsAWord`: `clean("i like it")` == `"I like it."`
  - `capitalizesStandaloneI`: `clean("i think i can")` == `"I think I can."`
  - `spokenPunctuation`: `clean("call me comma maybe question mark")` == `"Call me, maybe?"`; `clean("send it period")` == `"Send it."`
  - `newLineCommand`: `clean("hello new line how are you")` == `"Hello\nHow are you."`; `"new paragraph"` → `"\n\n"`.
  - `dictionary`: `clean("open pharr labs dashboard", mapOf("pharr labs" to "Pharr Labs"))` == `"Open Pharr Labs dashboard."`
  - `whisperArtifactsBecomeEmpty`: `"[BLANK_AUDIO]"`, `"(music)"`, `"[Music]"`, `" "`, `"um uh"` all → `""`.
  - `doesNotDoublePunctuate`: `clean("Is this right?")` == `"Is this right?"`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*CleanerTest"` → FAIL.
- [ ] **Step 3: Implement** `Cleaner.clean` as an ordered pipeline: strip bracket/paren artifacts → fillers (`um, umm, uh, uhh, er, erm, hmm`, with adjacent comma) → spoken commands → dictionary → capitalize sentence starts and standalone `i` → ensure terminal punctuation (not after a newline-only result, never on empty).
- [ ] **Step 4: Run tests** → all PASS.
- [ ] **Step 5: Commit** `feat: rule-based Cleaner`.

### Task 5: Recorder and SilenceTrimmer
**Files:**
- Create: `app/src/main/java/com/pharrlabs/voice/audio/SilenceTrimmer.kt`, `app/src/main/java/com/pharrlabs/voice/audio/Recorder.kt`
- Test: `app/src/test/java/com/pharrlabs/voice/audio/SilenceTrimmerTest.kt`

**Interfaces:**
- Produces: `object SilenceTrimmer { fun trim(pcm: ShortArray, sampleRate: Int = 16000, thresholdRms: Int = 200, padMs: Int = 200): ShortArray }` (returns empty if no frame exceeds the threshold); `class Recorder { fun start(): Result<Unit>; suspend fun stop(): ShortArray }` (`start` returns failure with a reason string on mic busy/permission error; `stop` returns trimmed PCM and `emptyArray` if shorter than 300 ms of speech).
- Uses the mic-capture variant chosen in Task 2.

- [ ] **Step 1: Write failing tests** (threshold lowered from 500 to 200 after Spike A measured speech RMS ≈ 250 on the Pixel): `allSilenceReturnsEmpty` (1 s of zeros → size 0); `trimsLeadingAndTrailingSilence` (0.5 s zeros + 1 s sine at amplitude 8000 + 0.5 s zeros → size within `16000 + 2*3200` of the speech span and < 32000); `keepsInteriorPause` (speech, 1 s zeros, speech → pause preserved); `shortBlipReturnsEmpty` (100 ms of sine → size 0); `quietSpeechSurvives` (1 s sine at amplitude 350, i.e. RMS ≈ 247, the level measured on the Pixel in Spike A → size > 0).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement `SilenceTrimmer.trim`** with 20 ms RMS frames; keep `padMs` around the first/last voiced frame.
- [ ] **Step 4: Run tests** → PASS.
- [ ] **Step 5: Implement `Recorder`** over `AudioRecord` (VOICE_RECOGNITION source, 16 kHz mono, background coroutine collecting into a growable buffer, 120 s hard cap), returning `Result.failure(MicUnavailable(reason))` when `AudioRecord.state != INITIALIZED`, permission is denied, or `startRecording` throws.
- [ ] **Step 6: Device check**: debug button records 3 s; `adb logcat` shows non-empty trimmed size for speech and size 0 for silence. Commit `feat: recorder and silence trimming`.

### Task 6: Transcriber, WhisperCppEngine, ModelManager
**Files:**
- Create: `app/src/main/java/com/pharrlabs/voice/asr/Transcriber.kt`, `asr/WhisperCppEngine.kt` (from the spike, productionized), `asr/Chunker.kt`, `asr/ModelManager.kt`
- Modify: `app/src/main/cpp/whisper_jni.cpp` (as needed)
- Test: `app/src/test/java/com/pharrlabs/voice/asr/ChunkerTest.kt`, `asr/ModelManagerTest.kt`, `app/src/androidTest/java/com/pharrlabs/voice/asr/WhisperCppEngineTest.kt`

**Interfaces:**
- Consumes: `SilenceTrimmer` PCM format (16 kHz mono `ShortArray`).
- Produces:
  - `interface Transcriber { suspend fun transcribe(pcm: ShortArray): String; fun close() }`
  - `class WhisperCppEngine(modelFile: File, threads: Int = <value chosen in Task 3>) : Transcriber`
  - `object Chunker { fun split(pcm: ShortArray, maxChunkSec: Int = 28, sampleRate: Int = 16000): List<ShortArray> }` (splits at the quietest 20 ms frame in the last 5 s of each window; chunks joined by a space after transcription)
  - `class ModelManager(dir: File, http: ...) { fun modelFile(name: String): File; fun isInstalled(name: String): Boolean; suspend fun download(name: String, onProgress: (Float) -> Unit): Result<File> }` with a pinned `Map<String, ModelSpec(url, sha256, sizeBytes)>` for `small.en-q5_1` and `base.en-q5_1`; a SHA mismatch deletes the file and returns failure.

- [ ] **Step 1: Write failing tests**: `ChunkerTest.shortAudioIsOneChunk`, `longAudioSplitsUnderMax` (70 s → ≥3 chunks, each ≤ 28 s, total samples preserved), `splitsAtQuietPoint` (a 1 s zero gap near 27 s becomes the cut point); `ModelManagerTest.badChecksumDeletesFile` (fake HTTP server serving wrong bytes) and `goodChecksumKeepsFile`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement `Chunker` and `ModelManager`**, then run unit tests → PASS.
- [ ] **Step 4: Write the instrumented test** `WhisperCppEngineTest.transcribesSampleWav`: loads the JFK sample from `androidTest/assets`, asserts the result contains `"ask not what your country can do for you"` (case-insensitive) and logs the real-time factor. Also `emptyPcmReturnsEmptyString`.
- [ ] **Step 5: Implement `WhisperCppEngine`** (load once, reuse the context, `Dispatchers.Default`, split via `Chunker`, `""` for empty PCM, `Result`-free: throws `ModelMissing` if the file is absent so callers can show the error state).
- [ ] **Step 6: Run** `./gradlew :app:connectedDebugAndroidTest` on the Pixel → PASS; save the logcat RTF line to `docs/spike-results.md`.
- [ ] **Step 7: Commit** `feat: whisper transcriber, chunking, model manager`.

### Task 7: TextMerger and Injector
**Files:**
- Create: `app/src/main/java/com/pharrlabs/voice/inject/TextMerger.kt`, `inject/Injector.kt`
- Test: `app/src/test/java/com/pharrlabs/voice/inject/TextMergerTest.kt`

**Interfaces:**
- Produces:
  - `data class Merged(val text: String, val cursor: Int)`
  - `object TextMerger { fun merge(existing: String, selStart: Int, selEnd: Int, insert: String): Merged }`: replaces `[selStart, selEnd)`; adds one leading space when the character before the insertion point is non-whitespace and `insert` starts with a letter or digit; adds one trailing space only when the next character is a letter or digit; `cursor` = end of inserted text; negative selection (unknown) means "append at end".
  - `class Injector(context: Context) { fun inject(node: AccessibilityNodeInfo, text: String): InjectResult }` where `enum class InjectResult { SetText, Pasted, ClipboardOnly }`.

- [ ] **Step 1: Write failing tests**: `appendsWithSpace` (`merge("Hello", 5, 5, "world.")` → `Merged("Hello world.", 12)`); `emptyFieldNoSpace` (`merge("", 0, 0, "Hi.")` → `Merged("Hi.", 3)`); `replacesSelection` (`merge("Send it now", 5, 7, "that")` → `"Send that now"`, cursor 9); `midTextAddsTrailingSpace` (`merge("ab cd", 2, 2, "x")` → `"ab x cd"`); `unknownSelectionAppends` (`merge("Hi", -1, -1, "there")` → `"Hi there"`); `noSpaceBeforePunctuationInsert` (`merge("Hi", 2, 2, ", there")` → `"Hi, there"`).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement `TextMerger.merge`**, run → PASS.
- [ ] **Step 4: Implement `Injector.inject`**: if `node.isShowingHintText` treat existing text as `""`; read `node.text`, `textSelectionStart/End`; `performAction(ACTION_SET_TEXT)` with merged text, then `ACTION_SET_SELECTION` to `cursor`; if SET_TEXT returns false → save clipboard, set clip, `performAction(ACTION_PASTE)`, restore clipboard after 500 ms; if paste returns false → leave text on clipboard and return `ClipboardOnly`.
- [ ] **Step 5: Device check** with `adb`-driven runs into Messages and Keep: empty field (hint not duplicated), cursor mid-text, a selected range; screenshots saved under `docs/acceptance/`.
- [ ] **Step 6: Commit** `feat: text merge and injection with fallbacks`.

### Task 8: BubbleService (state machine, field filter, pipeline wiring)
**Files:**
- Create: `app/src/main/java/com/pharrlabs/voice/bubble/BubbleStateMachine.kt`, `bubble/FieldFilter.kt`, `bubble/BubbleView.kt`, `bubble/BubbleService.kt`, `res/xml/accessibility_service_config.xml` (finalized), `res/drawable/` icons
- Modify: `AndroidManifest.xml`; delete `spike/` package
- Test: `app/src/test/java/com/pharrlabs/voice/bubble/BubbleStateMachineTest.kt`, `bubble/FieldFilterTest.kt`

**Interfaces:**
- Consumes: `Recorder`, `Transcriber`, `Cleaner.clean`, `Injector.inject`, `DictionaryStore.get(): Map<String,String>` (Task 9; use an in-memory stub until then).
- Produces:
  - `sealed interface BubbleState { Idle; Listening; Processing; data class Error(val reason: String) }`
  - `class BubbleStateMachine { fun onTap(): BubbleAction; fun onRecorded(empty: Boolean): BubbleAction; fun onInjected(): BubbleAction; fun onFailure(reason: String): BubbleAction; val state: BubbleState }` with `enum class BubbleAction { StartRecording, StopAndTranscribe, Nothing, ShowError }`
  - `object FieldFilter { fun shouldShow(isEditable: Boolean, isPassword: Boolean, packageName: String, ownPackage: String): Boolean }`

- [ ] **Step 1: Write failing tests**: `BubbleStateMachineTest`: tap from Idle → `StartRecording`/Listening; tap from Listening → `StopAndTranscribe`/Processing; tap while Processing → `Nothing`; `onRecorded(empty=true)` → Idle with no injection (Review Focus 1); `onFailure` → Error then tap → Idle. `FieldFilterTest`: password field → false; non-editable → false; own package → false; editable normal → true.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement both pure classes**, run → PASS.
- [ ] **Step 4: Implement `BubbleService`**: on `TYPE_VIEW_FOCUSED` / `TYPE_VIEW_TEXT_SELECTION_CHANGED` evaluate `FieldFilter`; debounce focus events by 150 ms; keep a reference to the focused `AccessibilityNodeInfo` (refresh before injecting, `refresh()` returns false → Error "field lost"); remove the overlay on focus loss, on `TYPE_WINDOW_STATE_CHANGED` to a different package, and in `onInterrupt`/`onDestroy`; re-anchor the bubble on `TYPE_WINDOWS_CHANGED` (keyboard opening). Pipeline on tap: Recorder → trim → `Transcriber` → `Cleaner` (+ dictionary) → `Injector`, each on a coroutine scope cancelled with the service; haptic tick on start/stop; bubble color per state (red = Error).
- [ ] **Step 5: Device acceptance (Review Focus 3, 5)**: with `adb`, verify the bubble appears for Messages/Keep fields and does **not** appear on a password field (Chrome login form) or non-editable content; rapid focus switching leaves no orphan bubble (`adb shell dumpsys window | grep -i overlay` shows none after focus is lost); revoke mic permission (`adb shell pm revoke … RECORD_AUDIO`) → red error bubble, no crash.
- [ ] **Step 6: Commit** `feat: bubble service wiring pipeline end to end`.

### Task 9: Settings, onboarding, dictionary
**Files:**
- Create: `app/src/main/java/com/pharrlabs/voice/settings/DictionaryStore.kt`, `settings/Prefs.kt`, `settings/OnboardingChecks.kt`
- Modify: `SettingsActivity.kt` (screens: status checklist, model download/select, dictionary editor, bubble size/side)
- Test: `app/src/test/java/com/pharrlabs/voice/settings/DictionaryStoreTest.kt`, `settings/OnboardingChecksTest.kt`

**Interfaces:**
- Produces: `class DictionaryStore(dataStore) { suspend fun get(): Map<String,String>; suspend fun put(spoken: String, written: String); suspend fun remove(spoken: String) }` (keys stored lowercase, trimmed, blank rejected); `object OnboardingChecks { fun missing(micGranted: Boolean, serviceEnabled: Boolean, modelInstalled: Boolean, batteryExempt: Boolean): List<Step> }` where `enum class Step { GrantMic, AllowRestrictedSettings, EnableService, DownloadModel, BatteryExemption }`.

- [ ] **Step 1: Write failing tests**: dictionary round trip, `blankKeyRejected`, `keysAreCaseInsensitive`; `OnboardingChecks.missing(false,false,false,false)` returns all five in that order; `missing(true,true,true,true)` is empty.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement**, run → PASS.
- [ ] **Step 4: Build the UI**: checklist with a button per missing step (mic permission request, deep link to App info for "Allow restricted settings" with explanatory text, accessibility settings intent, model download with progress and an error line on checksum/network failure, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`); dictionary list with add/remove; seed the dictionary from the owner's local starter file.
- [ ] **Step 5: Device check** from a clean install (`adb uninstall` then install): walk the checklist, delete the model file via `adb shell rm` → checklist shows "Download model" again (Review Focus 5). Screenshot to `docs/acceptance/`.
- [ ] **Step 6: Commit** `feat: settings, onboarding and dictionary`.

### Task 10: Acceptance run, release APK, project filing
**Files:**
- Create: `docs/acceptance/checklist.md`, `release/` (APK, gitignored), updated `README.md`
- Create: `D:\Hermes Database\Personal\Projects\voice-dictation.md` (from `_Templates/project-template.md`), Todoist/tasklist entry

**Interfaces:** consumes everything above; produces the signed release APK and README.

- [ ] **Step 1: Run the acceptance checklist on the Pixel** in Messages, Gmail compose, Keep, Chrome (URL bar and a page form), Slack or Teams, and Wispr's own settings field; for each: bubble appears, 10 s sentence inserts within the 3 s target (record actual time), filler words removed, airplane mode still works. Record pass/fail and times in `docs/acceptance/checklist.md`; list failing apps under "Known limitations" in `README.md`.
- [ ] **Step 2: Long-dictation test** (Review Focus 4): 60 s of speech inserts completely with no missing text. Silence test: tap start/stop on silence inserts nothing.
- [ ] **Step 3: Fix** anything failing by returning to the owning task's tests first (add a failing test, then the fix).
- [ ] **Step 4: Create the release keystore** in `%USERPROFILE%/.pharr-voice/release.keystore` (outside the repo; password stored via the 1Password CLI/vault, never in git), configure `signingConfigs.release`, run `./gradlew :app:assembleRelease`, verify with `apksigner verify`, install with `adb install -r`, and re-run Steps 1 and 2 on the release build.
- [ ] **Step 5: README**: build/install steps, the one-time sideload steps (restricted settings, Accessibility toggle, battery exemption), known-bad apps.
- [ ] **Step 6: File the project**: write `Personal/Projects/voice-dictation.md` from the template, add the follow-up "Phase 2 plan (LLM cleanup, hold-to-talk)" to `Personal/Tasks/tasklist.md`, and commit `docs: acceptance results and release notes`.
- [ ] **Step 7: Report to the owner** with the APK path, measured latency, and the known limitations.
