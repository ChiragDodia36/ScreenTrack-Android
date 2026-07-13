# ScreenTrack Android — Master Document

**Package:** `com.chiragdodia.screentrack`
**Version:** 1.0.1 (versionCode 2)
**Min SDK:** 26 (Android 8.0) | **Target SDK:** 35
**Language:** Kotlin + Jetpack Compose | **Theme:** Material 3 Dark

> Sub-documents: [On-Device ML Migration](feature-on-device-ml.md)

---

## App Overview

ScreenTrack is a digital wellbeing app that captures the user's screen at configurable intervals, analyzes the emotional tone of what they're viewing, and surfaces trends, insights, and wellness suggestions through a 5-screen dashboard.

**Analysis pipeline:** Screenshot → on-device ML Kit OCR → keyword-based emotion classifier → local Room DB

All analysis is 100% on-device. No screen content is sent to any server.

---

## Architecture

### Core Files

| File | Role |
|------|------|
| `MainActivity.kt` | 5-screen bottom-nav UI (Home, Trends, History, Insights, Settings) |
| `ScreenCaptureService.kt` | Foreground service — MediaProjection screen capture loop |
| `OnDeviceAnalyzer.kt` | ML Kit OCR + keyword emotion/content classifier |
| `CaptureViewModel.kt` | MVVM state management, StateFlow wrappers |
| `CaptureRepository.kt` | Room DB access layer |
| `AppDatabase.kt` | Room database definition |
| `CaptureResultEntity.kt` | DB entity: id, timestamp, emotion, emotionScore, sentiment, contentType, contentConfidence, textSnippet |
| `CaptureDao.kt` | Room DAO — queries, inserts, retention cleanup |
| `ChartComponents.kt` | Custom Compose canvas charts |
| `SuggestionEngine.kt` | Rule-based wellness suggestion logic |
| `NotificationHelper.kt` | Foreground and wellness notification builder |
| `ServiceStatusStore.kt` | SharedFlow for service running state |
| `PdfExporter.kt` | Generates PDF summary report |
| `ImageUtils.kt` | Bitmap scaling utilities |
| `TimeRangeOption.kt` | Enum for time-range filter options |

### Data Flow
```
Screen Buffer (RGBA_8888)
  → ImageUtils.scaleToMaxDimension() → Bitmap
  → OnDeviceAnalyzer.analyze()
      ├── ML Kit TextRecognizer → extracted text
      ├── keyword emotion classifier → { emotion, confidence }
      └── keyword content classifier → { contentType, confidence }
  → CaptureRepository.addResult() → Room SQLite
```

---

## Five Screens

### 1. Home
- Live capture status with pulsing indicator
- Animated mood ring glow showing latest emotion
- Mini sparkline (last 20 captures)
- Start/Stop capture buttons

### 2. Trends
- Sentiment line chart with area fill
- Granularity: All / 15m / 30m / 1h / 3h / 6h
- Time-range filter: 1h / 6h / 24h / 7d / 30d
- Period comparison with delta indicators
- Emotion and content distribution bar charts + donut charts

### 3. History
- Chronological capture list, date-grouped
- Emotion/content filter chips
- Expandable cards with full metrics
- Empty states

### 4. Insights
- Wellness score arc
- Screen time stats
- 24-hour usage heatmap
- Trend comparisons vs previous period
- AI-powered suggestions from SuggestionEngine

### 5. Settings
- Capture interval (1–60 seconds)
- JPEG quality (10–100%)
- Max dimension (240–2160px)
- Pause when screen off toggle
- Data retention (1–90 days)
- Clear data
- Backend URL + Test Connection (to be removed after on-device ML)

---

## Tech Stack

| Layer | Library |
|-------|---------|
| UI | Jetpack Compose + Material 3 |
| State | ViewModel + StateFlow + Coroutines |
| DB | Room (SQLite) |
| Networking | OkHttp 4.12 (to be removed for core capture) |
| Charts | Custom Compose Canvas |
| PDF | Android PdfDocument API |
| Crash reporting | Firebase Crashlytics |
| Analytics | Firebase Analytics |
| ML (planned) | ML Kit Text Recognition + TFLite MobileBERT |

---

## Permissions

| Permission | Purpose | Status |
|-----------|---------|--------|
| `INTERNET` | Firebase SDKs | Keep |
| `ACCESS_NETWORK_STATE` | Network availability check before upload | Keep |
| `FOREGROUND_SERVICE` | Screen capture service | Keep |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | MediaProjection API | Keep |
| `POST_NOTIFICATIONS` | Wellness + capture notifications (API 33+) | Keep |

---

## Build & Signing

```bash
# Debug
./gradlew installDebug

# Release AAB (Play Store)
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab

# Release APK (sideload)
./gradlew assembleRelease
```

**Signing:** via `keystore.properties` (gitignored) or env vars:
- `SCREENTRACK_KEYSTORE_PATH`
- `SCREENTRACK_KEYSTORE_PASSWORD`
- `SCREENTRACK_KEY_ALIAS`
- `SCREENTRACK_KEY_PASSWORD`

---

## Play Store Status

**Current state:** Rejected — "Violation of Play Console Requirements" — fix implemented, pending local test + resubmission

**Original Reason:** App was sending raw screen content (JPEG) to a remote server. Now fully on-device.

**Requirements before resubmission:**
- [x] On-device ML migration complete (no backend upload)
- [x] First-launch consent dialog added
- [x] targetSdk bumped to 35, versionCode 2
- [ ] Install debug build and test locally on device
- [ ] Update privacy policy to reflect on-device processing
- [ ] Fill out Foreground Service permission declaration + demo video in Play Console
- [ ] Change app category to Health & Fitness or Digital Wellbeing
- [ ] Upload new AAB

---

## Code Cleanup Done (2026-05-04)

- Removed `SuggestionEngine.formatSummary()` — dead code
- Removed `emotionColorForChart()` wrapper in ChartComponents.kt — duplicate of `emotionColor()`
- Removed unused `label` parameter from `PdfExporter.drawPage2()`
- Removed unused `capColor`, `sentColor` variables in MainActivity.kt
- Suppressed deprecated `getParcelableExtra` in ScreenCaptureService.kt
- Deleted `firebase-debug.log`, `memory.md`, `PUBLISH.md`, `feature-graphic.svg.png`
- Bumped `compileSdk`/`targetSdk` to 35, `versionCode` to 2

---

## Development Setup

**Requirements:** Android Studio Flamingo+, JDK 11+, Android SDK API 26+

```bash
# Clone and open ScreenTrack-Android/ in Android Studio
# Gradle syncs automatically
# local.properties is auto-created by Android Studio
```
