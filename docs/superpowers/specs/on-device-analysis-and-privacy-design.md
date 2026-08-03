# On-Device Analysis Accuracy + Privacy Hardening — Design Spec

**Date:** 2026-06-30
**Status:** Approved design (pending spec review)
**Scope owner:** Chirag

## 1. Goal

Make the app's emotional read of on-screen content **accurate and honest**, and
harden privacy so the study can truthfully claim **nothing from the participant's
screen leaves the device**. All analysis stays on-device.

The driving research need (from Krista): reliably tell whether the content a
participant consumes is **positive / negative / neutral**, to infer mental state —
plus a **content category** for context.

## 2. Why (problems being fixed)

- Current sentiment/emotion is naive keyword counting; content with no matching
  "feeling words" returns a meaningless "neutral" (e.g., a gameplay/reaction video).
- Current content category is weak keyword matching → returns "unknown"/"general"
  for common content.
- Privacy leaks remain that contradict the IRB data-handling doc: a stored text
  snippet, Firebase Analytics, Crashlytics, and an unused INTERNET capability.
- Krista's requested changes (remove snippet, remove analytics, add a pause button).

## 3. Decisions (settled)

1. **Database on update:** wipe & rebuild (destructive migration). No real
   participant data exists yet — only test installs.
2. **Bundle Krista's privacy changes** into this work (same files, one DB change).
3. **Sentiment:** text **+** faces.
4. **Category:** improved **content-based** classification (read on-screen text).
   **No app detection** — app identity ≠ content meaning (YouTube could be
   learning, news, or emotional content). No Usage Access permission.
5. **Old Hugging Face version:** archived to a folder, not deleted.

## 4. Scope

### In scope
- Rewrite `OnDeviceAnalyzer`: text sentiment + facial-expression signal + improved
  content category; output an honest "undetermined" when no signal exists.
- New call-state monitor (phone + VoIP) that pauses/resumes capture.
- Multi-source pause state (screen-off · call · manual) in `ScreenCaptureService`.
- Manual Pause/Resume (in-app button + notification action + service command).
- Remove `textSnippet`, Firebase Analytics, Crashlytics, `okhttp`, `INTERNET`
  permission, and the dead "JPEG quality" setting.
- Destructive DB migration (bump version, wipe on upgrade).
- Unit tests for the analyzer.
- Archive old Hugging Face version files into `archive/huggingface-version/`.

### Out of scope (later)
- Daily mental-health survey + reminder (separate spec, pending Krista's answer on
  how survey responses are delivered).
- TFLite/MobileBERT model upgrade (future accuracy improvement).

## 5. Architecture / components

### 5.1 `OnDeviceAnalyzer` (rewrite)
Input: a `Bitmap` (held in memory only; recycled by caller, never stored).
Output (`Result`): `emotion` (label), `emotionScore`, `sentiment` (−1..+1),
`contentType`, `contentConfidence`. **`textSnippet` removed.**

Pipeline per frame:
1. **Text signal** — ML Kit OCR → improved sentiment scoring (a valence lexicon
   with negation handling, e.g. "not good" is negative — not bare `contains()`
   counting) → a sentiment score + dominant emotion label.
2. **Face signal** — ML Kit Face Detection. If a face is present, use its
   smiling/expression probability as a positive/negative contribution.
3. **Combine** — if both signals present, blend (text-weighted); if one, use it;
   if **neither**, return `emotion = "undetermined"` with low confidence (do NOT
   fabricate "neutral").
4. **Content category** — improved content-based keyword/phrase classification over
   the OCR text (expanded, weighted lists; consider adding categories such as
   "education"). Falls back to "general" only when genuinely unclassifiable.

Lifecycle: analyzer holds the OCR client **and** the face-detection client; both
are released in `close()`.

### 5.2 Call-state monitor (new)
- Phone calls: `TelephonyManager` / call-state callback (needs `READ_PHONE_STATE`).
- VoIP/video calls: `AudioManager` mode `MODE_IN_COMMUNICATION`.
- Emits "call active / inactive" to the service.
- Known limitation: ~1–2s detection lag at call start; obscure VoIP apps may not be
  detected. Regular calls are reliable. Manual pause covers the gap.

### 5.3 Pause system (`ScreenCaptureService`)
Replace the single `isPaused` boolean with three independent reasons:
`pausedByScreenOff`, `pausedByCall`, `pausedManual`. Capture is gated when **any**
is true; resumes only when **all** are false. The image listener checks the
combined state.

### 5.4 Manual pause plumbing
- New service actions `ACTION_PAUSE` / `ACTION_RESUME` (today only `ACTION_STOP`).
- Notification gains a Pause/Resume action.
- In-app Pause/Resume button wired through the existing
  `ScreenCaptureService` control surface; UI reflects paused state.

### 5.5 Data model + migration
- `CaptureResultEntity`: remove `textSnippet`.
- `AppDatabase`: bump `version` to 2, add `fallbackToDestructiveMigration()` so the
  old table is dropped & rebuilt on upgrade (no crash, old test data cleared).

### 5.6 Removals / cleanup
- Delete the 3 `FirebaseAnalytics` calls + import; remove `firebase-analytics-ktx`.
- Remove Crashlytics plugin + `firebase-crashlytics-ktx` (sends data off-device).
- Remove `okhttp` dependency.
- Remove `INTERNET` (and `ACCESS_NETWORK_STATE`) permission — strengthens the
  "no network / nothing leaves device" claim.
- Remove the "JPEG quality" setting (UI field, pref, service extra) — dead in the
  on-device path.
- Update "Last upload" wording (nothing is uploaded).
- Remove the stored text-snippet display in `MainActivity`.

### 5.7 Tests
Unit tests for `OnDeviceAnalyzer`'s pure logic (sentiment + category) with sample
inputs: negative text → negative; positive text → positive; no text/face →
undetermined; representative category samples. (The keyword/lexicon and combine
logic are pure and testable without a device.)

## 6. Data flow (per capture, unchanged shape)
frame → bitmap (memory) → analyzer (OCR + face, in memory) → `Result` →
`CaptureResultEntity` saved to local Room DB → charts / suggestions read it.
Bitmap recycled immediately. Pause sources gate whether a frame is processed.

## 7. Permissions impact
- **Add:** `READ_PHONE_STATE` (call detection) + a runtime request flow.
- **Remove:** `INTERNET`, `ACCESS_NETWORK_STATE`.
- No Usage Access (app detection rejected).
- Face detection needs no permission.

## 8. IRB / privacy implications (to tell Krista)
- Stronger claim now possible: no raw text stored, no analytics/crash data sent,
  app cannot reach the network.
- **New disclosure:** the app analyzes **faces visible on screen** on-device
  (never stored). Mitigated by call auto-pause (private video calls excluded) +
  manual pause. Update `docs/IRB-data-handling.md` accordingly.

## 9. Risks
- **Battery/CPU:** OCR + face detection every frame (~5s). Measure; consider
  raising the interval or skipping face detection when no face was recently seen.
- **VoIP detection gaps:** documented; manual pause is the backstop.
- **APK size:** face-detection model adds size (already ~40 MB). Acceptable.
- **No prior tests:** mitigated by adding analyzer unit tests this round.

## 10. Out-of-scope follow-ups
- Survey feature (own spec).
- Update `docs/IRB-data-handling.md` to reflect face analysis + removals.
- Rebuild + redistribute the new build via Firebase to testers.
