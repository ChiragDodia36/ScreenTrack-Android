% ScreenTrack (Mooditer) — Master Handover Document
% On-device Mood & Content Tracker — Android + iOS
% Prepared 2026-08-20

# 1. What this project is

**ScreenTrack** (UI name **Mooditer**) is an Android app that passively observes
what is on a phone's screen and derives, **entirely on-device**, two things:

1. the **emotional tone** of the content the user is consuming, and
2. the **category of content** they are looking at,

then shows the user their mood through the day and lets them export a report.
It was built to support a research study on **social-media usage and mental
health**, but works as a standalone digital-wellbeing app.

**The single most important design fact:** *nothing derived from the screen ever
leaves the device.* All machine learning runs locally. There is no server, no
analytics, and the app is built with the `INTERNET` permission removed.

# 2. How it works, end to end

```
 Screen  ──MediaProjection──▶  one frame every ~15s (in memory only)
                                     │
                                     ▼
                         OnDeviceAnalyzer  (all on device)
             ┌───────────────┬───────────────┬──────────────────┐
             ▼               ▼               ▼
      ML Kit OCR       ML Kit Face        (frame is
      → screen text    detection →         recycled &
             │          face crop          discarded)
             ▼               ▼
     EmotionModel      FaceEmotionModel
     (RoBERTa ONNX,    (FER+ ONNX, 8
      28 GoEmotions)    face emotions)
             │               │
             └──────┬────────┘
                    ▼
            AnalysisCombiner  → blend text+face, collapse to 6 Ekman + neutral
                    │
                    ▼
        Room database (local)  ──▶  Trends / Insights UI + CSV export
```

Each capture stores only **derived numbers** (emotion scores, sentiment, content
category) plus a timestamp. **No screenshots and no raw text are ever stored.**
A 30-day rolling window auto-deletes old rows.

# 3. The two taxonomies (as delivered)

**Emotions — 6 Ekman basics + neutral (7):**
`anger, disgust, fear, joy, sadness, surprise, neutral`

The app runs an accurate 28-class GoEmotions model and *groups* its output into
these 7 using the standard Ekman mapping (`EkmanMap.kt`). Grouping a strong
27-class model is more reliable than a native 6-class model.

**Content — 7 categories:**
`news_articles_current, humorous_content, shopping_ecommerce_products, videos,
food_recipes_cooking, work_related, general_or_miscellaneous`
(`general_or_miscellaneous` is the fallback when nothing matches.)

These are the exact columns shown in the app's Emotion/Content distributions and
written to the CSV.

# 4. Tech stack

- **Kotlin**, **Jetpack Compose** + Material 3 (UI), custom **Canvas** charts
- **Room** (local SQLite), **StateFlow** MVVM
- **ML Kit** (on-device OCR + face detection)
- **ONNX Runtime Mobile** + **ONNX Runtime Extensions** (the emotion + face models)
- **Firebase App Distribution** (private tester delivery only — not a backend)
- Build: Gradle 8.7, AGP/Kotlin per `gradle/`, `compileSdk`/`targetSdk` 35, `minSdk` 26

# 5. File-by-file code map (business logic)

Source lives in `app/src/main/java/com/chiragdodia/screentrack/`. Grouped by
responsibility. **Pure** files have no Android imports and are unit-tested on the
JVM; **I/O** files touch Android/ML and are verified on device.

## 5.1 Capture (screen → frames)
| File | Responsibility |
|------|----------------|
| `ScreenCaptureService.kt` | Foreground service. Sets up `MediaProjection` + `VirtualDisplay` + `ImageReader`, grabs one frame every interval, hands it to the analyzer, writes the result to Room. Owns the multi-source **pause** (screen-off, call, manual) and the daily cleanup. Builds the analyzer **lazily on the background thread** so `startForeground()` fires instantly (Android-14 mediaProjection rule). |
| `ImageUtils.kt` | Converts an `ImageReader` `Image` to a `Bitmap` and scales it down. |
| `CallMonitor.kt` | Detects phone + VoIP/video calls (TelephonyManager / audio mode) so capture auto-pauses during calls. |
| `PauseState.kt` | Pure: combines the three pause reasons — capture runs only when all are clear. |

