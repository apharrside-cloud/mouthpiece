# Mouthpiece model benchmarks (on-device, Pixel 9 Pro XL, 4 threads)

Test set: 40 clips of LibriSpeech test-other (969 words, ~6 min audio), scored by word error rate (WER) after
lowercasing and stripping punctuation. RTF = processing time / audio time (lower is faster; 1.0 = real time).
Kit: `C:/pharr-build/bench` (bench.cpp mirrors the app's whisper decode settings; run_*.sh drive the phone;
score*.py compute WER). LibriSpeech is read audiobooks, not dictation, so treat gaps under ~1 point as noise.

| Model | Engine | WER | RTF | Download |
|---|---|---|---|---|
| Balanced: Whisper small.en q5_1 (current default) | whisper.cpp | 9.5% (40 clips) | 0.4-0.7 | 190 MB |
| Whisper small.en q8_0 | whisper.cpp | 10.6% (40 clips) | 0.25-0.7 | 264 MB |
| **Parakeet TDT 0.6B v2 int8** (shipped as "Accurate + quick") | sherpa-onnx | **6.3%** (40 clips) | 0.24-0.45 | 660 MB |
| Parakeet TDT-CTC 110M int8 (not shipped: GitHub-only tar.bz2) | sherpa-onnx | 6.9% (40 clips) | 0.06 | 126 MB |
| Parakeet 0.6B redux (multilingual) | sherpa-onnx | 34% (broken on this set) | 0.29 | 660 MB |
| Moonshine base | sherpa-onnx | 33% | 0.06 | small |
| Distil-Whisper small.en | whisper.cpp | 10.6% (20 clips) | 0.46 | 336 MB |
| Distil-Whisper medium.en | whisper.cpp | 57% (20 clips, shrunk window) | 1.6 | 794 MB |
| Whisper medium.en q5_0 (kept; fixed window: 6.0% on 8 clips, was 17.4%) | whisper.cpp | 13% (20 clips, shrunk window) | 4.1-4.9 | 540 MB |
| Whisper large-v3-turbo q5_0 (DROPPED from the app, too slow) | whisper.cpp | 48% shrunk window / 4.2% full window (8 clips) | 5.9 | 574 MB |

## Findings
- whisper.cpp `audio_ctx` shrinking is only safe for small and below. Medium and large-turbo loop/garble with it
  (fixed in whisper_jni.cpp: shrink only when n_audio_state <= 768).
- Beam search made Balanced slower with no accuracy gain; not used.
- A candidate worth adding needs to beat Parakeet 0.6B on WER at RTF <= ~0.5, or match ~6-7% at a much smaller
  size / faster speed (Parakeet 110M is that case).
- Parakeet 110M is the obvious next tier: needs tar.bz2 extraction (commons-compress) or a self-hosted copy.

## Parakeet variants (40 clips, same set)
- Parakeet TDT 0.6B **v2** (shipped): 6.3% WER.
- Parakeet 0.6B **ultra** int8: 11.3% WER (one clip with 45 errors; RTF 0.19). Not better than v2.
- Parakeet 0.6B **v3** int8 (multilingual): 11.2% WER, RTF 0.24. Not better than v2 for English.
- Verdict: v2 stays the in-between tier. Next candidate to test is Parakeet 110M (126 MB, 6.9% WER, ~10x faster).

## Looking for a tier between Parakeet 0.6B v2 and Medium (Nov 20)
Nothing found that fits. Parakeet v2 (6.3%, RTF ~0.3) already matches Medium (6.0%, RTF ~4.9).
- Distil-Whisper medium.en, full window: 8.5% WER on 20 clips, RTF 1.76 (794 MB). Worse than Balanced and 4x slower than Parakeet.
- Parakeet unified en 0.6B int8: 8.7% WER, RTF 0.38. Worse than v2.
- Whisper small.en f16: 7.2% WER, RTF 0.68 (487 MB). Fits between Balanced and Parakeet, not above Parakeet.
- Whisper large-v3-turbo, full window: 4.2% WER on 8 clips only, RTF 5.9. The only thing more accurate than Parakeet, and unusably slow.
