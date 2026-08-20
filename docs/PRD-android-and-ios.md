# ScreenTrack — Product Requirements Document (Android + iOS)

**Date:** 2026-07-08
**Author:** Chirag
**Study:** "Social Media Usage and Mental Health" (IU IRB Protocol 28055, PI: Krista Li)
**Purpose:** Document the complete working of the current Android app, honestly assess what is and isn't portable to iOS, and specify the realistic iOS version so we can build and test it.

---

# PART A — Current Android App (what we've built)

## A1. Goal
Passively understand what kind of content a participant consumes and its emotional tone, **entirely on-device**, to support research on screen use and mood — while guaranteeing no screen data leaves the phone.

## A2. Platform & stack
- **Android only**, min SDK 26 (Android 8.0), target/compile SDK 35.
- Kotlin, Jetpack Compose + Material 3, Room (local DB), ML Kit (on-device OCR + face detection).
- Version 1.1.0 (versionCode 3). Package `com.chiragdodia.screentrack`.

## A3. End-to-end working (capture → analysis → storage → display)

1. **Consent & permission.** Participant grants Android's system screen-capture consent (MediaProjection). The app also asks for notification + phone-state permissions.
2. **Capture loop.** A foreground service (`ScreenCaptureService`) mirrors the screen via `MediaProjection` + `VirtualDisplay` + `ImageReader`, grabbing **one still frame every ~5 seconds** (configurable 1–60s). It is *not* video; it's periodic snapshots.
3. **On-device analysis** (`OnDeviceAnalyzer`), all in memory:
   - **ML Kit OCR** reads on-screen text.
   - **ML Kit face detection** reads a smiling probability if a face is visible.
   - Pure classifiers derive: **emotion** label, **sentiment** (−1…+1), **content category**, with confidences.
   - If neither text nor face gives a signal → **`"undetermined"`** (honest, not a fake "neutral").
4. **Discard image.** The frame is recycled from memory immediately — never written to disk, never transmitted.
5. **Store derived result only.** A row is saved to a local Room database: timestamp, emotion, emotionScore, sentiment, contentType, contentConfidence. **No screenshots, no raw text.** 30-day rolling auto-delete.
6. **Display.** Compose UI shows latest result, trends/charts, and wellness suggestions. PDF export available locally.

## A4. Pause & privacy controls
- **Auto-pause** when the screen is off, and **during phone and VoIP/video calls** (`CallMonitor`).
- **Manual Pause/Resume** button.
- Multi-source pause: capture resumes only when *all* pause reasons clear.

## A5. Privacy posture (verified)
- **No network capability.** `INTERNET`/`ACCESS_NETWORK_STATE` permissions removed (including ML-Kit-merged) — confirmed absent in the built APK.
- **No analytics, no crash reporting, no server.** All Firebase Analytics/Crashlytics removed.
- Nothing derived from the screen leaves the device. Data is local-only, sandboxed, 30-day retention.
- Faces are analyzed on-device (smiling probability only) and never stored; call auto-pause excludes private video calls.

## A6. Architecture (testable core)
- **Pure, unit-tested logic** (JVM, 19 tests): `TextSentimentClassifier`, `ContentClassifier`, `FaceSignals`, `AnalysisCombiner`, `PauseState`.
- **Thin impure I/O**: `OnDeviceAnalyzer` (ML Kit), `ScreenCaptureService` (MediaProjection), `CallMonitor` (telephony), Room DB.

## A7. Distribution & status
- Distributed **privately via Firebase App Distribution** to a `study-participants` tester group — **not** on the Play Store.
- Current build v1.1.0 distributed for testing. Old Hugging Face (server-upload) version retired/archived.
- IRB coverage under Protocol 28055 pending confirmation; survey feature deferred.

---

# PART B — iOS Feasibility (what is and isn't portable)

## B1. The core is NOT portable
The Android capture engine **cannot be replicated on iOS**, for OS and policy reasons — not effort:

- **No passive background screen capture.** iOS's only screen API (**ReplayKit**) is *user-initiated*, shows a **persistent red recording indicator**, and cannot run silently in the background sampling a frame every few seconds. There is no MediaProjection/foreground-service equivalent.
- **Background suspension.** iOS aggressively suspends apps; a continuous capture-and-analyze loop cannot stay alive.
- **App Store policy.** An app that reads the user's screen across all other apps for wellness monitoring is an automatic rejection.

This is the same wall as the web version — different mechanism, same outcome.

## B2. What iOS offers instead — the Screen Time API
Apple's sanctioned tool is **Screen Time** (frameworks: **FamilyControls**, **DeviceActivity**, **ManagedSettings**):
- ✅ App/category **usage duration**, pickups, notification counts.
- ❌ **No screen content**, no OCR, no on-screen emotion/sentiment.
- ⚠️ **Data-export is restricted by design.** Usage is rendered inside a sandboxed `DeviceActivityReport` SwiftUI extension that has **no network access**, specifically to stop apps from harvesting Screen Time data. Getting precise raw usage numbers *off the device* to researchers is deliberately blocked. You can react to **threshold events** (via a `DeviceActivityMonitor` extension) and store flags, but not export a clean minute-by-minute log.

## B3. Portability map