## 5.2 On-device ML (frame → emotion + content)
| File | Responsibility |
|------|----------------|
| `OnDeviceAnalyzer.kt` | Orchestrator. Runs OCR, runs face detection + crop, calls the two models and the pure classifiers, returns one `AnalysisResult`. Recycles the frame immediately. |
| `EmotionModel.kt` | ONNX wrapper for the RoBERTa-GoEmotions model. Feeds **raw text** (tokenizer embedded in the ONNX graph), returns 28 sigmoid scores. Memory-mapped from a file to avoid heap OOM. |
| `FaceEmotionModel.kt` | ONNX wrapper for FER+ (emotion-ferplus-8). Crops the detected face to 64×64 grayscale, returns 8 softmax scores. |
| `TextEmotion.kt`, `FaceEmotion.kt` | Small data holders for each side's scores + `hadSignal`. |

## 5.3 Pure decision logic (unit-tested)
| File | Responsibility |
|------|----------------|
| `EmotionLabels.kt` | The 28 GoEmotions labels in model-output order (`GO_EMOTIONS`). |
| `EkmanMap.kt` | Collapses 28 GoEmotions → **6 Ekman + neutral** (`EKMAN_EMOTIONS`, `toEkman`). |
| `EmotionValence.kt` | Valence (−1..+1) per emotion; turns a score map into a sentiment number. |
| `ContentClassifier.kt` | Keyword classifier → the **7 content categories** (`CATEGORY_NAMES`). |
| `FaceEmotionMap.kt` | Maps FER's 8 face emotions into the GoEmotions space (`toGoEmotions`). |
| `AnalysisCombiner.kt` | Blends text (0.6) + face (0.4), computes sentiment, **collapses to the 7 Ekman labels**, picks the dominant. Produces the stored `AnalysisResult`. |
| `EmotionSmoother.kt` | **Display-only** rolling average of recent captures with neutral-suppression, so the live "current emotion" doesn't flicker to neutral between frames. Never changes stored data. |
| `MoodBuckets.kt` | Buckets captures into hour/day/day for the mood chart (Day/Week/Month). |

## 5.4 Data layer
| File | Responsibility |
|------|----------------|
| `CaptureResultEntity.kt` | The Room row: timestamp, emotion, emotionScore, sentiment, contentType, contentConfidence, `emotionScoresJson` (the 7 Ekman scores). No image, no raw text. |
| `AppDatabase.kt`, `CaptureDao.kt`, `CaptureRepository.kt` | Room DB, queries (latest / recent / since / delete-older-than), repository. Destructive migration (safe — pre-study, no real data). |
| `EmotionScoresJson.kt` | Tiny JSON encode/decode for the score map (no extra dependency). |
| `CaptureViewModel.kt` | StateFlow MVVM: latest (smoothed), recent, mood buckets, running/paused state, time range. |
| `ServiceStatusStore.kt` | Persists running/paused/last-error/last-capture flags for the UI. |
| `TimeRangeOption.kt` | The Trends/Insights time-range chips. |

## 5.5 UI
| File | Responsibility |
|------|----------------|
| `MainActivity.kt` | The whole Compose UI: 4 screens (Home, Trends, Insights, Settings), bottom nav, MediaProjection consent flow, CSV **Share** (FileProvider), survey buttons. |
| `MoodBarChart.kt` | The diverging mood bar chart (up=positive, down=negative), Day/Week/Month tabs, tap-to-select, empty=flat, faint=low-usage. |
| `ChartComponents.kt` | Donut/bar charts, emotion colors, wellness gauge, stat tiles. |

## 5.6 Report + survey + notifications
| File | Responsibility |
|------|----------------|
| `CsvExporter.kt` | Builds the **hourly CSV**: one row per hour, 7 emotion score columns + positive/negative/neutral % + 7 content % columns. |
| `ModelAssets.kt` | Copies a bundled `.onnx` asset to cache once so ORT can memory-map it. |
| `SurveyReminder.kt`, `SurveyReminderReceiver.kt` | Daily reminder notification (AlarmManager) that opens the study Google Form in the browser. |
| `NotificationHelper.kt`, `SuggestionEngine.kt` | Wellbeing notification + rule-based suggestions. |

