# ScreenTrack — Data Collection, Storage & Privacy (IRB Supplement)

**App:** ScreenTrack (Android, package `com.chiragdodia.screentrack`)
**Prepared for:** IRB / ethics review
**Date:** 2026-06-29
**Platform:** Android only (minimum Android 8.0 / API 26)

> **Important scope note:** This document describes the **on-device** version of
> the app, in which all analysis happens locally on the participant's phone and
> no screen images leave the device. The study **must be conducted with this
> on-device build.** An earlier version that transmitted screenshots to a remote
> server must not be used for the study.

---

## 1. Purpose

ScreenTrack passively observes what is on the participant's phone screen and
derives high-level indicators of (a) the emotional tone of on-screen content and
(b) the category of activity (e.g., social media, messaging, work). These derived
indicators are used to provide the participant with wellness insights and to
support the research study on screen use and mood.

## 2. What data is collected

The app **does not retain screen images.** For each sampled moment it stores only
the following derived fields in a local database:

| Field | Description | Example |
|-------|-------------|---------|
| `timestamp` | When the sample was taken | 2026-06-29 14:32 |
| `emotion` | Dominant emotion label inferred from on-screen text and/or a visible face | "joy", "sadness", "undetermined" |
| `emotionScore` | Confidence of the emotion label (0–1) | 0.74 |
| `sentiment` | Sentiment value, positive↔negative (−1 to +1) | −0.5 |
| `contentType` | Activity category inferred from on-screen text | "social_media", "education" |
| `contentConfidence` | Confidence of the category (0–1) | 0.6 |

**No recognized text is stored.** When neither readable text nor a visible face
yields a signal, `emotion` is recorded as `"undetermined"` rather than a fabricated
value.

## 3. How data is collected (processing pipeline)

1. With the participant's explicit permission (Android's standard screen-capture
   consent dialog), the app captures the screen at a fixed interval
   (default: one frame every 5 seconds; paused when the screen is off).
2. Each captured frame is held **only in memory**, downscaled, and passed to an
   **on-device** analyzer.
3. The analyzer uses **Google ML Kit on-device text recognition (OCR)** to read
   visible text, and **on-device face detection** to read a facial expression
   (smiling probability) when a face is visible. Local rules classify emotion,
   sentiment, and content category. **No cloud service is contacted.**
4. The image is **immediately discarded from memory** (`recycle()`) after
   analysis. It is never written to storage and never transmitted.
5. Only the derived fields in §2 are written to the local database.
6. Capture **auto-pauses during phone and VoIP/video calls** and whenever the
   participant taps **Pause**, so private conversations are not analyzed.

## 4. What is explicitly NOT collected or stored

- ❌ **No screenshots or images** are ever saved to disk or sent anywhere.
- ❌ **No recognized text is stored** — text is read in memory only and discarded.
- ❌ **No face images** are stored — only a transient smiling probability is used.
- ❌ **No keystrokes, passwords, contacts, location, or files** are accessed.
- ❌ **No audio or camera** access.
- ❌ **No data is transmitted anywhere.** The app has no `INTERNET` permission.

## 5. Where data is stored

- All derived records are stored in a **local database on the participant's own
  device**, inside the app's private storage area. This area is sandboxed by
  Android and is not accessible to other apps, and is protected by the device's
  built-in storage encryption.
- Data is **automatically deleted after 30 days** (rolling retention).
- The participant can stop collection at any time and uninstalling the app
  removes all stored data.

## 6. Facial-expression analysis (on-device)

When a face is visible on screen (e.g., a person in a video), the app reads its
**smiling probability** on-device to inform the positive/negative reading. This
is computed in memory and **never stored or transmitted** — no face image or
identity is retained. Because a face may belong to someone other than the
participant (e.g., in a video), capture **auto-pauses during phone and video/VoIP
calls** so private conversations are excluded, and the participant can pause
manually at any time.

## 7. Data that leaves the device

**Nothing.** All analysis is on-device, and the app has **no `INTERNET`
permission** — it is technically incapable of transmitting data. There is no
analytics, no crash reporting, and no server of any kind. (The earlier
Firebase Analytics has been removed from the study build.)

- **Recommended for the study:** consider **disabling analytics entirely** for
  study builds so that *no* participant-derived data leaves the device. This
  makes the privacy guarantee absolute and simplifies IRB review.

## 8. Distribution & consent

- The app is **not published to any public app store.** It is distributed
  privately via **Firebase App Distribution** to a named list of enrolled
  participants only.
- Participants are **invited individually by email** and can install the app
  only after enrolling and accepting their invite.
- On first capture, Android shows its own system consent dialog; capture cannot
  begin without the participant explicitly granting it.
- This is in addition to the study's own informed-consent process.

## 9. Participant controls

- Start/stop capture at any time from within the app.
- **Manual Pause/Resume** button to stop collection at any moment.
- Capture automatically pauses when the screen is off and **during phone/video calls**.
- Configurable retention period.
- Uninstalling removes all locally stored data.

## 10. Summary of privacy posture

| Question | Answer |
|----------|--------|
| Are screenshots stored? | No — discarded immediately after on-device analysis |
| Is recognized text stored? | No — read in memory only, never saved |
| Are face images stored? | No — only a transient smiling probability is used |
| Is screen content sent to a server? | No |
| Does analysis use the cloud? | No — fully on-device (ML Kit OCR + face + local rules) |
| **What leaves the device?** | **Nothing** — the app has no INTERNET permission |
| Where is data kept? | Locally on the participant's device only |
| How long? | 30 days, then auto-deleted |
| Platform | Android only |

---

### Notes for the research team

1. The study build is the **on-device version** (the earlier server-upload version
   is retired).
2. The `textSnippet` field has been **removed** — no recognized text is stored.
3. Firebase Analytics and crash reporting have been **removed** — nothing leaves
   the device.
4. New: the app performs **on-device facial-expression analysis** (§6), mitigated
   by auto-pause during calls and a manual pause.
