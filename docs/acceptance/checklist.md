# Acceptance results (Pixel 9 Pro XL, Android 17), release build 0.1.0

Method: real taps/focus on the phone over adb. The speech step uses the debug-only broadcast hook
(`--es text` / `--es wav`) because nobody can speak to the phone during automated runs; the real-mic
tap-to-record path was exercised separately on the release build (see bottom). Evidence screenshots are in this folder.

| Check | Result |
|---|---|
| Messages: bubble on compose field, inserts cleaned text at cursor, mid-text insert keeps surrounding text | PASS |
| Gmail compose: subject and body both receive text; focus moves between them (needed a live-focus fix) | PASS (subject gets a trailing period: Gmail reports it as multi-line) |
| Gmail body placeholder "Compose email" not merged into text | PASS (after fix) |
| Chrome web form field: bubble + insert | PASS |
| Chrome URL bar: bubble + insert | PASS (trailing period added, Chrome reports multi-line) |
| Chrome password field: no bubble | PASS |
| Rapid focus switching (12 taps): ends with exactly one overlay; zero after Home | PASS |
| Return to an app with a field already focused: bubble reappears | PASS (after fix) |
| 33 s dictation (3 chunks) into Messages | PASS: 17 s total, text correct and complete |
| Fillers removed ("um", "uh"), "comma", "new paragraph", "question mark" | PASS |
| Mic permission revoked: no crash, error logged, bubble recovers | PASS |
| Setup UI renders; mic grant, model download with progress + SHA-256 check (190,098,681 B) | PASS (progress thread crash found and fixed) |
| Accessibility enable through real Settings flow (release APK) | PASS (no restricted-settings block when installed via adb) |
| Release APK: bubble idle -> green + system mic indicator while recording -> idle; silence inserts nothing | PASS |
| App icon (adaptive, themed) | PASS |
| Keep, Slack, Teams | NOT RUN: Keep opened but not driven; Slack and Teams are not installed |
| Spoken input through the real mic into text | NOT VERIFIED: the phone's speaker volume could not be raised from adb, so a played-back clip never reached the mic. Needs one real sentence from the owner |
| Airplane mode | NOT RUN (would drop the adb link). Only the model download uses the network |


## v0.2.0 (Phase 2)

216 unit tests green. Release APK `release/pharr-voice-0.2.0.apk` installed over 0.1.0 in place (model, dictionary and accessibility service kept).

| Item | Unit tests | On device |
|---|---|---|
| 1 One-line field classification, period modes, per-app period | PASS | NOT RUN (phone locked) |
| 2 Smart edit (scratch that, self-corrections, bullet lines) | PASS | NOT RUN |
| 3 Per-app style presets | PASS | NOT RUN |
| 4 Learn from corrections, remember chip | PASS | NOT RUN |
| 5 Hold-to-talk gesture, silence auto-stop | PASS | NOT RUN (needs real touch + voice) |
| 6 Streaming transcription | PASS | PASS via debug replay: 35 s clip, 3 phrases: tail after stop 11.4 s vs 42.3 s whole-clip (phone locked, ~4x slow) |
| 7 Undo / Retry chip | PASS | NOT RUN |
| 8 Tabbed settings (Home, Dictation, Bubble, Words, Apps, History), test box | n/a (UI) | Builds and installs; not visually checked |
| 9 Draggable bubble, opacity, level pulse, hidden apps | PASS (placement) | NOT RUN |
| 10 History log, export/import backup | PASS | NOT RUN (file picker flow) |
