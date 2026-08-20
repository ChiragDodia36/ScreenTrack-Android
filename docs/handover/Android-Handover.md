% ScreenTrack — Android Technical Handover
% Build, run, architecture, and operations
% Prepared 2026-08-20

# 1. Scope

This document covers the **Android app** in depth: environment, build, signing,
run, device requirements, the service/permission model, the ML runtime, and
troubleshooting. For the project overview and the file-by-file business-logic map,
see the **Master Handover** document.

- Package / namespace: `com.chiragdodia.screentrack`
- `minSdk` 26 (Android 8.0), `compileSdk`/`targetSdk` 35, Gradle 8.7
- Version: `versionCode 6`, `versionName 1.1.3`

# 2. Prerequisites

- **JDK 17** and **Android SDK** (API 35). Android Studio recommended.
- An **arm64** Android device (min Android 8.0) or an arm64 emulator system image.
  The release build restricts ABI to `arm64-v8a` to keep the APK small.
- The three **ONNX model assets** in `app/src/main/assets/` (see §6). Without them
  the app compiles but crashes at first capture.
- Optional for distribution: Firebase CLI (`npm i -g firebase-tools`).

# 3. Getting the source

```bash
git clone https://github.com/ChiragDodia36/ScreenTrack-Android.git
cd ScreenTrack-Android
git checkout feature/mood-chart-27emotion   # branch with all current work
```

# 4. Build & install

```bash
# Debug (fastest; installs to a connected device/emulator)
./gradlew installDebug

# Release APK (signed) — output: app/build/outputs/apk/release/app-release.apk
./gradlew assembleRelease

# Release AAB (for Play Store, if ever used)
./gradlew bundleRelease
```

**adb tips** (SDK platform-tools):
```bash
adb devices -l                 # confirm the device is 'device' (not 'unauthorized')
adb install -r app-release.apk # manual install
adb logcat -d -b crash         # last crash, if any
```

# 5. Signing

Release signing reads, in order:
1. `keystore.properties` at the repo root (gitignored), keys:
   `SCREENTRACK_KEYSTORE_FILE`, `SCREENTRACK_KEYSTORE_PASSWORD`,
   `SCREENTRACK_KEY_ALIAS`, `SCREENTRACK_KEY_PASSWORD`; or
2. the same values as environment variables `SCREENTRACK_KEYSTORE_*`.

If neither is present, the release build is unsigned. Keep the keystore and its
passwords **out of git** (they already are).

# 6. The ML model assets (required)

`app/src/main/assets/` must contain:

| File | ~Size | Source |
|------|-------|--------|
| `emotion_model.onnx` | 120 MB | RoBERTa-GoEmotions, int8 (regenerate via `tools/emotion-model/export.sh`) |
| `tokenizer.onnx` | 1.3 MB | ONNX Runtime Extensions (`embed_tokenizer.py`) |
| `fer_model.onnx` | 21 MB | emotion-ferplus-8, int8 (`fer/verify_fer.py`) |

They are **gitignored** (too big for GitHub). Regeneration commands are in the
Master Handover §6. `ModelAssets.kt` copies each to cache on first use so ONNX
Runtime memory-maps it (native memory) instead of loading it onto the Java heap —
this is what fixed the out-of-memory crash.

# 7. Permissions & the capture service

Declared in `AndroidManifest.xml`:

- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` — the capture service.
- `POST_NOTIFICATIONS` — the foreground + reminder notifications.
- `READ_PHONE_STATE` — call auto-pause (`CallMonitor`).
- `INTERNET` and `ACCESS_NETWORK_STATE` are **explicitly removed** with
  `tools:node="remove"` (they are merged in by ML Kit) so the app has no network
  capability. **Do not re-add them** without updating the IRB/privacy story.
- A `FileProvider` (`${applicationId}.fileprovider`) exposes the CSV for sharing.

**Service lifecycle (`ScreenCaptureService`):**
1. `MainActivity` requests MediaProjection consent; on approval it starts the FGS,
   passing the consent token.
2. `onStartCommand` checks consent, calls `startForeground()` **immediately** (the
   analyzer is built lazily on the capture background thread — building it on the
   main thread before `startForeground` caused an Android-14 `SecurityException`).
3. `ImageReader` delivers frames; one per `intervalMs` is analyzed, the rest closed.
4. Pause is the OR of three flags (screen-off, call, manual) via `PauseState`.
5. `onDestroy` closes the analyzer and projection.

**Note:** after a reboot or if Android kills the process, screen capture cannot
silently resume (the consent token is one-time) — the user must tap Start again.

# 8. The ML runtime on device

- `EmotionModel` and `FaceEmotionModel` use **ONNX Runtime Mobile**
  (`com.microsoft.onnxruntime:onnxruntime-android`) + **ORT Extensions**
  (`onnxruntime-extensions-android`) for the embedded tokenizer.
- The emotion model takes **raw text** (tokenizer is inside the ONNX graph), so
  there is no hand-written BPE tokenizer in Kotlin.
- Per-inference latency on a mid-range arm64 device (Samsung A17): ~20–180 ms.
- `.onnx` files are kept uncompressed in the APK (`androidResources.noCompress`).

# 9. Data & storage

- Room DB `screentrack.db`, table `capture_results` (`CaptureResultEntity`).
- Each row: timestamp, dominant emotion, emotion score, sentiment, content type,
  content confidence, and `emotionScoresJson` (the 7 Ekman scores). **No image,
  no raw text.**
- 30-day rolling delete; destructive migration on version bump (safe pre-study).
- Inspect on a debug build:
  `adb exec-out run-as com.chiragdodia.screentrack cat databases/screentrack.db > out.db`
  then open `out.db` in any SQLite tool.

# 10. Testing

```bash
./gradlew testDebugUnitTest      # 42 JVM unit tests across 13 classes
```
Pure logic (EkmanMap, ContentClassifier, AnalysisCombiner, EmotionSmoother,
MoodBuckets, CsvExporter, EmotionValence, PauseState, SurveyReminder, …) is fully
unit-tested. Model I/O and the UI are verified on device.

# 11. Distribution (private, no Play Store)

Testers receive the app through **Firebase App Distribution** (Firebase is used
only as a delivery channel — there is no backend):

```bash
firebase login    # as the account that owns the Firebase project
firebase appdistribution:distribute \
  app/build/outputs/apk/release/app-release.apk \
  --app <FIREBASE_ANDROID_APP_ID> \
  --groups "study-participants" \
  --release-notes "..." \
  --project <FIREBASE_PROJECT_ID>
```
Add testers by email to the tester group; each gets an install invite and uses the
"Firebase App Tester" app to download.

**Google Play note:** an earlier submission was rejected under Play Console
Requirements (an org/organization account requirement for this app category), not
for the screenshots themselves. Private Firebase distribution avoids Play review
entirely and is the recommended path.

# 12. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| Crash on Start with `SecurityException … mediaProjection` | Heavy work before `startForeground()`. Keep analyzer construction off the `onStartCommand` main-thread path (already fixed). |
| `OutOfMemoryError` loading the model | Load via file + memory-map, not `assets.readBytes()` (already fixed in `ModelAssets`/`EmotionModel`). |
| App builds but crashes at first capture | The `.onnx` assets are missing — regenerate (§6). |
| `parseDebugLocalResources … Failed file name validation` | A macOS-duplicated resource like `foo 2.xml`. Delete files with a space-number suffix under `app/src` and `app/build`. |
| Everything reads "neutral" on a video | Expected — the content's emotion is in audio/motion, which the app can't read. Test with text-emotional content or a clear face. |
| `adb` shows `unauthorized` | Accept the USB-debugging prompt on the phone; if none, revoke authorizations in Developer Options and replug. |

# 13. Suggested next steps

1. Rebuild the three model assets on the new machine (§6) and confirm `./gradlew
   installDebug` runs on a device.
2. Decide the emotion/content set is final (currently 6 Ekman + neutral, 7 content).
3. If distributing, set up a Firebase project you own and add tester emails.
4. Bump `versionCode`/`versionName` before each new distribution build.