# 6. The ML models (how they were made)

Conversion tooling lives in `tools/emotion-model/` (dev-only, not shipped in the app).

- **Emotion:** `SamLowe/roberta-base-go_emotions` → exported to ONNX, int8-quantized
  (`export.sh`), tokenizer embedded via ONNX Runtime Extensions
  (`embed_tokenizer.py`). Result: `emotion_model.onnx` (~120 MB) + `tokenizer.onnx`
  (~1.3 MB) in `app/src/main/assets/`.
- **Face:** `emotion-ferplus-8` from the ONNX Model Zoo, int8-quantized
  (`fer/verify_fer.py`) → `fer_model.onnx` (~21 MB).

**These `.onnx` assets are gitignored** (too large for GitHub). To rebuild them:
```bash
cd tools/emotion-model
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
bash export.sh                       # emotion model + int8 + verify
python embed_tokenizer.py            # tokenizer.onnx
cp onnx/model.int8.onnx  ../../app/src/main/assets/emotion_model.onnx
cp onnx/tokenizer.onnx   ../../app/src/main/assets/tokenizer.onnx
curl -sL -o fer/emotion-ferplus-8.onnx \
  https://huggingface.co/onnxmodelzoo/emotion-ferplus-8/resolve/main/emotion-ferplus-8.onnx
python fer/verify_fer.py
cp fer/emotion-ferplus-8.int8.onnx ../../app/src/main/assets/fer_model.onnx
```

# 7. Privacy posture (for IRB / review)

- No `INTERNET` permission (stripped, including ML-Kit-merged) — verified absent in the built APK.
- No screenshots or raw text stored — frames are analyzed in memory and recycled.
- No analytics, no crash reporting, no backend. On-device only.
- Faces analyzed on device (never stored); capture auto-pauses during calls.
- The **only** thing that leaves the phone is a survey the user *chooses* to submit
  (a Google Form opened in the browser) and a CSV the user *chooses* to share.

# 8. Known limitations (important, honest)

- Emotion is read from **on-screen text and visible faces only.** Content whose
  emotion lives in **audio or motion** (e.g. a funny video with neutral on-screen
  text and no clear face) is correctly read as **neutral** — the app cannot hear
  audio or "understand" video. This is a hard limit, not a bug.
- Fine-grained emotion from short OCR fragments is imperfect; collapsing to 6
  Ekman basics improves reliability.
- After a phone reboot, Android requires the user to re-grant screen capture.

# 9. How to build & run (Android)

**Requirements:** macOS/Linux/Windows, Android Studio (or command-line Gradle 8.7 +
JDK 17), an Android device (min Android 8.0) or emulator. The `.onnx` model assets
must be present (see §6).

```bash
# 1. Put the three model files in app/src/main/assets/ (see §6)
# 2. Debug build onto a connected device/emulator:
./gradlew installDebug
# 3. Release build (signed via keystore.properties or SCREENTRACK_KEYSTORE_* env):
./gradlew assembleRelease        # APK in app/build/outputs/apk/release/
```

**Run:** open the app → **Start** → grant the screen-record + notification prompts.
Use the phone normally; open **Trends** for the mood chart, **Insights** for the
wellness summary, **Settings → Share CSV Report** to export.

**Distribute to testers (private, no Play Store):**
```bash
firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
  --app <FIREBASE_ANDROID_APP_ID> --groups "study-participants" --project <PROJECT_ID>
```

# 10. Repository & branches

- GitHub: `github.com/ChiragDodia36/ScreenTrack-Android`
- Active branch with all current work: **`feature/mood-chart-27emotion`**
- Full docs in `docs/` (this handover, the IRB data-handling doc, the on-device-ML
  spec, the iOS PRD, and the implementation plans under `docs/superpowers/`).

*See the separate Android and iOS handover documents for platform-specific depth.*