| Android feature | iOS equivalent | Portable? |
|-----------------|----------------|-----------|
| Continuous screen capture (MediaProjection) | ReplayKit (user-initiated, indicator, no background) | ❌ No |
| On-screen OCR + content category | — | ❌ No |
| Facial-expression sentiment | — | ❌ No |
| Emotion/sentiment from screen content | — | ❌ No |
| App/social-media **usage time** | Screen Time API (DeviceActivity) | ⚠️ Display yes; **export restricted** |
| Daily mental-health survey | Standard app + notifications | ✅ Yes (and exportable) |
| On-device, private processing | Screen Time is on-device by design | ✅ Yes |
| Private beta distribution (Firebase) | **TestFlight** | ✅ Yes |

**Conclusion:** The iOS app is a **different product with the same research goal** — it measures *usage + self-reported mood*, not *screen-content emotion*.

---

# PART C — iOS Version PRD (the buildable app)

## C1. Product summary
An iOS study app that measures **social-media usage** (via Apple Screen Time API) and **mental health** (via daily in-app surveys / EMA), for the same "Social Media Usage and Mental Health" study. It does **not** capture or analyze screen content.

## C2. Platform & stack
- iOS 16+ (Screen Time API maturity), Swift + SwiftUI.
- Frameworks: **FamilyControls** (authorization), **DeviceActivity** (usage monitoring + report), **ManagedSettings** (optional), **UserNotifications** (daily survey prompts).
- Distribution: **TestFlight** (up to 10,000 external testers, lightweight Beta App Review, no full App Store listing).

## C3. Prerequisites (gating items — start early)
1. **Apple Developer Program** membership ($99/yr).
2. A **Mac + Xcode** for development and builds.
3. **Family Controls distribution entitlement** — a special entitlement Apple must approve by request for non-parental-control use. **This can be slow and is the critical-path dependency.**
4. IRB coverage confirmation for the iOS data collection.

## C4. Features (MVP)
1. **Onboarding & consent** — study info, consent, and Screen Time authorization (`AuthorizationCenter.requestAuthorization(for: .individual)`).
2. **Usage monitoring** — `DeviceActivitySchedule` + `DeviceActivityMonitor` extension tracking social-media category usage; threshold events recorded as flags on-device.
3. **Usage display** — a `DeviceActivityReport` view showing the participant their own social-media time (Apple renders this; app cannot read the raw numbers directly).
4. **Daily mental-health survey (EMA)** — a short validated scale + mood rating, prompted by a **daily local notification**. Fully in-app.
5. **Data delivery** — survey responses upload to a researcher-controlled store (see C6). Usage data delivery is constrained (see C6).
6. **Participant controls** — pause/withdraw, view what's collected, delete data.

## C5. Data model
- **Survey response**: participantId (pseudonymous), timestamp, scale answers, mood rating. → exportable.
- **Usage signals**: threshold-event flags (e.g., "social use exceeded 2h today") captured by the monitor extension. → limited, on-device.
- No screen content of any kind.

## C6. ⚠️ The data-delivery problem (must resolve with Krista)
- **Survey data:** easy — a normal iOS app has network; responses can go to Firestore or a Google Form/Sheet (same options as the Android survey discussion).
- **Usage data:** Apple **blocks** clean export of Screen Time numbers. Options, all imperfect:
  - (a) Use **threshold events** to record coarse flags (e.g., crossed X minutes) — low fidelity.
  - (b) Ask participants to **manually submit** their iOS Screen Time weekly summary (screenshot/report) — common in research, but manual.
  - (c) Accept that iOS gives **usage shown to the participant** but not researcher-grade raw export.
- **This is a research-design decision for the PI**, and it means iOS usage data will be lower-fidelity than Android.

## C7. Distribution & testing
- Build in Xcode → upload to **App Store Connect** → **TestFlight**.
- Internal testers: instant. External testers (study participants): one-time lightweight Beta App Review, then invite by email — analogous to Firebase App Distribution on Android.

## C8. What the iOS app explicitly will NOT do
- ❌ No screen capture, screenshots, or screen recording.
- ❌ No OCR or reading of on-screen text.
- ❌ No emotion/sentiment from screen content.
- ❌ No background per-app content analysis.

## C9. Rough phasing
1. **Phase 0 (unblock):** Apple Developer account + request Family Controls entitlement (long lead time) + IRB check for iOS.
2. **Phase 1:** SwiftUI app shell, onboarding/consent, Screen Time authorization.
3. **Phase 2:** Daily survey + notifications + survey-data delivery (mirror Android survey decision).
4. **Phase 3:** DeviceActivity usage monitoring + report view + threshold events.
5. **Phase 4:** TestFlight distribution + pilot.

---

## Open questions for the research team (Krista)
1. Given iOS **cannot** measure screen-content emotion, is **usage + surveys** enough scientific value to justify an iOS build, or should the study stay **Android-only** for the content-analysis arm and use iOS only for surveys?
2. How should **survey data** be delivered (Firestore vs Google Form/Sheet)? (Same decision as Android.)
3. Is **lower-fidelity iOS usage data** (threshold flags / manual Screen Time submission) acceptable, or is precise usage required?
4. Does IRB Protocol 28055 cover an iOS app with different data collection?
