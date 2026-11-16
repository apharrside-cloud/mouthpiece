# Mouthpiece

Free, private voice typing for Android. A floating bubble appears when you focus a text field in any app;
tap it, talk, tap again, and the cleaned-up text is typed at your cursor. Speech is transcribed **on your phone**
with [OpenAI Whisper](https://github.com/openai/whisper) running through whisper.cpp, so your voice never leaves the
device and it works in airplane mode. No account, no ads, no subscription.

**Download:** [mouthpiece.pharrlabs.com](https://mouthpiece.pharrlabs.com) or the [latest GitHub release](../../releases/latest)
(each release lists a SHA-256 checksum). Android 12 or newer, 64-bit ARM phone (arm64).

## Install (sideload)
1. Download the APK and open it. Android asks you to allow installs from your browser or file manager.
2. Open **Mouthpiece**. The checklist shows what is left:
   - **Grant microphone permission.**
   - **Allow restricted settings** (sideloaded apps only): tap Mouthpiece once in Accessibility (it says "Restricted setting"), then App info, the top-right menu (it only appears after that tap), then **Allow restricted settings**.
   - **Enable the service**: Accessibility, Installed apps, Mouthpiece, on.
   - **Download a speech model**: one-time download (32 MB to 575 MB, your choice), SHA-256 verified. Everything after that is offline.
   - **Battery exemption** (recommended) so Android does not stop the service.
3. Tap a text field in any app. A purple bubble appears next to it.

Bubble colors: purple idle, green listening, amber transcribing, red error (a toast gives the reason).
The bubble never appears on password fields or inside Mouthpiece itself.

## Using it
- **Tap** the bubble to start and tap again to finish (it keeps listening through pauses; an optional stop-on-silence timer is in Settings > Dictation). **Press and hold** to talk and release to finish.
- Text is transcribed phrase by phrase while you talk, so there is little waiting after you stop. A live caption shows next to the bubble.
- Cleanup: fillers ("um", "uh") removed, sentences capitalized, punctuation added. One-line fields (subject, URL bar, search) get no trailing period; prose fields do.
- Smart edit: "scratch that" drops the last sentence; "no wait", "I mean", "or rather" replace what you just said.
- Spoken commands: "comma", "period", "question mark", "exclamation point", "colon", "semicolon", "new line", "new paragraph".
- Styles per app (Standard, Casual, Formal, Literal) with built-in presets; override period and style per app in Settings > Apps, or hide the bubble there.
- After each dictation an **Undo / Retry** chip appears for 6 s. If you fix a word afterwards, a chip offers to add it to your dictionary.
- Drag the bubble anywhere; it remembers the spot. Size, opacity and side are in Settings > Bubble.
- Settings > History keeps the last 20 dictations (on-device) so you can copy text an app swallowed. Settings > Home has Appearance (System/Light/Dark) and Export/Import for your dictionary, rules and preferences.
- If a field refuses direct text entry, the text is pasted via the clipboard; if that fails too, it is left on the clipboard with a toast.

## Build
```
source scripts/env.sh                       # JAVA_HOME / ANDROID_HOME for the portable toolchain in C:\Android
./gradlew :app:testDebugUnitTest            # unit tests (pure logic: cleanup, gestures, streaming, undo, history, backup)
scripts/deploy.sh                           # debug build, install on the paired Pixel, launch
./gradlew :app:assembleRelease              # signed APK in app/build/outputs/apk/release
```
Release signing reads `~/.pharr-voice/keystore.properties` (outside the repo, never committed). Builds you sign with your
own key will not install over the official APK; uninstall it first. `scripts/deploy.sh` installs a debug build on a paired phone.
Optional: `~/.pharr-voice/owner-terms.txt` (`spoken=Written|spoken=Written`) seeds a starter dictionary into your own build.

Do not run `gradlew connectedDebugAndroidTest`: it uninstalls the app afterwards and deletes the downloaded model.
Use `installDebug installDebugAndroidTest`, then `adb shell am instrument -w ...`.

Debug builds only: `adb shell am broadcast -a com.pharrlabs.voice.DEBUG_DICTATE --es wav jfk.wav` runs a WAV from the
app's files dir through transcribe, clean and inject into the focused field (`--es text "raw text"` skips transcription).

## Known limitations
See `docs/acceptance/checklist.md` for per-app results.
- Fields that report themselves oddly may be classified wrong; per-app Period rules are the override.
- Phone locked or screen off: whisper runs about 4x slower (not a real dictation case).
- Apps that block accessibility text entry fall back to clipboard paste.
- Undo only works when the text went in directly (not through clipboard paste).
- LLM-based rewriting was evaluated and left out; rule-based smart edit covers the common cases offline.

## Contributing
Issues and pull requests are welcome. Keep logic in pure Kotlin with a unit test first (`./gradlew :app:testDebugUnitTest`).

## License
MIT, see `LICENSE`. Third-party components are listed in `THIRD_PARTY_NOTICES.md`.
