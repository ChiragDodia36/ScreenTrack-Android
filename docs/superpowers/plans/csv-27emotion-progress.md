# CSV / 27-Emotion — Progress & Handoff Note

**Branch:** `feature/csv-27emotion` (all new work here; not pushed yet)
**Plan:** [27emotion-ml-and-hourly-csv.md](27emotion-ml-and-hourly-csv.md)
**Paused:** mid Phase 5 on-device verification.

## Two versions (as requested)
| Version | Branch | State |
|---------|--------|-------|
| **PDF** | `feature/on-device-analysis-privacy` @ `80b7484` | Existing app: keyword 5-emotion + PDF export. Already on GitHub. Untouched. |
| **CSV** | `feature/csv-27emotion` | New: 27-emotion ONNX model + hourly CSV. **Local only.** |

## Done (committed on feature/csv-27emotion)
- **Phase 0 — spike → GO.** Converted `SamLowe/roberta-base-go_emotions` to int8 ONNX (120 MB), embedded tokenizer (raw-text input). On-device: **88–180 ms**, accurate, no OOM. Tooling in `tools/emotion-model/`.
- **Phase 1** (`d238f86`) — `EmotionModel` ONNX wrapper, 28 GoEmotions labels, valence table; replaced keyword `TextSentimentClassifier`.
- **Phase 2** (`162fd36`) — persist 28-score map (`emotionScoresJson`), Room v3 (destructive migration).
- **Phase 4** (`e39e4a8`) — hourly `CsvExporter` (28 emotion score cols + sentiment % + content %); export writes `.csv`; deleted `PdfExporter`.
- All JVM unit tests green. Model isolated-tested on-device (`EmotionSpikeTest`).

## In progress (this pause — committed but NOT yet verified on-device)
- **Crash fix:** first real-app launch crashed with `SecurityException: Starting FGS mediaProjection` at `startForeground()`. Cause: I'd moved analyzer construction (now loads ONNX native lib) onto the main thread right before `startForeground()`, delaying the FGS start; Android 14 rejected it. **Fix:** analyzer is now built lazily on the capture background thread (`ScreenCaptureService`), so `startForeground()` fires immediately.
- **arm64 abiFilter** added (APK 305 MB → ~150 MB; study phones are arm64).

## ⚠️ Immediate next step when resuming
1. **Verify the crash fix on the phone:** open app → Start → Allow → let it run ~1 min. It should NOT crash.
2. Confirm emotion scores are stored: `adb shell run-as com.chiragdodia.screentrack` → query `screentrack.db` `capture_results.emotionScoresJson`.
3. Export a CSV in-app; open it; check hourly rows + 28 emotion score columns look sane.
4. Watch for a possible **OOM when the 120 MB model loads** during the first capture (process death → START_STICKY restart → stale-consent crash). If that happens, add a try/catch around `startForeground` (stopSelf on failure) and/or reduce model footprint.

## Phase 3 — DONE (code-complete, `7c0b93e`)
- `emotion-ferplus-8` int8 ONNX (20 MB) bundled as `fer_model.onnx` (gitignored).
- `FaceEmotionModel` (crop face → 64x64 grayscale → 8 FER emotions), `FaceEmotionMap` (8→28), `AnalysisCombiner` now blends text+face 28-score maps. `FaceSignal` removed.
- On-device FER inference NOT yet run — part of the pending verification.

## Remaining
- **On-device verification (BLOCKED on phone):** crash fix + text model + face model + CSV, and confirm no OOM with BOTH models loaded (~140 MB of models). If OOM on restart → add try/catch around `startForeground` (stopSelf on failure) and/or make the FER model optional.
- **Phase 5:** after verification → version bump (versionCode 7 / 1.2.0), release build, distribute the CSV version via Firebase.

## Notes
- **Model asset is gitignored** (120 MB > GitHub 100 MB). Regenerate: `tools/emotion-model/` → `export.sh` → copy `model.int8.onnx` to `app/src/main/assets/emotion_model.onnx`. `tokenizer.onnx` (1.3 MB) is committed.
- User mentioned **"one more change"** not yet specified — get it before finalizing.
- Commits: author `chiragdodia36`, no Claude co-author. Push only to `feature/csv-27emotion`.
