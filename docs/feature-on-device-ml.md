# Feature: On-Device ML Migration

> Part of: [MASTER.md](MASTER.md) → Play Store compliance

**Status:** Implemented — pending local device test
**Priority:** Critical — required to unblock Play Store approval

---

## Why This Change

The app currently sends raw JPEG screenshots to a remote server (`POST /api/upload_screenshot`). Google Play treats this as a screen surveillance pattern and restricts such apps to organization accounts only.

Moving all analysis on-device means no screen content ever leaves the device, which removes the rejection reason and allows publishing under a personal developer account.

---

## What Changed

| Before | After | Status |
|--------|-------|--------|
| `FrameUploader.kt` sends raw JPEG to backend | Deleted | ✅ Done |
| Backend does OCR + emotion classification | ML Kit OCR + keyword emotion classifier on-device | ✅ Done |
| Backend returns JSON with emotion/sentiment | `OnDeviceAnalyzer.kt` returns result directly | ✅ Done |
| `BACKEND_URL` / `BACKEND_API_KEY` BuildConfig fields | Removed from `build.gradle.kts` | ✅ Done |
| Settings: backend URL + test connection card | Removed from `SettingsScreen` | ✅ Done |
| No consent dialog | First-launch consent dialog added | ✅ Done |
| `ScreenCaptureService` validates backend URL | Validates consent flag instead | ✅ Done |

---

## Chosen Stack

| Component | Library | APK Size | Inference Time |
|-----------|---------|----------|----------------|
| OCR | ML Kit Text Recognition 16.0.0 | +9.8 MB | 50–200ms |
| Emotion classification | TFLite MobileBERT (quantized) | +2–5 MB | 50–100ms |
| Content type | Rule-based keyword matching | 0 MB | <1ms |
| **Total** | | **~12–15 MB** | **~150–300ms** |

RAM peak during inference: ~400–600 MB (fine on any modern Android phone with 4GB+ RAM).

**Why not Ollama?** Ollama does not run natively on Android — only through Termux (Linux emulation). It does not use mobile NPU/GPU, requires 2–4 GB RAM for the runtime alone, and inference takes 500–2000ms. Not viable for production.

---

## Model

- **Model:** `distilbert-base-uncased-emotion` converted to TFLite (quantized)
- **Source:** Hugging Face → convert with `optimum` CLI to `.tflite`
- **Size:** ~10–15 MB quantized
- **Output classes:** joy, sadness, anger, fear, love, surprise
- **Class mapping to existing DB schema:**
  - `love → joy`
  - `neutral` added as fallback for low-confidence predictions

Place model file at: `app/src/main/assets/emotion_model.tflite`

---

## Files to Create / Modify

### New: `OnDeviceAnalyzer.kt`
```kotlin
class OnDeviceAnalyzer(context: Context) {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val interpreter: Interpreter  // loaded from assets/emotion_model.tflite

    suspend fun analyze(bitmap: Bitmap): AnalysisResult {
        val text = extractText(bitmap)           // ML Kit OCR
        val emotion = classifyEmotion(text)      // TFLite inference
        val contentType = inferContentType(text) // keyword rules
        return AnalysisResult(emotion, sentimentFromEmotion(emotion), contentType, text.take(200))
    }

    private fun inferContentType(text: String): String {
        val lower = text.lowercase()
        return when {
            listOf("tweet", "retweet", "instagram", "tiktok", "facebook").any { it in lower } -> "social_media"
            listOf("inbox", "message", "chat", "reply", "dm").any { it in lower } -> "messaging"
            listOf("breaking", "headline", "report", "article").any { it in lower } -> "news"
            listOf("checkout", "cart", "buy now", "order").any { it in lower } -> "shopping"
            listOf("meeting", "spreadsheet", "deadline", "email").any { it in lower } -> "work"
            listOf("score", "level", "game", "play", "lives").any { it in lower } -> "gaming"
            else -> "general"
        }
    }
}
```

### Modify: `ScreenCaptureService.kt`
Replace the `FrameUploader.uploadFrame()` call:
```kotlin
// Remove:
val result = frameUploader.uploadFrame(imageBytes, backendUrl, apiKey)

// Add:
val result = onDeviceAnalyzer.analyze(scaledBitmap)
// Pass bitmap directly — skip JPEG encode/decode cycle
```

### Modify: `app/build.gradle.kts`
```kotlin
// Add
implementation("com.google.mlkit:text-recognition:16.0.0")
implementation("org.tensorflow:tensorflow-lite:2.14.0")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

// Remove (no longer needed for core function)
// BuildConfig fields: BACKEND_URL, BACKEND_API_KEY
```

### Delete: `FrameUploader.kt`
No longer needed once on-device analysis is wired up.

### Modify: `MainActivity.kt`
- Remove backend URL and "Test Connection" from Settings screen
- Add first-launch consent dialog (see Consent Dialog section below)

---

## Consent Dialog (First Launch)

Show once before the MediaProjection permission prompt. Persisted in SharedPreferences.

**Title:** "Screen Recording Notice"

**Body:**
- ScreenTrack captures your screen to analyze your emotional wellbeing
- All analysis happens entirely on your device — nothing is sent to any server
- No screenshots are stored — only the analysis result (emotion, sentiment score)
- You can stop recording at any time from the Home screen

**Buttons:** "I Understand, Continue" / "Cancel"

```kotlin
val prefs = getSharedPreferences("screentrack_prefs", MODE_PRIVATE)
if (!prefs.getBoolean("consent_given", false)) {
    showConsentDialog(
        onAccept = {
            prefs.edit().putBoolean("consent_given", true).apply()
            requestMediaProjection()
        },
        onDecline = { /* user can start later */ }
    )
} else {
    requestMediaProjection()
}
```

`ScreenCaptureService` should also guard against starting without consent:
```kotlin
val consentGiven = getSharedPreferences("screentrack_prefs", MODE_PRIVATE)
    .getBoolean("consent_given", false)
if (!consentGiven) { stopSelf(); return START_NOT_STICKY }
```

---

## Emotion → Sentiment Score Mapping

Since TFLite returns emotion class only (not a continuous sentiment score), derive `sentimentScore` from emotion:

```kotlin
fun sentimentFromEmotion(emotion: String): Double = when (emotion) {
    "joy"      ->  0.8
    "surprise" ->  0.3
    "neutral"  ->  0.0
    "sadness"  -> -0.5
    "fear"     -> -0.6
    "anger"    -> -0.7
    else       ->  0.0
}
```

---

## Verification Checklist

- [ ] Install debug build: `./gradlew installDebug`
- [ ] Tap "Start Capture" — consent dialog appears (first time only)
- [ ] Accept consent — MediaProjection permission dialog appears
- [ ] Grant permission — capture starts, notification appears
- [ ] Wait ~10 seconds — History screen shows a new result
- [ ] Home screen shows emotion label and sentiment score
- [ ] Tap "Start Capture" again (after stopping) — consent dialog does NOT appear again
- [ ] Insights screen wellness score calculates correctly
- [ ] Logcat filter `FrameUploader` shows zero output (deleted)
- [ ] Settings screen has no "Connection" / "Test Connection" section
